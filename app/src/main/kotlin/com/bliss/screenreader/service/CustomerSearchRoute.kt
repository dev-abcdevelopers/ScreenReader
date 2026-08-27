@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.bliss.screenreader.data.parser.PolicySearchParser
import java.util.Locale
import kotlin.math.abs

interface CustomerSearchHost {
    fun CustomerSearchRootNode(): AccessibilityNodeInfo?
    fun CustomerSearchRecycleNode(NodeRef: AccessibilityNodeInfo?)
    fun CustomerSearchTap(XPos: Float, YPos: Float): Boolean
    fun CustomerSearchSetText(NodeRef: AccessibilityNodeInfo, TextValue: String): Boolean
    fun CustomerSearchSchedule(DelayMs: Long, ActionRef: () -> Unit)
    fun CustomerSearchRefreshScreen()
    fun CustomerSearchInfo(EventName: String, MessageText: String)
    fun CustomerSearchWarn(EventName: String, MessageText: String)
    fun CustomerSearchScreenWidth(): Int
    fun CustomerSearchScreenHeight(): Int
    fun CustomerSearchBeginRun()
    fun CustomerSearchOpenDetail(NameTextVal: String)
    fun CustomerSearchPressBack(): Boolean
    fun CustomerSearchHandOffToDashboard()
    fun CustomerSearchFinishRun()
}

class CustomerSearchRoute(private val HostRef: CustomerSearchHost) {

    private enum class Stage {
        IDLE, OPEN_SEARCH, WAIT_SEARCH, FILTER_CHIP, TYPE_QUERY, WAIT_RESULTS, OPEN_ROW, DETAIL,
        RETURN
    }

    private data class NodeSnapshot(
        val TextVal: String,
        val Bounds: Rect,
        val IsClickable: Boolean,
        val IsEditable: Boolean
    )

    companion object {
        private const val MAX_TARGETS = 5
        private const val STEP_DELAY_MS = 450L
        private const val SCREEN_WAIT_MS = 1300L
        private const val RESULT_WAIT_MS = 900L
        private const val ICON_ATTEMPT_LIMIT = 3
        private const val SEARCH_WAIT_LIMIT = 5
        private const val QUERY_ATTEMPT_LIMIT = 3
        private const val RESULT_WAIT_LIMIT = 6
        private const val ROW_ATTEMPT_LIMIT = 3
        private const val ROW_WAIT_LIMIT = 4
        private const val RETURN_WAIT_LIMIT = 5
        private const val BACKOUT_LIMIT = 3
        private const val STALL_LIMIT_MS = 25_000L
        private const val DETAIL_STALL_LIMIT_MS = 240_000L
        private const val HEADER_BAND_RATIO = 0.14f
        private const val HEADER_RIGHT_RATIO = 0.62f
        private const val CHIP_BAND_TOP_RATIO = 0.10f
        private const val CHIP_BAND_BOTTOM_RATIO = 0.34f
        private const val ICON_FALLBACK_X_RATIO = 0.92f
        private const val ROW_ARROW_X_RATIO = 0.94f
        private const val SHEET_FIELD_BAND_RATIO = 0.5f
        private const val ROW_HEIGHT_RATIO = 0.12f
        private const val ROW_PAIR_RATIO = 0.06f
        private const val DANGER_CLEARANCE_RATIO = 0.035f

        private val HEADER_EXCLUDED_LABELS = listOf(
            "back", "close", "customer portfolio", "customer dashboard", "filter", "sort",
            "calendar", "arrow-left"
        )

        private val ROW_ARROW_LABELS = listOf("righticon", "rightarrowicon", "arrowright")
        private const val DASHBOARD_ARROW_LABEL = "cardrightarrow"

        private val ROLE_MARKER_REGEX = Regex("\\(\\s*(a|p|la)\\s*\\)", RegexOption.IGNORE_CASE)

        private val DANGER_LABELS = listOf(
            "call customer", "call", "phone", "dial", "mail", "email", "message", "whatsapp",
            "send reminder"
        )
    }

    var IsArmed = false
        private set

    var IsDriving = false
        private set

    var IsBackingOut = false
        private set

    private var StageVal = Stage.IDLE
    private var Targets: List<String> = emptyList()
    private var TargetIndex = 0
    private var IconAttempts = 0
    private var SearchWaits = 0
    private var QueryAttempts = 0
    private var ResultWaits = 0
    private var RowAttempts = 0
    private var RowWaits = 0
    private var ReturnWaits = 0
    private var LastStepAt = 0L
    private var IsSearchScreenVisible = false
    private var IsEntryVisibleVal = false
    private var LatestNodes: List<String> = emptyList()

    fun Reset() {
        IsArmed = false
        IsDriving = false
        StageVal = Stage.IDLE
        Targets = emptyList()
        TargetIndex = 0
        IconAttempts = 0
        SearchWaits = 0
        QueryAttempts = 0
        ResultWaits = 0
        RowAttempts = 0
        RowWaits = 0
        ReturnWaits = 0
        IsBackingOut = false
        LastStepAt = 0L
        IsSearchScreenVisible = false
        IsEntryVisibleVal = false
        LatestNodes = emptyList()
    }

    fun Arm(TargetsVal: List<String>) {
        Reset()
        val CleanTargets = TargetsVal
            .map { NameText -> NameText.trim() }
            .filter { NameText -> NameText.isNotEmpty() }
            .distinct()
        if (CleanTargets.isEmpty()) return
        if (CleanTargets.size > MAX_TARGETS) {
            HostRef.CustomerSearchInfo(
                EventName = "CUSTOMER_TARGET_START",
                MessageText = "targets=${CleanTargets.size} route=dashboard-walk " +
                        "reason=[more than $MAX_TARGETS targets]"
            )
            return
        }
        Targets = CleanTargets
        IsArmed = true
        HostRef.CustomerSearchInfo(
            EventName = "CUSTOMER_TARGET_START",
            MessageText = "targets=${Targets.joinToString(separator = "|")} route=search"
        )
    }

    fun HandleScreen(
        VisibleNodes: List<String>,
        IsEntryVisible: Boolean,
        IsBusyScreen: Boolean
    ): Boolean {
        if (!IsArmed && !IsBackingOut) return false
        LatestNodes = VisibleNodes
        IsSearchScreenVisible = PolicySearchParser.IsSearchScreen(Nodes = VisibleNodes)
        IsEntryVisibleVal = IsEntryVisible && !IsSearchScreenVisible
        if (!IsArmed) return false

        if (IsDriving && HasStalled()) {
            Fallback(ReasonText = "route stalled at ${StageVal.name.lowercase(Locale.US)}")
            return false
        }

        if (IsBusyScreen && !IsSearchScreenVisible) return false

        if (!IsDriving) {
            if (!IsEntryVisibleVal && !IsSearchScreenVisible) return false
            BeginRoute()
            return true
        }
        return true
    }

    fun OnDetailOpenFailed() {
        if (!IsDriving) return
        StageVal = Stage.OPEN_ROW
        HostRef.CustomerSearchWarn(
            EventName = "CUSTOMER_SEARCH_ROW_RETRY",
            MessageText = "customer=${CurrentTarget()} attempts=$RowAttempts"
        )
        Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { OpenRow() })
    }

    fun OnCustomerFinished() {
        if (!IsDriving) return
        TargetIndex++
        RowAttempts = 0
        RowWaits = 0
        ResultWaits = 0
        QueryAttempts = 0
        ReturnWaits = 0

        if (TargetIndex >= Targets.size) {
            HostRef.CustomerSearchInfo(
                EventName = "CUSTOMER_TARGET_COMPLETE",
                MessageText = "customers=${Targets.size} route=search"
            )
            IsArmed = false
            IsDriving = false
            StageVal = Stage.IDLE
            HostRef.CustomerSearchFinishRun()
            return
        }

        StageVal = Stage.RETURN
        IsSearchScreenVisible = false
        Step(DelayMs = STEP_DELAY_MS, ActionRef = { ReturnToSearch() })
    }

    private fun ReturnToSearch() {
        if (IsSearchScreenVisible) {
            StageVal = Stage.TYPE_QUERY
            QueryAttempts = 0
            Step(DelayMs = STEP_DELAY_MS, ActionRef = { TypeQuery() })
            return
        }
        if (IsEntryVisibleVal) {
            StageVal = Stage.OPEN_SEARCH
            IconAttempts = 0
            Step(DelayMs = STEP_DELAY_MS, ActionRef = { TapSearchIcon() })
            return
        }
        ReturnWaits++
        if (ReturnWaits > RETURN_WAIT_LIMIT) {
            Fallback(ReasonText = "could not get back to search after the previous customer")
            return
        }
        HostRef.CustomerSearchPressBack()
        Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { ReturnToSearch() })
    }

    private fun BeginRoute() {
        IsDriving = true
        HostRef.CustomerSearchBeginRun()
        if (IsSheetReady()) {
            StageVal = Stage.FILTER_CHIP
            QueryAttempts = 0
            HostRef.CustomerSearchInfo(
                EventName = "CUSTOMER_TARGET_ROUTE",
                MessageText = "stage=filter-chip customer=${CurrentTarget()} " +
                        "index=${TargetIndex + 1}/${Targets.size} entry=open-search-sheet"
            )
            Step(DelayMs = STEP_DELAY_MS, ActionRef = { TapCustomersChip() })
            return
        }
        StageVal = Stage.OPEN_SEARCH
        IconAttempts = 0
        HostRef.CustomerSearchInfo(
            EventName = "CUSTOMER_TARGET_ROUTE",
            MessageText = "stage=open-search customer=${CurrentTarget()} " +
                    "index=${TargetIndex + 1}/${Targets.size}"
        )
        Step(DelayMs = STEP_DELAY_MS, ActionRef = { TapSearchIcon() })
    }

    private fun TapSearchIcon() {
        if (IsSheetReady()) {
            OnSearchScreenReady()
            return
        }
        IconAttempts++
        if (IconAttempts > ICON_ATTEMPT_LIMIT) {
            Fallback(ReasonText = "search control was never found on the customer screens")
            return
        }

        val Snapshots = ReadSnapshots() ?: run {
            Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { TapSearchIcon() })
            return
        }
        val ScreenWidth = HostRef.CustomerSearchScreenWidth()
        val ScreenHeight = HostRef.CustomerSearchScreenHeight()
        val HeaderNodes = Snapshots.filter { SnapItem ->
            SnapItem.Bounds.centerY() in 1..(ScreenHeight * HEADER_BAND_RATIO).toInt()
        }
        val CandidateText = HeaderNodes.joinToString(separator = " ") { SnapItem ->
            val LabelText = if (SnapItem.TextVal.isEmpty()) "-" else SnapItem.TextVal
            "[" + LabelText + "]" + SnapItem.Bounds + "clickable=" + SnapItem.IsClickable
        }
        HostRef.CustomerSearchInfo(
            EventName = "CUSTOMER_SEARCH_ICON_CANDIDATES",
            MessageText = "attempt=$IconAttempts headerNodes=${HeaderNodes.size} " +
                    "candidates=$CandidateText"
        )

        val IconBounds = ChooseSearchIcon(
            HeaderNodes = HeaderNodes,
            ScreenWidth = ScreenWidth,
            ScreenHeight = ScreenHeight
        )
        val TapX: Float
        val TapY: Float
        val MethodText: String
        if (IconBounds != null) {
            TapX = IconBounds.centerX().toFloat()
            TapY = IconBounds.centerY().toFloat()
            MethodText = "node-bounds"
        } else {
            val TitleBounds = HeaderNodes.firstOrNull { SnapItem ->
                SnapItem.TextVal.isNotEmpty() && !IsExcludedHeaderLabel(TextValue = SnapItem.TextVal)
            }?.Bounds ?: HeaderNodes.firstOrNull()?.Bounds
            if (TitleBounds == null) {
                Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { TapSearchIcon() })
                return
            }
            TapX = ScreenWidth * ICON_FALLBACK_X_RATIO
            TapY = TitleBounds.centerY().toFloat()
            MethodText = "header-anchored"
        }

        val TapAccepted = HostRef.CustomerSearchTap(XPos = TapX, YPos = TapY)
        HostRef.CustomerSearchInfo(
            EventName = "CUSTOMER_SEARCH_ICON_TAP",
            MessageText = "method=$MethodText tap=($TapX,$TapY) accepted=$TapAccepted " +
                    "attempt=$IconAttempts"
        )
        StageVal = Stage.WAIT_SEARCH
        SearchWaits = 0
        Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { WaitForSearchScreen() })
    }

    private fun ChooseSearchIcon(
        HeaderNodes: List<NodeSnapshot>,
        ScreenWidth: Int,
        ScreenHeight: Int
    ): Rect? {
        val Usable = HeaderNodes.filter { SnapItem ->
            !IsExcludedHeaderLabel(TextValue = SnapItem.TextVal)
        }
        val Labelled = Usable.filter { SnapItem ->
            SnapItem.TextVal.contains("search", ignoreCase = true)
        }
        if (Labelled.isNotEmpty()) {
            return Labelled.maxByOrNull { SnapItem -> SnapItem.Bounds.centerX() }?.Bounds
        }
        val IconLike = Usable.filter { SnapItem ->
            SnapItem.IsClickable &&
                    SnapItem.Bounds.centerX() >= ScreenWidth * HEADER_RIGHT_RATIO &&
                    SnapItem.Bounds.width() <= ScreenWidth * 0.25f &&
                    SnapItem.Bounds.height() <= ScreenHeight * 0.1f
        }
        return IconLike.maxByOrNull { SnapItem -> SnapItem.Bounds.centerX() }?.Bounds
    }

    private fun IsExcludedHeaderLabel(TextValue: String): Boolean {
        if (TextValue.isEmpty()) return false
        val Lowered = TextValue.lowercase(Locale.US)
        return HEADER_EXCLUDED_LABELS.any { LabelText -> Lowered.contains(LabelText) }
    }

    private fun WaitForSearchScreen() {
        if (IsSheetReady()) {
            OnSearchScreenReady()
            return
        }
        SearchWaits++
        if (SearchWaits > SEARCH_WAIT_LIMIT) {
            StageVal = Stage.OPEN_SEARCH
            Step(DelayMs = STEP_DELAY_MS, ActionRef = { TapSearchIcon() })
            return
        }
        Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { WaitForSearchScreen() })
    }

    private fun OnSearchScreenReady() {
        HostRef.CustomerSearchInfo(
            EventName = "CUSTOMER_SEARCH_OPENED",
            MessageText = "customer=${CurrentTarget()} nodes=${LatestNodes.size}"
        )
        StageVal = Stage.FILTER_CHIP
        QueryAttempts = 0
        Step(DelayMs = STEP_DELAY_MS, ActionRef = { TapCustomersChip() })
    }

    private fun TapCustomersChip() {
        val Snapshots = ReadSnapshots()
        val ScreenHeight = HostRef.CustomerSearchScreenHeight()
        val ChipBounds = Snapshots?.filter { SnapItem ->
            SnapItem.TextVal.equals("Customers", ignoreCase = true) &&
                    SnapItem.Bounds.centerY() >= (ScreenHeight * CHIP_BAND_TOP_RATIO).toInt() &&
                    SnapItem.Bounds.centerY() <= (ScreenHeight * CHIP_BAND_BOTTOM_RATIO).toInt()
        }?.minByOrNull { SnapItem -> SnapItem.Bounds.centerY() }?.Bounds

        if (ChipBounds != null) {
            HostRef.CustomerSearchTap(
                XPos = ChipBounds.centerX().toFloat(),
                YPos = ChipBounds.centerY().toFloat()
            )
        }
        HostRef.CustomerSearchInfo(
            EventName = "CUSTOMER_SEARCH_CHIP",
            MessageText = "customersChip=${ChipBounds ?: "not-found"}"
        )
        StageVal = Stage.TYPE_QUERY
        Step(DelayMs = STEP_DELAY_MS, ActionRef = { TypeQuery() })
    }

    private fun TypeQuery() {
        QueryAttempts++
        if (QueryAttempts > QUERY_ATTEMPT_LIMIT) {
            Fallback(ReasonText = "search field did not accept the customer name")
            return
        }
        val QueryText = QueryTextFor()
        if (QueryText.isEmpty()) {
            Fallback(ReasonText = "no usable name for ${CurrentTarget()}")
            return
        }

        val RootNode = HostRef.CustomerSearchRootNode() ?: run {
            Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { TypeQuery() })
            return
        }

        var FieldNode: AccessibilityNodeInfo? = null
        var FieldBounds: Rect? = null
        var Filled = false
        try {
            val FoundNode = FindEditableNode(TargetNode = RootNode)
            FieldNode = FoundNode
            if (FoundNode != null) {
                FieldBounds = BoundsOf(NodeRef = FoundNode)
                Filled = HostRef.CustomerSearchSetText(NodeRef = FoundNode, TextValue = QueryText)
            }
        } finally {
            HostRef.CustomerSearchRecycleNode(NodeRef = FieldNode)
            HostRef.CustomerSearchRecycleNode(NodeRef = RootNode)
        }

        HostRef.CustomerSearchInfo(
            EventName = "CUSTOMER_SEARCH_QUERY",
            MessageText = "customer=${CurrentTarget()} query=[$QueryText] " +
                    "field=${FieldBounds ?: "not-found"} filled=$Filled attempt=$QueryAttempts"
        )

        if (!Filled) {
            val FieldRect = FieldBounds
            if (FieldRect != null) {
                HostRef.CustomerSearchTap(
                    XPos = FieldRect.centerX().toFloat(),
                    YPos = FieldRect.centerY().toFloat()
                )
            }
            Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { TypeQuery() })
            return
        }

        StageVal = Stage.WAIT_RESULTS
        ResultWaits = 0
        Step(DelayMs = RESULT_WAIT_MS, ActionRef = { WaitForResults() })
    }

    private fun WaitForResults() {
        val TargetName = CurrentTarget()
        if (HasNameOnScreen(NameText = TargetName)) {
            HostRef.CustomerSearchInfo(
                EventName = "CUSTOMER_SEARCH_RESULTS",
                MessageText = "customer=$TargetName matched=true " +
                        "count=${PolicySearchParser.ResultCount(Nodes = LatestNodes) ?: -1}"
            )
            StageVal = Stage.OPEN_ROW
            RowAttempts = 0
            RowWaits = 0
            Step(DelayMs = STEP_DELAY_MS, ActionRef = { OpenRow() })
            return
        }

        ResultWaits++
        if (ResultWaits > RESULT_WAIT_LIMIT) {
            Fallback(ReasonText = "search never listed $TargetName")
            return
        }
        Step(DelayMs = RESULT_WAIT_MS, ActionRef = { WaitForResults() })
    }

    private fun OpenRow() {
        val TargetName = CurrentTarget()

        if (!IsSearchScreenVisible) {
            HostRef.CustomerSearchWarn(
                EventName = "CUSTOMER_SEARCH_SHEET_LOST",
                MessageText = "customer=$TargetName attempt=$RowAttempts; reopening search"
            )
            StageVal = Stage.OPEN_SEARCH
            IconAttempts = 0
            Step(DelayMs = STEP_DELAY_MS, ActionRef = { TapSearchIcon() })
            return
        }

        if (!HasNameOnScreen(NameText = TargetName)) {
            HostRef.CustomerSearchWarn(
                EventName = "CUSTOMER_SEARCH_ROW_LOST",
                MessageText = "customer=$TargetName attempt=$RowAttempts; retyping the query"
            )
            StageVal = Stage.TYPE_QUERY
            QueryAttempts = 0
            Step(DelayMs = STEP_DELAY_MS, ActionRef = { TypeQuery() })
            return
        }

        val Snapshots = ReadSnapshots()
        val RowPair = if (Snapshots == null) {
            Pair(null, null)
        } else {
            ChooseResultRow(Snapshots = Snapshots, TargetName = TargetName)
        }
        val NameRect = RowPair.first
        val ArrowRect = RowPair.second

        if (NameRect == null && ArrowRect == null) {
            RowWaits++
            HostRef.CustomerSearchInfo(
                EventName = "CUSTOMER_SEARCH_ROW_CANDIDATES",
                MessageText = "customer=$TargetName wait=$RowWaits nameBounds=not-found " +
                        "arrowBounds=not-found"
            )
            if (RowWaits > ROW_WAIT_LIMIT) {
                Fallback(ReasonText = "result row for $TargetName never rendered")
                return
            }
            Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { OpenRow() })
            return
        }

        RowAttempts++
        if (RowAttempts > ROW_ATTEMPT_LIMIT) {
            Fallback(ReasonText = "result row for $TargetName would not open")
            return
        }
        HostRef.CustomerSearchInfo(
            EventName = "CUSTOMER_SEARCH_ROW_CANDIDATES",
            MessageText = "customer=$TargetName attempt=$RowAttempts " +
                    "nameBounds=${NameRect ?: "not-found"} arrowBounds=${ArrowRect ?: "not-found"}"
        )

        val ScreenWidth = HostRef.CustomerSearchScreenWidth()
        val TapX: Float
        val TapY: Float
        val MethodText: String
        when {
            ArrowRect != null -> {
                TapX = ArrowRect.centerX().toFloat()
                TapY = ArrowRect.centerY().toFloat()
                MethodText = "arrow-node"
            }

            NameRect != null && RowAttempts <= 2 -> {
                TapX = ScreenWidth * ROW_ARROW_X_RATIO
                TapY = NameRect.centerY().toFloat()
                MethodText = "row-arrow-inset"
            }

            NameRect != null -> {
                TapX = NameRect.centerX().toFloat()
                TapY = NameRect.centerY().toFloat()
                MethodText = "row-text"
            }

            else -> {
                Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { OpenRow() })
                return
            }
        }

        val DangerBounds = DangerNear(
            Snapshots = Snapshots.orEmpty(),
            XPos = TapX,
            YPos = TapY
        )
        if (DangerBounds != null) {
            HostRef.CustomerSearchWarn(
                EventName = "CUSTOMER_SEARCH_TAP_BLOCKED",
                MessageText = "customer=$TargetName tap=($TapX,$TapY) would land on " +
                        "$DangerBounds; refusing"
            )
            Fallback(ReasonText = "result row tap for $TargetName was unsafe")
            return
        }

        val Opened = HostRef.CustomerSearchTap(XPos = TapX, YPos = TapY)
        HostRef.CustomerSearchInfo(
            EventName = "CUSTOMER_SEARCH_ROW_TAP",
            MessageText = "customer=$TargetName method=$MethodText tap=($TapX,$TapY) " +
                    "accepted=$Opened attempt=$RowAttempts"
        )

        if (!Opened) {
            Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { OpenRow() })
            return
        }

        StageVal = Stage.DETAIL
        IsSearchScreenVisible = false
        IsEntryVisibleVal = false
        LastStepAt = System.currentTimeMillis()
        HostRef.CustomerSearchOpenDetail(NameTextVal = TargetName)
    }

    private fun ChooseResultRow(
        Snapshots: List<NodeSnapshot>,
        TargetName: String
    ): Pair<Rect?, Rect?> {
        val ScreenHeight = HostRef.CustomerSearchScreenHeight()
        val WantedName = NormalisedOf(TextValue = TargetName)
        val ResultsFloor = ResultsFloorOf(Snapshots = Snapshots)
        val RowNodes = Snapshots.filter { SnapItem ->
            !SnapItem.IsEditable &&
                    SnapItem.TextVal.isNotEmpty() &&
                    SnapItem.Bounds.top >= ResultsFloor &&
                    SnapItem.Bounds.height() <= ScreenHeight * ROW_HEIGHT_RATIO
        }
        val ExactNodes = RowNodes.filter { SnapItem ->
            NormalisedOf(TextValue = SnapItem.TextVal) == WantedName
        }
        val StrippedNodes = RowNodes.filter { SnapItem ->
            StrippedNameOf(TextValue = SnapItem.TextVal) == WantedName
        }
        val ContainingNodes = RowNodes.filter { SnapItem ->
            ContainsWholeName(TextValue = SnapItem.TextVal, WantedName = WantedName)
        }.sortedBy { SnapItem -> SnapItem.TextVal.length }
        val NameNodes = when {
            ExactNodes.isNotEmpty() -> ExactNodes
            StrippedNodes.isNotEmpty() -> StrippedNodes
            else -> ContainingNodes
        }
        if (NameNodes.isEmpty()) return Pair(null, null)
        if (NameNodes.size > 1 || ContainingNodes.size > 1) {
            HostRef.CustomerSearchInfo(
                EventName = "CUSTOMER_SEARCH_ROW_MATCHES",
                MessageText = "customer=$TargetName exact=${ExactNodes.size} " +
                        "stripped=${StrippedNodes.size} containing=${ContainingNodes.size} " +
                        "picked=[${NameNodes.first().TextVal}]"
            )
        }

        val ArrowNodes = Snapshots.filter { SnapItem ->
            IsResultArrowLabel(TextValue = SnapItem.TextVal)
        }
        var BestName: Rect? = null
        var BestArrow: Rect? = null
        var BestDistance = Int.MAX_VALUE
        for (NameItem in NameNodes) {
            for (ArrowItem in ArrowNodes) {
                if (ArrowItem.Bounds.centerX() <= NameItem.Bounds.centerX()) continue
                val DistanceVal = abs(ArrowItem.Bounds.centerY() - NameItem.Bounds.centerY())
                if (DistanceVal > ScreenHeight * ROW_PAIR_RATIO) continue
                if (DistanceVal >= BestDistance) continue
                BestDistance = DistanceVal
                BestName = NameItem.Bounds
                BestArrow = ArrowItem.Bounds
            }
        }
        if (BestName != null) return Pair(BestName, BestArrow)
        return Pair(NameNodes.first().Bounds, null)
    }

    private fun IsSheetReady(): Boolean {
        if (!IsSearchScreenVisible) return false
        val Snapshots = ReadSnapshots() ?: return false
        val ScreenHeight = HostRef.CustomerSearchScreenHeight()
        val FieldNode = Snapshots.firstOrNull { SnapItem ->
            SnapItem.IsEditable &&
                    !SnapItem.Bounds.isEmpty &&
                    SnapItem.Bounds.top >= 0 &&
                    SnapItem.Bounds.centerY() <= ScreenHeight * SHEET_FIELD_BAND_RATIO
        }
        if (FieldNode == null) {
            HostRef.CustomerSearchInfo(
                EventName = "CUSTOMER_SEARCH_SHEET_STALE",
                MessageText = "search markers are in the tree but no on-screen field; " +
                        "treating the sheet as closed"
            )
            return false
        }
        return true
    }

    private fun ResultsFloorOf(Snapshots: List<NodeSnapshot>): Int {
        for (MarkerText in PolicySearchParser.ResultsFloorMarkers()) {
            val MarkerNode = Snapshots.firstOrNull { SnapItem ->
                SnapItem.TextVal.contains(MarkerText, ignoreCase = true)
            }
            if (MarkerNode != null) return MarkerNode.Bounds.bottom
        }
        return 0
    }

    private fun DangerNear(Snapshots: List<NodeSnapshot>, XPos: Float, YPos: Float): Rect? {
        if (Snapshots.isEmpty()) return null
        val Clearance = (HostRef.CustomerSearchScreenHeight() * DANGER_CLEARANCE_RATIO).toInt()
        return Snapshots.firstOrNull { SnapItem ->
            IsDangerLabel(TextValue = SnapItem.TextVal) &&
                    XPos >= SnapItem.Bounds.left - Clearance &&
                    XPos <= SnapItem.Bounds.right + Clearance &&
                    YPos >= SnapItem.Bounds.top - Clearance &&
                    YPos <= SnapItem.Bounds.bottom + Clearance
        }?.Bounds
    }

    private fun IsDangerLabel(TextValue: String): Boolean {
        if (TextValue.isEmpty()) return false
        val Lowered = TextValue.lowercase(Locale.US)
        return DANGER_LABELS.any { LabelText -> Lowered.contains(LabelText) }
    }

    private fun IsResultArrowLabel(TextValue: String): Boolean {
        if (TextValue.isEmpty()) return false
        val Lowered = TextValue.lowercase(Locale.US).replace(" ", "").replace("_", "")
        if (Lowered.contains(DASHBOARD_ARROW_LABEL)) return false
        return ROW_ARROW_LABELS.any { LabelText -> Lowered.contains(LabelText) }
    }

    private fun HasNameOnScreen(NameText: String): Boolean {
        if (NameText.isEmpty()) return false
        val WantedName = NormalisedOf(TextValue = NameText)
        return LatestNodes.any { NodeText ->
            NormalisedOf(TextValue = NodeText) == WantedName ||
                    StrippedNameOf(TextValue = NodeText) == WantedName ||
                    ContainsWholeName(TextValue = NodeText, WantedName = WantedName)
        }
    }

    private fun StrippedNameOf(TextValue: String): String {
        return NormalisedOf(TextValue = ROLE_MARKER_REGEX.replace(TextValue, " "))
    }

    private fun ContainsWholeName(TextValue: String, WantedName: String): Boolean {
        if (WantedName.isEmpty()) return false
        val Haystack = StrippedNameOf(TextValue = TextValue)
        if (Haystack == WantedName) return true
        val MatchIdx = Haystack.indexOf(WantedName)
        if (MatchIdx < 0) return false
        val BeforeChar = Haystack.getOrNull(MatchIdx - 1)
        val AfterChar = Haystack.getOrNull(MatchIdx + WantedName.length)
        val StartsClean = BeforeChar == null || !BeforeChar.isLetterOrDigit()
        val EndsClean = AfterChar == null || !AfterChar.isLetterOrDigit()
        return StartsClean && EndsClean
    }

    private fun NormalisedOf(TextValue: String): String {
        return TextValue.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
    }

    private fun Fallback(ReasonText: String) {
        HostRef.CustomerSearchWarn(
            EventName = "CUSTOMER_TARGET_FALLBACK",
            MessageText = "stage=${StageVal.name.lowercase(Locale.US)} customer=${CurrentTarget()} " +
                    "reason=[$ReasonText]"
        )
        IsArmed = false
        IsDriving = false
        StageVal = Stage.IDLE
        IsBackingOut = true
        BackOutAndHandOff(AttemptCount = 0)
    }

    private fun BackOutAndHandOff(AttemptCount: Int) {
        if (IsEntryVisibleVal || AttemptCount >= BACKOUT_LIMIT) {
            IsBackingOut = false
            HostRef.CustomerSearchHandOffToDashboard()
            return
        }
        HostRef.CustomerSearchPressBack()
        HostRef.CustomerSearchSchedule(
            DelayMs = SCREEN_WAIT_MS,
            ActionRef = {
                HostRef.CustomerSearchRefreshScreen()
                BackOutAndHandOff(AttemptCount = AttemptCount + 1)
            }
        )
    }

    private fun Step(DelayMs: Long, ActionRef: () -> Unit) {
        LastStepAt = System.currentTimeMillis()
        HostRef.CustomerSearchSchedule(
            DelayMs = DelayMs,
            ActionRef = {
                LastStepAt = System.currentTimeMillis()
                if (IsArmed && IsDriving) {
                    HostRef.CustomerSearchRefreshScreen()
                    if (IsArmed && IsDriving) ActionRef()
                }
            }
        )
    }

    private fun HasStalled(): Boolean {
        if (LastStepAt <= 0L) return false
        val LimitMs = if (StageVal == Stage.DETAIL) DETAIL_STALL_LIMIT_MS else STALL_LIMIT_MS
        return System.currentTimeMillis() - LastStepAt > LimitMs
    }

    private fun CurrentTarget(): String {
        return Targets.getOrNull(TargetIndex).orEmpty()
    }

    private fun QueryTextFor(): String {
        val TargetName = CurrentTarget()
        val FirstName = PolicySearchParser.FirstNameOf(NameText = TargetName)
        return FirstName.ifEmpty { TargetName }
    }

    private fun ReadSnapshots(): List<NodeSnapshot>? {
        val RootNode = HostRef.CustomerSearchRootNode() ?: return null
        val ResultList = mutableListOf<NodeSnapshot>()
        try {
            CollectSnapshots(TargetNode = RootNode, ResultList = ResultList)
        } finally {
            HostRef.CustomerSearchRecycleNode(NodeRef = RootNode)
        }
        return ResultList
    }

    private fun CollectSnapshots(
        TargetNode: AccessibilityNodeInfo,
        ResultList: MutableList<NodeSnapshot>
    ) {
        try {
            val BoundsObj = BoundsOf(NodeRef = TargetNode)
            if (!BoundsObj.isEmpty) {
                ResultList.add(
                    NodeSnapshot(
                        TextVal = TextOf(NodeRef = TargetNode),
                        Bounds = BoundsObj,
                        IsClickable = TargetNode.isClickable,
                        IsEditable = TargetNode.isEditable
                    )
                )
            }
            for (ChildIndex in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIndex) ?: continue
                try {
                    CollectSnapshots(TargetNode = ChildNode, ResultList = ResultList)
                } finally {
                    HostRef.CustomerSearchRecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            HostRef.CustomerSearchWarn(
                EventName = "CUSTOMER_SEARCH_TREE_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
        }
    }

    private fun FindEditableNode(TargetNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        try {
            for (ChildIndex in 0 until TargetNode.childCount) {
                val ChildNode = TargetNode.getChild(ChildIndex) ?: continue
                val ClassNameText = ChildNode.className?.toString().orEmpty()
                val IsTextEntry = ChildNode.isEditable ||
                        ClassNameText.contains("EditText", ignoreCase = true)
                if (IsTextEntry && !BoundsOf(NodeRef = ChildNode).isEmpty) return ChildNode
                val NestedNode = FindEditableNode(TargetNode = ChildNode)
                HostRef.CustomerSearchRecycleNode(NodeRef = ChildNode)
                if (NestedNode != null) return NestedNode
            }
        } catch (ExceptionObj: Exception) {
            HostRef.CustomerSearchWarn(
                EventName = "CUSTOMER_SEARCH_FIELD_ERROR",
                MessageText = "${ExceptionObj.javaClass.simpleName}: ${ExceptionObj.message.orEmpty()}"
            )
        }
        return null
    }

    private fun TextOf(NodeRef: AccessibilityNodeInfo): String {
        return try {
            NodeRef.text?.toString()?.trim()
                .takeUnless { TextValue -> TextValue.isNullOrEmpty() }
                ?: NodeRef.contentDescription?.toString()?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun BoundsOf(NodeRef: AccessibilityNodeInfo): Rect {
        val BoundsObj = Rect()
        try {
            NodeRef.getBoundsInScreen(BoundsObj)
        } catch (_: Exception) {
            BoundsObj.setEmpty()
        }
        return BoundsObj
    }
}
