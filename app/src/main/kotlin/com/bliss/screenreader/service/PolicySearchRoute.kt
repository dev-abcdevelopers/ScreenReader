@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused")

package com.bliss.screenreader.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import com.bliss.screenreader.data.parser.PolicySearchParser

interface PolicySearchHost {
    fun SearchRootNode(): AccessibilityNodeInfo?
    fun SearchRecycleNode(NodeRef: AccessibilityNodeInfo?)
    fun SearchTap(XPos: Float, YPos: Float): Boolean
    fun SearchSetText(NodeRef: AccessibilityNodeInfo, TextValue: String): Boolean
    fun SearchSchedule(DelayMs: Long, ActionRef: () -> Unit)
    fun SearchRefreshScreen()
    fun SearchInfo(EventName: String, MessageText: String)
    fun SearchWarn(EventName: String, MessageText: String)
    fun SearchScreenWidth(): Int
    fun SearchScreenHeight(): Int
    fun SearchBeginRun()
    fun SearchOpenDetail(PolicyNumberVal: String)
    fun SearchPressBack(): Boolean
    fun SearchHandOffToPageWalk()
    fun SearchFinishRun()
    fun SearchNoteRowContact(PolicyNumberVal: String, MobileText: String, AgeText: String)
}

class PolicySearchRoute(private val HostRef: PolicySearchHost) {

    private enum class Stage {
        IDLE, OPEN_SEARCH, WAIT_SEARCH, FILTER_CHIP, TYPE_QUERY, WAIT_RESULTS, OPEN_ROW, DETAIL
    }

    private enum class QueryKind { NUMBER, NAME }

    private data class NodeSnapshot(
        val TextVal: String,
        val Bounds: Rect,
        val IsClickable: Boolean,
        val IsEditable: Boolean
    )

    companion object {
        private const val MAX_TARGETS = 5
        private const val STEP_DELAY_MS = 450L
        private const val SCREEN_WAIT_MS = 1200L
        private const val RESULT_WAIT_MS = 900L
        private const val ICON_ATTEMPT_LIMIT = 3
        private const val SEARCH_WAIT_LIMIT = 5
        private const val QUERY_ATTEMPT_LIMIT = 3
        private const val RESULT_WAIT_LIMIT = 6
        private const val ROW_ATTEMPT_LIMIT = 3
        private const val ROW_WAIT_LIMIT = 4
        private const val RETURN_WAIT_LIMIT = 5
        private const val STALL_LIMIT_MS = 25_000L
        private const val DETAIL_STALL_LIMIT_MS = 150_000L
        private const val HEADER_BAND_RATIO = 0.14f
        private const val HEADER_RIGHT_RATIO = 0.62f
        private const val CHIP_BAND_TOP_RATIO = 0.10f
        private const val CHIP_BAND_BOTTOM_RATIO = 0.34f
        private const val ICON_FALLBACK_X_RATIO = 0.92f
        private const val ROW_ARROW_X_RATIO = 0.94f
        private const val ROW_HEIGHT_RATIO = 0.12f
        private const val ROW_PAIR_RATIO = 0.06f

        private val ROW_ARROW_LABELS = listOf("righticon", "rightarrowicon", "arrowright")
        private const val DASHBOARD_ARROW_LABEL = "cardrightarrow"

        private val HEADER_EXCLUDED_LABELS = listOf(
            "back", "close", "policy dashboard", "filter", "sort", "date range"
        )
    }

    var IsArmed = false
        private set

    var IsDriving = false
        private set

    private var StageVal = Stage.IDLE
    private var Targets: List<String> = emptyList()
    private var NameHints: Map<String, String> = emptyMap()
    private var TargetIndex = 0
    private var QueryKindVal = QueryKind.NUMBER
    private var IconAttempts = 0
    private var SearchWaits = 0
    private var QueryAttempts = 0
    private var ResultWaits = 0
    private var RowAttempts = 0
    private var RowWaits = 0
    private var ReturnWaits = 0
    private var LastStepAt = 0L
    private var IsSearchScreenVisible = false
    private var IsDashboardVisibleVal = false
    private var LatestNodes: List<String> = emptyList()

    fun Reset() {
        IsArmed = false
        IsDriving = false
        StageVal = Stage.IDLE
        Targets = emptyList()
        NameHints = emptyMap()
        TargetIndex = 0
        QueryKindVal = QueryKind.NUMBER
        IconAttempts = 0
        SearchWaits = 0
        QueryAttempts = 0
        ResultWaits = 0
        RowAttempts = 0
        RowWaits = 0
        ReturnWaits = 0
        LastStepAt = 0L
        IsSearchScreenVisible = false
        IsDashboardVisibleVal = false
        LatestNodes = emptyList()
    }

    fun Arm(TargetsVal: List<String>, NameHintsVal: Map<String, String>) {
        Reset()
        if (TargetsVal.isEmpty()) return
        if (TargetsVal.size > MAX_TARGETS) {
            HostRef.SearchInfo(
                EventName = "POLICY_TARGET_START",
                MessageText = "targets=${TargetsVal.size} route=page-walk " +
                        "reason=[more than $MAX_TARGETS targets]"
            )
            return
        }
        Targets = TargetsVal
        NameHints = NameHintsVal
        IsArmed = true
        HostRef.SearchInfo(
            EventName = "POLICY_TARGET_START",
            MessageText = "targets=${Targets.joinToString(separator = ",")} route=search"
        )
    }

    fun HandleScreen(VisibleNodes: List<String>, IsDashboardVisible: Boolean): Boolean {
        if (!IsArmed) return false
        LatestNodes = VisibleNodes
        IsSearchScreenVisible = PolicySearchParser.IsSearchScreen(Nodes = VisibleNodes)
        IsDashboardVisibleVal = IsDashboardVisible && !IsSearchScreenVisible

        if (!IsDriving) {
            if (!IsDashboardVisibleVal) return false
            BeginRoute()
            return true
        }

        if (HasStalled()) {
            Fallback(ReasonText = "route stalled at ${StageVal.name.lowercase()}")
            return false
        }
        return true
    }

    fun OnDetailOpenFailed() {
        if (!IsDriving) return
        StageVal = Stage.OPEN_ROW
        HostRef.SearchWarn(
            EventName = "POLICY_SEARCH_ROW_RETRY",
            MessageText = "policy=${CurrentTarget()} attempts=$RowAttempts"
        )
        Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { OpenRow() })
    }

    fun OnDetailReturn() {
        if (!IsDriving) return
        if (IsSearchScreenVisible || IsDashboardVisibleVal) {
            AdvanceTarget()
            return
        }
        ReturnWaits++
        if (ReturnWaits > RETURN_WAIT_LIMIT) {
            Fallback(ReasonText = "could not return from policy ${CurrentTarget()}")
            return
        }
        HostRef.SearchPressBack()
        Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { OnDetailReturn() })
    }

    private fun BeginRoute() {
        IsDriving = true
        StageVal = Stage.OPEN_SEARCH
        IconAttempts = 0
        HostRef.SearchBeginRun()
        HostRef.SearchInfo(
            EventName = "POLICY_TARGET_ROUTE",
            MessageText = "stage=open-search target=${CurrentTarget()} " +
                    "index=${TargetIndex + 1}/${Targets.size}"
        )
        Step(DelayMs = STEP_DELAY_MS, ActionRef = { TapSearchIcon() })
    }

    private fun TapSearchIcon() {
        if (IsSearchScreenVisible) {
            OnSearchScreenReady()
            return
        }
        IconAttempts++
        if (IconAttempts > ICON_ATTEMPT_LIMIT) {
            Fallback(ReasonText = "search control was never found in the dashboard header")
            return
        }

        val Snapshots = ReadSnapshots() ?: run {
            Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { TapSearchIcon() })
            return
        }

        val ScreenWidth = HostRef.SearchScreenWidth()
        val ScreenHeight = HostRef.SearchScreenHeight()
        val HeaderNodes = Snapshots.filter { SnapItem ->
            SnapItem.Bounds.centerY() in 1..(ScreenHeight * HEADER_BAND_RATIO).toInt()
        }
        val CandidateText = HeaderNodes.joinToString(separator = " ") { SnapItem ->
            val LabelText = if (SnapItem.TextVal.isEmpty()) "-" else SnapItem.TextVal
            "[" + LabelText + "]" + SnapItem.Bounds + "clickable=" + SnapItem.IsClickable
        }
        HostRef.SearchInfo(
            EventName = "POLICY_SEARCH_ICON_CANDIDATES",
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
                SnapItem.TextVal.equals("Policy Dashboard", ignoreCase = true)
            }?.Bounds
            if (TitleBounds == null) {
                Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { TapSearchIcon() })
                return
            }
            TapX = ScreenWidth * ICON_FALLBACK_X_RATIO
            TapY = TitleBounds.centerY().toFloat()
            MethodText = "title-anchored"
        }

        val TapAccepted = HostRef.SearchTap(XPos = TapX, YPos = TapY)
        HostRef.SearchInfo(
            EventName = "POLICY_SEARCH_ICON_TAP",
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
        val Lowered = TextValue.lowercase()
        return HEADER_EXCLUDED_LABELS.any { LabelText -> Lowered.contains(LabelText) }
    }

    private fun WaitForSearchScreen() {
        if (IsSearchScreenVisible) {
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
        HostRef.SearchInfo(
            EventName = "POLICY_SEARCH_OPENED",
            MessageText = "target=${CurrentTarget()} nodes=${LatestNodes.size} " +
                    "existingRows=${PolicySearchParser.DescribeResults(Nodes = LatestNodes)}"
        )
        StageVal = Stage.FILTER_CHIP
        QueryKindVal = QueryKind.NUMBER
        QueryAttempts = 0
        Step(DelayMs = STEP_DELAY_MS, ActionRef = { TapPoliciesChip() })
    }

    private fun TapPoliciesChip() {
        val Snapshots = ReadSnapshots()
        val ScreenHeight = HostRef.SearchScreenHeight()
        val ChipBounds = Snapshots?.filter { SnapItem ->
            SnapItem.TextVal.equals("Policies", ignoreCase = true) &&
                    SnapItem.Bounds.centerY() >= (ScreenHeight * CHIP_BAND_TOP_RATIO).toInt() &&
                    SnapItem.Bounds.centerY() <= (ScreenHeight * CHIP_BAND_BOTTOM_RATIO).toInt()
        }?.minByOrNull { SnapItem -> SnapItem.Bounds.centerY() }?.Bounds

        if (ChipBounds != null) {
            HostRef.SearchTap(
                XPos = ChipBounds.centerX().toFloat(),
                YPos = ChipBounds.centerY().toFloat()
            )
        }
        HostRef.SearchInfo(
            EventName = "POLICY_SEARCH_CHIP",
            MessageText = "policiesChip=${ChipBounds ?: "not-found"}"
        )
        StageVal = Stage.TYPE_QUERY
        Step(DelayMs = STEP_DELAY_MS, ActionRef = { TypeQuery() })
    }

    private fun TypeQuery() {
        QueryAttempts++
        if (QueryAttempts > QUERY_ATTEMPT_LIMIT) {
            Fallback(ReasonText = "search field did not accept text")
            return
        }
        val QueryText = QueryTextFor()
        if (QueryText.isEmpty()) {
            Fallback(ReasonText = "no usable query text for ${CurrentTarget()}")
            return
        }

        val RootNode = HostRef.SearchRootNode() ?: run {
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
                Filled = HostRef.SearchSetText(NodeRef = FoundNode, TextValue = QueryText)
            }
        } finally {
            HostRef.SearchRecycleNode(NodeRef = FieldNode)
            HostRef.SearchRecycleNode(NodeRef = RootNode)
        }

        HostRef.SearchInfo(
            EventName = "POLICY_SEARCH_QUERY",
            MessageText = "policy=${CurrentTarget()} kind=${QueryKindVal.name.lowercase()} " +
                    "query=[$QueryText] field=${FieldBounds ?: "not-found"} filled=$Filled " +
                    "attempt=$QueryAttempts"
        )

        if (!Filled) {
            val FieldRect = FieldBounds
            if (FieldRect != null) {
                HostRef.SearchTap(
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
        val TargetNumber = CurrentTarget()
        if (PolicySearchParser.ContainsPolicyNumber(
                Nodes = LatestNodes,
                PolicyNumber = TargetNumber
            )
        ) {
            HostRef.SearchInfo(
                EventName = "POLICY_SEARCH_RESULTS",
                MessageText = "policy=$TargetNumber matched=true " +
                        "count=${PolicySearchParser.ResultCount(Nodes = LatestNodes) ?: -1} " +
                        "rows=${PolicySearchParser.DescribeResults(Nodes = LatestNodes)}"
            )
            StageVal = Stage.OPEN_ROW
            RowAttempts = 0
            RowWaits = 0
            Step(DelayMs = STEP_DELAY_MS, ActionRef = { OpenRow() })
            return
        }

        ResultWaits++
        val IsEmptyList = PolicySearchParser.IsEmptyResult(Nodes = LatestNodes)
        val ShouldTryName = QueryKindVal == QueryKind.NUMBER &&
                NameQueryFor(PolicyNumber = TargetNumber).isNotEmpty() &&
                (IsEmptyList || ResultWaits >= RESULT_WAIT_LIMIT / 2)
        if (ShouldTryName) {
            HostRef.SearchInfo(
                EventName = "POLICY_SEARCH_RESULTS",
                MessageText = "policy=$TargetNumber matched=false empty=$IsEmptyList " +
                        "switchingTo=name"
            )
            QueryKindVal = QueryKind.NAME
            QueryAttempts = 0
            StageVal = Stage.TYPE_QUERY
            Step(DelayMs = STEP_DELAY_MS, ActionRef = { TypeQuery() })
            return
        }

        if (ResultWaits > RESULT_WAIT_LIMIT) {
            Fallback(ReasonText = "search never listed $TargetNumber")
            return
        }
        Step(DelayMs = RESULT_WAIT_MS, ActionRef = { WaitForResults() })
    }

    private fun OpenRow() {
        val TargetNumber = CurrentTarget()

        if (!IsSearchScreenVisible) {
            HostRef.SearchWarn(
                EventName = "POLICY_SEARCH_SHEET_LOST",
                MessageText = "policy=$TargetNumber attempt=$RowAttempts; reopening search"
            )
            StageVal = Stage.OPEN_SEARCH
            IconAttempts = 0
            Step(DelayMs = STEP_DELAY_MS, ActionRef = { TapSearchIcon() })
            return
        }

        if (!PolicySearchParser.ContainsPolicyNumber(
                Nodes = LatestNodes,
                PolicyNumber = TargetNumber
            )
        ) {
            HostRef.SearchWarn(
                EventName = "POLICY_SEARCH_ROW_LOST",
                MessageText = "policy=$TargetNumber attempt=$RowAttempts; retyping the query"
            )
            StageVal = Stage.TYPE_QUERY
            QueryAttempts = 0
            QueryKindVal = QueryKind.NUMBER
            Step(DelayMs = STEP_DELAY_MS, ActionRef = { TypeQuery() })
            return
        }

        val Snapshots = ReadSnapshots()
        val RowPair = if (Snapshots == null) {
            Pair(null, null)
        } else {
            ChooseResultRow(Snapshots = Snapshots, TargetNumber = TargetNumber)
        }
        val NumberRect = RowPair.first
        val ArrowRect = RowPair.second

        if (NumberRect == null && ArrowRect == null) {
            RowWaits++
            HostRef.SearchInfo(
                EventName = "POLICY_SEARCH_ROW_CANDIDATES",
                MessageText = "policy=$TargetNumber wait=$RowWaits numberBounds=not-found " +
                        "arrowBounds=not-found"
            )
            if (RowWaits > ROW_WAIT_LIMIT) {
                Fallback(ReasonText = "result row for $TargetNumber never rendered")
                return
            }
            Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { OpenRow() })
            return
        }

        RowAttempts++
        if (RowAttempts > ROW_ATTEMPT_LIMIT) {
            Fallback(ReasonText = "result row for $TargetNumber would not open")
            return
        }
        HostRef.SearchInfo(
            EventName = "POLICY_SEARCH_ROW_CANDIDATES",
            MessageText = "policy=$TargetNumber attempt=$RowAttempts " +
                    "numberBounds=${NumberRect ?: "not-found"} " +
                    "arrowBounds=${ArrowRect ?: "not-found"}"
        )

        val ScreenWidth = HostRef.SearchScreenWidth()
        val TapX: Float
        val TapY: Float
        val MethodText: String
        when {
            ArrowRect != null -> {
                TapX = ArrowRect.centerX().toFloat()
                TapY = ArrowRect.centerY().toFloat()
                MethodText = "arrow-node"
            }

            NumberRect != null && RowAttempts <= 2 -> {
                TapX = ScreenWidth * ROW_ARROW_X_RATIO
                TapY = NumberRect.centerY().toFloat()
                MethodText = "row-arrow-inset"
            }

            NumberRect != null -> {
                TapX = NumberRect.centerX().toFloat()
                TapY = NumberRect.centerY().toFloat()
                MethodText = "row-text"
            }

            else -> {
                Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { OpenRow() })
                return
            }
        }

        val Opened = HostRef.SearchTap(XPos = TapX, YPos = TapY)
        HostRef.SearchInfo(
            EventName = "POLICY_SEARCH_ROW_TAP",
            MessageText = "policy=$TargetNumber method=$MethodText tap=($TapX,$TapY) " +
                    "accepted=$Opened attempt=$RowAttempts"
        )

        if (!Opened) {
            Step(DelayMs = SCREEN_WAIT_MS, ActionRef = { OpenRow() })
            return
        }

        val RowInfo = PolicySearchParser.RowFor(
            Nodes = LatestNodes,
            PolicyNumber = TargetNumber
        )
        if (RowInfo != null) {
            HostRef.SearchNoteRowContact(
                PolicyNumberVal = TargetNumber,
                MobileText = RowInfo.MobileNumber,
                AgeText = RowInfo.AgeText
            )
        }

        StageVal = Stage.DETAIL
        ReturnWaits = 0
        IsSearchScreenVisible = false
        IsDashboardVisibleVal = false
        LastStepAt = System.currentTimeMillis()
        HostRef.SearchOpenDetail(PolicyNumberVal = TargetNumber)
    }

    private fun ChooseResultRow(
        Snapshots: List<NodeSnapshot>,
        TargetNumber: String
    ): Pair<Rect?, Rect?> {
        val ScreenHeight = HostRef.SearchScreenHeight()
        val BoundedRegex = Regex("(?<!\\d)" + Regex.escape(TargetNumber) + "(?!\\d)")
        val ResultsFloor = ResultsFloorOf(Snapshots = Snapshots)
        val NumberNodes = Snapshots.filter { SnapItem ->
            !SnapItem.IsEditable &&
                    SnapItem.Bounds.top >= ResultsFloor &&
                    SnapItem.Bounds.height() <= ScreenHeight * ROW_HEIGHT_RATIO &&
                    BoundedRegex.containsMatchIn(SnapItem.TextVal)
        }
        if (NumberNodes.isEmpty()) return Pair(null, null)

        val ArrowNodes = Snapshots.filter { SnapItem ->
            IsResultArrowLabel(TextValue = SnapItem.TextVal)
        }
        var BestNumber: Rect? = null
        var BestArrow: Rect? = null
        var BestDistance = Int.MAX_VALUE
        for (NumberItem in NumberNodes) {
            for (ArrowItem in ArrowNodes) {
                if (ArrowItem.Bounds.centerX() <= NumberItem.Bounds.centerX()) continue
                val DistanceVal = abs(ArrowItem.Bounds.centerY() - NumberItem.Bounds.centerY())
                if (DistanceVal > ScreenHeight * ROW_PAIR_RATIO) continue
                if (DistanceVal >= BestDistance) continue
                BestDistance = DistanceVal
                BestNumber = NumberItem.Bounds
                BestArrow = ArrowItem.Bounds
            }
        }
        if (BestNumber != null) return Pair(BestNumber, BestArrow)
        return Pair(NumberNodes.first().Bounds, null)
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

    private fun IsResultArrowLabel(TextValue: String): Boolean {
        if (TextValue.isEmpty()) return false
        val Lowered = TextValue.lowercase().replace(" ", "").replace("_", "")
        if (Lowered.contains(DASHBOARD_ARROW_LABEL)) return false
        return ROW_ARROW_LABELS.any { LabelText -> Lowered.contains(LabelText) }
    }

    private fun AdvanceTarget() {
        TargetIndex++
        ReturnWaits = 0
        RowAttempts = 0
        RowWaits = 0
        ResultWaits = 0
        QueryAttempts = 0
        QueryKindVal = QueryKind.NUMBER

        if (TargetIndex >= Targets.size) {
            HostRef.SearchInfo(
                EventName = "POLICY_TARGET_COMPLETE",
                MessageText = "captured=${Targets.size} route=search"
            )
            IsArmed = false
            IsDriving = false
            StageVal = Stage.IDLE
            HostRef.SearchFinishRun()
            return
        }

        HostRef.SearchInfo(
            EventName = "POLICY_TARGET_ROUTE",
            MessageText = "stage=next-target target=${CurrentTarget()} " +
                    "index=${TargetIndex + 1}/${Targets.size}"
        )
        if (IsSearchScreenVisible) {
            StageVal = Stage.TYPE_QUERY
            Step(DelayMs = STEP_DELAY_MS, ActionRef = { TypeQuery() })
            return
        }
        StageVal = Stage.OPEN_SEARCH
        IconAttempts = 0
        Step(DelayMs = STEP_DELAY_MS, ActionRef = { TapSearchIcon() })
    }

    private fun Fallback(ReasonText: String) {
        HostRef.SearchWarn(
            EventName = "POLICY_TARGET_FALLBACK",
            MessageText = "stage=${StageVal.name.lowercase()} target=${CurrentTarget()} " +
                    "reason=[$ReasonText]"
        )
        val NeedsBackOut = !IsDashboardVisibleVal
        IsArmed = false
        IsDriving = false
        StageVal = Stage.IDLE
        if (NeedsBackOut) {
            HostRef.SearchPressBack()
            HostRef.SearchSchedule(
                DelayMs = SCREEN_WAIT_MS,
                ActionRef = { HostRef.SearchHandOffToPageWalk() }
            )
            return
        }
        HostRef.SearchHandOffToPageWalk()
    }

    private fun Step(DelayMs: Long, ActionRef: () -> Unit) {
        LastStepAt = System.currentTimeMillis()
        HostRef.SearchSchedule(
            DelayMs = DelayMs,
            ActionRef = {
                LastStepAt = System.currentTimeMillis()
                if (IsArmed && IsDriving) {
                    HostRef.SearchRefreshScreen()
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

    private fun NameQueryFor(PolicyNumber: String): String {
        return PolicySearchParser.FirstNameOf(NameText = NameHints[PolicyNumber].orEmpty())
    }

    private fun QueryTextFor(): String {
        val TargetNumber = CurrentTarget()
        if (QueryKindVal == QueryKind.NUMBER) return TargetNumber
        return NameQueryFor(PolicyNumber = TargetNumber)
    }

    private fun ReadSnapshots(): List<NodeSnapshot>? {
        val RootNode = HostRef.SearchRootNode() ?: return null
        val ResultList = mutableListOf<NodeSnapshot>()
        try {
            CollectSnapshots(TargetNode = RootNode, ResultList = ResultList)
        } finally {
            HostRef.SearchRecycleNode(NodeRef = RootNode)
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
                    HostRef.SearchRecycleNode(NodeRef = ChildNode)
                }
            }
        } catch (ExceptionObj: Exception) {
            HostRef.SearchWarn(
                EventName = "POLICY_SEARCH_TREE_ERROR",
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
                HostRef.SearchRecycleNode(NodeRef = ChildNode)
                if (NestedNode != null) return NestedNode
            }
        } catch (ExceptionObj: Exception) {
            HostRef.SearchWarn(
                EventName = "POLICY_SEARCH_FIELD_ERROR",
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
