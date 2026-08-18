@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused",
    "SpellCheckingInspection"
)

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.ContactValue
import com.bliss.screenreader.data.model.CustomerProfile

object CustomerProfileParser {

    enum class ContactKind { MOBILE, EMAIL, ADDRESS }

    const val LABEL_MOBILE = "Mobile Number"
    const val LABEL_EMAIL = "Email ID"
    const val LABEL_ADDRESS = "Communication Address"
    const val LABEL_DOB = "Date of Birth"
    const val LABEL_GENDER = "Gender"
    const val LABEL_EDUCATION = "Education"
    const val LABEL_OCCUPATION = "Occupation"
    const val LABEL_MARITAL = "Marital Status"
    const val LABEL_INCOME = "Annual Income"

    const val SECTION_CONTACT = "Contact Details"
    const val SECTION_PERSONAL = "Personal Details"
    const val LINK_VIEW_ALL = "View all"
    const val RELATED_PREFIX = "Policy(ies) Related"

    private val FIELD_LABELS = listOf(
        LABEL_MOBILE, LABEL_EMAIL, LABEL_ADDRESS, LABEL_DOB, LABEL_GENDER,
        LABEL_EDUCATION, LABEL_OCCUPATION, LABEL_MARITAL, LABEL_INCOME
    )

    private val SECTION_LABELS = listOf(
        SECTION_CONTACT, SECTION_PERSONAL, "Policies", "Profile", "Add Favourite",
        "Relationship With Customer", "Total Sum Assured", "Annualized Premium(excl. GST)",
        "Annualized Premium (excl. GST)"
    )

    private val NOISE_VALUES = listOf(LINK_VIEW_ALL, "-", "--", "N/A", "NA")

    private val CHIP_REGEX = Regex("^\\+\\d{1,2}$")
    private val MOBILE_REGEX = Regex("^[6-9]\\d{9}$")
    private val PINCODE_REGEX = Regex("^\\d{6}$")
    private val POLICY_NUMBER_REGEX = Regex("(?<!\\d)(\\d{8,10})(?!\\d)")
    private val LISTED_POLICY_REGEX = Regex("^\\d{8,10}\\s*\\|")
    private val NUMBER_LIST_REGEX = Regex("^[\\d,\\s]+$")
    private val SHEET_TITLES = listOf("Email ID(s)", "Address(es)", "Mobile Number(s)")
    private val ELLIPSIS_MARKERS = listOf("…", "...")

    fun CleanNodes(Nodes: List<String>): List<String> {
        return Nodes
            .flatMap { NodeText -> NodeText.split("\n") }
            .map { NodeText -> NodeText.trim() }
            .filter { NodeText -> NodeText.isNotEmpty() }
    }

    fun IsProfilePane(Nodes: List<String>): Boolean {
        val CleanList = CleanNodes(Nodes = Nodes)
        return CleanList.any { NodeText -> NodeText.equals(SECTION_CONTACT, ignoreCase = true) } ||
                CleanList.any { NodeText -> NodeText.equals(SECTION_PERSONAL, ignoreCase = true) }
    }

    fun IsProfilePaneComplete(Nodes: List<String>): Boolean {
        val CleanList = CleanNodes(Nodes = Nodes)
        val HasContact = CleanList.any { NodeText ->
            NodeText.equals(SECTION_CONTACT, ignoreCase = true)
        }
        val HasPersonal = CleanList.any { NodeText ->
            NodeText.equals(SECTION_PERSONAL, ignoreCase = true)
        }
        return HasContact && HasPersonal
    }

    fun IsTruncated(ValueText: String): Boolean {
        val Trimmed = ValueText.trim()
        return ELLIPSIS_MARKERS.any { MarkerText -> Trimmed.endsWith(MarkerText) }
    }

    fun NeedsSheet(Nodes: List<String>, LabelText: String): Boolean {
        val CleanList = CleanNodes(Nodes = Nodes)
        val LabelIndex = CleanList.indexOfFirst { NodeText ->
            NodeText.equals(LabelText, ignoreCase = true)
        }
        if (LabelIndex < 0) return false

        var ScanIndex = LabelIndex + 1
        while (ScanIndex < CleanList.size) {
            val NodeText = CleanList[ScanIndex]
            if (IsFieldLabel(NodeText = NodeText) || IsSectionLabel(NodeText = NodeText)) break
            if (CHIP_REGEX.matches(NodeText)) return true
            if (IsTruncated(ValueText = NodeText)) return true
            ScanIndex++
        }
        return false
    }

    fun ParseProfilePane(Nodes: List<String>, CustomerNameVal: String = ""): CustomerProfile {
        val CleanList = CleanNodes(Nodes = Nodes)
        val FieldMap = PairLabelsWithValues(CleanList = CleanList)

        return CustomerProfile(
            CustomerName = CustomerNameVal,
            Dob = FieldMap[LABEL_DOB].orEmpty(),
            Gender = FieldMap[LABEL_GENDER].orEmpty(),
            Education = FieldMap[LABEL_EDUCATION].orEmpty(),
            Occupation = FieldMap[LABEL_OCCUPATION].orEmpty(),
            MaritalStatus = FieldMap[LABEL_MARITAL].orEmpty(),
            AnnualIncome = FieldMap[LABEL_INCOME].orEmpty(),
            Mobiles = InlineValue(
                CleanList = CleanList,
                FieldMap = FieldMap,
                LabelText = LABEL_MOBILE,
                KindVal = ContactKind.MOBILE
            ),
            Emails = InlineValue(
                CleanList = CleanList,
                FieldMap = FieldMap,
                LabelText = LABEL_EMAIL,
                KindVal = ContactKind.EMAIL
            ),
            Addresses = InlineValue(
                CleanList = CleanList,
                FieldMap = FieldMap,
                LabelText = LABEL_ADDRESS,
                KindVal = ContactKind.ADDRESS
            ),
            PolicyNumbers = emptyList()
        )
    }

    data class ContactSheetRead(
        val Values: List<ContactValue>,
        val RelatedGroupCount: Int,
        val OrphanGroupCount: Int
    )

    fun ParseContactSheet(
        Nodes: List<String>,
        KindVal: ContactKind,
        SelectedIndexVal: Int = -1
    ): List<ContactValue> {
        return ReadContactSheet(
            Nodes = Nodes,
            KindVal = KindVal,
            SelectedIndexVal = SelectedIndexVal
        ).Values
    }

    fun ReadContactSheet(
        Nodes: List<String>,
        KindVal: ContactKind,
        SelectedIndexVal: Int = -1
    ): ContactSheetRead {
        val CleanList = ScopedSheetNodes(CleanList = CleanNodes(Nodes = Nodes))
        val ValueList = mutableListOf<String>()
        val RelatedList = mutableListOf<MutableList<String>>()
        var RelatedGroupCount = 0
        var OrphanGroupCount = 0

        var ScanIndex = 0
        while (ScanIndex < CleanList.size) {
            val NodeText = CleanList[ScanIndex]

            if (NodeText.startsWith(RELATED_PREFIX, ignoreCase = true)) {
                RelatedGroupCount++
                var NumberList = PolicyNumbersIn(NodeText = NodeText.substringAfter(':', ""))
                if (NumberList.isEmpty()) {
                    val NextText = CleanList.getOrNull(ScanIndex + 1).orEmpty()
                    if (NUMBER_LIST_REGEX.matches(NextText)) {
                        NumberList = PolicyNumbersIn(NodeText = NextText)
                        if (NumberList.isNotEmpty()) ScanIndex++
                    }
                }
                if (ValueList.isEmpty()) {
                    OrphanGroupCount++
                } else {
                    RelatedList[RelatedList.size - 1].addAll(NumberList)
                }
                ScanIndex++
                continue
            }

            ScanIndex++
            if (IsSheetNoise(NodeText = NodeText)) continue

            if (KindVal == ContactKind.ADDRESS &&
                PINCODE_REGEX.matches(NodeText) &&
                ValueList.isNotEmpty()
            ) {
                ValueList[ValueList.size - 1] = "${ValueList.last()}, $NodeText".trim()
                continue
            }
            if (!IsValueOfKind(NodeText = NodeText, KindVal = KindVal)) continue
            if (IsTruncated(ValueText = NodeText)) continue

            ValueList.add(NormaliseValue(RawValue = NodeText, KindVal = KindVal))
            RelatedList.add(mutableListOf())
        }

        val DefaultIndex = if (SelectedIndexVal in ValueList.indices) SelectedIndexVal else 0
        val ResultValues = ValueList.mapIndexed { ValueIndex, ValueText ->
            ContactValue(
                Value = ValueText,
                RelatedPolicies = RelatedList[ValueIndex].distinct(),
                IsDefault = ValueIndex == DefaultIndex
            )
        }
        return ContactSheetRead(
            Values = ResultValues,
            RelatedGroupCount = RelatedGroupCount,
            OrphanGroupCount = OrphanGroupCount
        )
    }

    private fun ScopedSheetNodes(CleanList: List<String>): List<String> {
        val TitleIndex = CleanList.indexOfFirst { NodeText ->
            SHEET_TITLES.any { TitleText -> NodeText.trim().equals(TitleText, ignoreCase = true) }
        }
        return if (TitleIndex < 0) CleanList else CleanList.drop(TitleIndex)
    }

    private fun PolicyNumbersIn(NodeText: String): List<String> {
        return POLICY_NUMBER_REGEX
            .findAll(NodeText)
            .map { MatchItem -> MatchItem.groupValues[1] }
            .toList()
    }

    fun ParsePolicyNumbers(Nodes: List<String>): List<String> {
        val ParsedNumbers = ScreenDataParser.ParsePolicyDashboard(Nodes = Nodes)
            .map { PolicyItem -> PolicyItem.PolicyNumber }
            .filter { NumberText -> NumberText.isNotEmpty() }
        if (ParsedNumbers.isNotEmpty()) return ParsedNumbers.distinct()

        return CleanNodes(Nodes = Nodes)
            .filter { NodeText -> LISTED_POLICY_REGEX.containsMatchIn(NodeText) }
            .mapNotNull { NodeText ->
                POLICY_NUMBER_REGEX.find(NodeText)?.groupValues?.get(1)
            }
            .distinct()
    }

    private fun InlineValue(
        CleanList: List<String>,
        FieldMap: Map<String, String>,
        LabelText: String,
        KindVal: ContactKind
    ): List<ContactValue> {
        val ValueText = FieldMap[LabelText].orEmpty()
        if (ValueText.isEmpty()) return emptyList()
        if (!IsTruncated(ValueText = ValueText)) {
            return listOf(
                ContactValue(
                    Value = NormaliseValue(RawValue = ValueText, KindVal = KindVal),
                    IsDefault = true
                )
            )
        }

        val FullValue = FullValueFor(
            CleanList = CleanList,
            PartialText = ValueText,
            KindVal = KindVal
        )
        if (FullValue.isNotEmpty()) {
            return listOf(
                ContactValue(
                    Value = NormaliseValue(RawValue = FullValue, KindVal = KindVal),
                    IsDefault = true
                )
            )
        }
        return listOf(ContactValue(Value = ValueText, IsDefault = true, IsPartial = true))
    }

    fun FullValueFor(
        CleanList: List<String>,
        PartialText: String,
        KindVal: ContactKind
    ): String {
        val VisiblePrefix = PartialText.trim().trimEnd('\u2026', '.').trim()
        if (VisiblePrefix.length < 4) return ""
        return CleanList.firstOrNull { NodeText ->
            !IsTruncated(ValueText = NodeText) &&
                    NodeText.length > PartialText.length &&
                    NodeText.startsWith(VisiblePrefix, ignoreCase = true) &&
                    IsValueOfKind(NodeText = NodeText, KindVal = KindVal)
        }.orEmpty()
    }

    private fun PairLabelsWithValues(CleanList: List<String>): Map<String, String> {
        val ResultMap = mutableMapOf<String, String>()
        var ScanIndex = 0

        while (ScanIndex < CleanList.size) {
            if (IsSectionLabel(NodeText = CleanList[ScanIndex])) {
                ScanIndex++
                continue
            }
            if (!IsFieldLabel(NodeText = CleanList[ScanIndex])) {
                ScanIndex++
                continue
            }

            val LabelRun = mutableListOf<String>()
            while (ScanIndex < CleanList.size) {
                val NodeText = CleanList[ScanIndex]
                if (IsSectionLabel(NodeText = NodeText)) break
                if (!IsFieldLabel(NodeText = NodeText)) break
                LabelRun.add(CanonicalLabel(NodeText = NodeText))
                ScanIndex++
            }

            val ValueRun = mutableListOf<String>()
            while (ScanIndex < CleanList.size && ValueRun.size < LabelRun.size) {
                val NodeText = CleanList[ScanIndex]
                if (IsFieldLabel(NodeText = NodeText) || IsSectionLabel(NodeText = NodeText)) break
                ScanIndex++
                if (NodeText.equals(LINK_VIEW_ALL, ignoreCase = true)) continue
                if (CHIP_REGEX.matches(NodeText)) continue
                ValueRun.add(if (IsNoiseValue(NodeText = NodeText)) "" else NodeText)
            }

            for (PairIndex in LabelRun.indices) {
                val LabelText = LabelRun[PairIndex]
                val ValueText = ValueRun.getOrNull(PairIndex).orEmpty()
                if (ValueText.isEmpty()) continue
                if (ResultMap.containsKey(LabelText)) continue
                ResultMap[LabelText] = ValueText
            }
        }
        return ResultMap
    }

    private fun IsValueOfKind(NodeText: String, KindVal: ContactKind): Boolean {
        return when (KindVal) {
            ContactKind.MOBILE -> MOBILE_REGEX.matches(DigitsOf(RawValue = NodeText))
            ContactKind.EMAIL -> NodeText.contains('@') && NodeText.substringAfter('@').contains('.')
            ContactKind.ADDRESS -> NodeText.length >= 8 &&
                    (NodeText.contains(',') || PINCODE_REGEX.containsMatchIn(NodeText))
        }
    }

    private fun NormaliseValue(RawValue: String, KindVal: ContactKind): String {
        return when (KindVal) {
            ContactKind.MOBILE -> DigitsOf(RawValue = RawValue)
            ContactKind.EMAIL -> RawValue.trim()
            ContactKind.ADDRESS -> RawValue.trim().trimEnd(',').trim()
        }
    }

    private fun DigitsOf(RawValue: String): String {
        val DigitText = RawValue.filter { CharValue -> CharValue.isDigit() }
        return if (DigitText.length > 10) DigitText.takeLast(10) else DigitText
    }

    private fun IsSheetNoise(NodeText: String): Boolean {
        if (NodeText.startsWith("Mark as default", ignoreCase = true)) return true
        if (NodeText.endsWith("(s)")) return true
        if (NodeText.equals("Address(es)", ignoreCase = true)) return true
        return IsNoiseValue(NodeText = NodeText)
    }

    private fun IsNoiseValue(NodeText: String): Boolean {
        return NOISE_VALUES.any { NoiseText -> NodeText.equals(NoiseText, ignoreCase = true) }
    }

    private fun IsFieldLabel(NodeText: String): Boolean {
        return FIELD_LABELS.any { LabelText -> NodeText.equals(LabelText, ignoreCase = true) }
    }

    private fun IsSectionLabel(NodeText: String): Boolean {
        if (IsSheetTitle(NodeText = NodeText)) return true
        return SECTION_LABELS.any { LabelText -> NodeText.equals(LabelText, ignoreCase = true) }
    }

    private fun IsSheetTitle(NodeText: String): Boolean {
        val Trimmed = NodeText.trim()
        if (SHEET_TITLES.any { TitleText -> Trimmed.equals(TitleText, ignoreCase = true) }) {
            return true
        }
        return Trimmed.endsWith("(s)") || Trimmed.endsWith("(es)")
    }

    private fun CanonicalLabel(NodeText: String): String {
        return FIELD_LABELS.first { LabelText -> NodeText.equals(LabelText, ignoreCase = true) }
    }
}
