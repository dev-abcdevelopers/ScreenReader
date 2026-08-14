@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.data.parser

import com.bliss.screenreader.data.model.CustomerPolicy
import java.util.regex.Pattern

object ScreenDataParser {

    private val DATE_REGEX = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$")
    private val POLICY_NUMBER_REGEX = Regex("(?<!\\d)(\\d{8,10})(?!\\d)")
    private val DISPLAY_DATE_REGEX = Regex(
        "^\\d{1,2}\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+\\d{4}$",
        RegexOption.IGNORE_CASE
    )
    private val PLAN_CODE_REGEX = Regex("^(\\d{1,4})\\s*[-–]")
    private val HOLDER_NAME_REGEX = Regex("^[\\p{L}.']+(?:\\s+[\\p{L}.']+){1,4}$")

    private val ICON_WORDS = setOf(
        "icon", "arrow", "chevron", "image", "svg", "vector", "logo", "banner",
        "button", "card", "star", "badge", "avatar", "placeholder", "graphic",
        "indicator", "checkbox", "radio", "toggle", "spinner", "loader"
    )

    private val NON_HOLDER_PHRASES = setOf(
        "call customer", "send reminder", "view all", "filter sort", "add favourite",
        "remove favourite", "share greetings", "share posters", "based on selected filters",
        "customer portfolio", "customer dashboard", "detailed customer view",
        "detailed policy view", "policy dashboard", "your portfolio", "contact details",
        "personal details", "total sum assured", "annualized premium", "relationship with customer"
    )

    private val POLICY_LABELS = setOf(
        "auto pay", "premium amount", "premium amount (excl. gst)", "send reminder",
        "filter & sort", "all date ranges", "special revival campaign eligible",
        "based on selected filters", "policy dashboard", "page", "policies"
    )

    fun IsValidDate(InputStr: String): Boolean {
        return DATE_REGEX.matcher(InputStr.trim()).matches()
    }

    fun ParsePolicyDashboard(Nodes: List<String>): List<CustomerPolicy> {
        val CleanNodes = Nodes
            .flatMap { NodeText -> NodeText.split("\n") }
            .map { NodeText -> NodeText.trim() }
            .filter { NodeText -> NodeText.isNotEmpty() }

        val PolicyMap = linkedMapOf<String, CustomerPolicy>()
        var NodeIdx = 0
        while (NodeIdx < CleanNodes.size) {
            val AnchorText = CleanNodes[NodeIdx]
            val NumberMatch = POLICY_NUMBER_REGEX.find(AnchorText)
            if (NumberMatch == null) {
                NodeIdx++
                continue
            }

            val PolicyNumber = NumberMatch.groupValues[1]
            var PlanName = AnchorText.substring(NumberMatch.range.last + 1)
                .trim()
                .trimStart('|', 'I', 'l', '!', ':', '-', '–')
                .trim()
            var HolderName = ""
            var Status = ""
            var AutoPay = ""
            var RenewalDate = ""
            var RenewalType = ""
            var PremiumAmount = ""
            var PremiumFrequency = ""
            var KycStatus = ""
            var NeftStatus = ""
            var NomineeStatus = ""
            var MobileUpdateStatus = ""
            var AddressUpdateStatus = ""

            for (BackIdx in NodeIdx - 1 downTo maxOf(0, NodeIdx - 10)) {
                val PreviousText = CleanNodes[BackIdx]
                if (POLICY_NUMBER_REGEX.containsMatchIn(PreviousText) ||
                    PreviousText.startsWith("Send Reminder", ignoreCase = true)
                ) {
                    break
                }
                if (Status.isEmpty() && IsPolicyStatus(PreviousText)) Status = PreviousText
                if (RenewalType.isEmpty() && IsRenewalType(PreviousText)) RenewalType = PreviousText

                val PreviousLower = PreviousText.lowercase()
                if (PreviousLower.contains("kyc not updated")) KycStatus = "Not Updated"
                if (PreviousLower.contains("neft not updated")) NeftStatus = "Not Updated"
                if (PreviousLower.contains("nominee not updated")) NomineeStatus = "Not Updated"
                if (PreviousLower.contains("mobile not updated")) MobileUpdateStatus = "Not Updated"
                if (PreviousLower.contains("address not updated")) AddressUpdateStatus = "Not Updated"
            }

            var ForwardIdx = NodeIdx + 1
            while (ForwardIdx < CleanNodes.size && ForwardIdx < NodeIdx + 22) {
                val CurrentText = CleanNodes[ForwardIdx]
                val CurrentLower = CurrentText.lowercase()
                if (POLICY_NUMBER_REGEX.containsMatchIn(CurrentText)) break

                if (CurrentText.startsWith("Send Reminder", ignoreCase = true)) {
                    ForwardIdx++
                    break
                }

                if (CurrentLower.contains("kyc not updated")) KycStatus = "Not Updated"
                if (CurrentLower.contains("neft not updated")) NeftStatus = "Not Updated"
                if (CurrentLower.contains("nominee not updated")) NomineeStatus = "Not Updated"
                if (CurrentLower.contains("mobile not updated")) MobileUpdateStatus = "Not Updated"
                if (CurrentLower.contains("address not updated")) AddressUpdateStatus = "Not Updated"

                if (Status.isEmpty() && IsPolicyStatus(CurrentText)) {
                    Status = CurrentText
                    ForwardIdx++
                    continue
                }
                if (RenewalType.isEmpty() && IsRenewalType(CurrentText)) {
                    RenewalType = CurrentText
                    ForwardIdx++
                    continue
                }
                if (PlanName.isEmpty() &&
                    (CurrentText.contains("LIC", ignoreCase = true) || PLAN_CODE_REGEX.containsMatchIn(CurrentText))
                ) {
                    PlanName = CurrentText
                    ForwardIdx++
                    continue
                }
                if (HolderName.isEmpty() && LooksLikePolicyHolder(CurrentText)) {
                    HolderName = CurrentText
                    ForwardIdx++
                    continue
                }
                if (CurrentText.equals("Enabled", ignoreCase = true) ||
                    CurrentText.equals("Disabled", ignoreCase = true)
                ) {
                    AutoPay = CurrentText
                    ForwardIdx++
                    continue
                }
                if (CurrentLower.contains("revival without dgh") ||
                    CurrentLower.contains("revival expiry") ||
                    CurrentLower.contains("renewal due") ||
                    CurrentLower == "fup"
                ) {
                    RenewalType = CurrentText
                    ForwardIdx++
                    continue
                }
                if (DISPLAY_DATE_REGEX.matches(CurrentText) || IsValidDate(InputStr = CurrentText)) {
                    RenewalDate = CurrentText
                    ForwardIdx++
                    continue
                }
                if (CurrentText.startsWith("₹") ||
                    (CurrentText.any { CharacterVal -> CharacterVal.isDigit() } &&
                            listOf("/Month", "/Year", "/Quarter", "/Half").any {
                                FrequencyText -> CurrentText.contains(FrequencyText, ignoreCase = true)
                            })
                ) {
                    val PremiumParts = CurrentText.split("/", limit = 2)
                    PremiumAmount = PremiumParts.first().trim()
                    if (PremiumParts.size > 1) PremiumFrequency = PremiumParts[1].trim()
                }
                ForwardIdx++
            }

            val (PlanCode, PlanNameOnly) = PlanIdentity.Split(RawLabel = PlanName)
            val IncomingPolicy = CustomerPolicy(
                HolderName = HolderName,
                PolicyNumber = PolicyNumber,
                PlanName = PlanNameOnly,
                PlanCode = PlanCode,
                RenewalDueDate = RenewalDate,
                PremiumAmount = PremiumAmount,
                PremiumFrequency = PremiumFrequency,
                AutoPay = AutoPay,
                Status = Status,
                NomineeStatus = NomineeStatus,
                MobileUpdateStatus = MobileUpdateStatus,
                AddressUpdateStatus = AddressUpdateStatus,
                KycStatus = KycStatus,
                NeftStatus = NeftStatus,
                RenewalType = RenewalType
            )
            val ExistingPolicy = PolicyMap[PolicyNumber]
            PolicyMap[PolicyNumber] = if (ExistingPolicy == null) {
                IncomingPolicy
            } else {
                MergePolicyDashboardRecord(ExistingPolicy = ExistingPolicy, IncomingPolicy = IncomingPolicy)
            }
            NodeIdx = maxOf(NodeIdx + 1, ForwardIdx)
        }
        return PolicyMap.values.toList()
    }

    fun MergePolicyDashboardRecord(
        ExistingPolicy: CustomerPolicy,
        IncomingPolicy: CustomerPolicy
    ): CustomerPolicy {
        return ExistingPolicy.copy(
            HolderName = IncomingPolicy.HolderName.ifEmpty { ExistingPolicy.HolderName },
            PlanName = IncomingPolicy.PlanName.ifEmpty { ExistingPolicy.PlanName },
            PlanCode = IncomingPolicy.PlanCode.ifEmpty { ExistingPolicy.PlanCode },
            RenewalDueDate = IncomingPolicy.RenewalDueDate.ifEmpty { ExistingPolicy.RenewalDueDate },
            PremiumAmount = IncomingPolicy.PremiumAmount.ifEmpty { ExistingPolicy.PremiumAmount },
            PremiumFrequency = IncomingPolicy.PremiumFrequency.ifEmpty { ExistingPolicy.PremiumFrequency },
            AutoPay = IncomingPolicy.AutoPay.ifEmpty { ExistingPolicy.AutoPay },
            Status = IncomingPolicy.Status.ifEmpty { ExistingPolicy.Status },
            NomineeStatus = IncomingPolicy.NomineeStatus.ifEmpty { ExistingPolicy.NomineeStatus },
            MobileUpdateStatus = IncomingPolicy.MobileUpdateStatus.ifEmpty { ExistingPolicy.MobileUpdateStatus },
            AddressUpdateStatus = IncomingPolicy.AddressUpdateStatus.ifEmpty { ExistingPolicy.AddressUpdateStatus },
            KycStatus = IncomingPolicy.KycStatus.ifEmpty { ExistingPolicy.KycStatus },
            NeftStatus = IncomingPolicy.NeftStatus.ifEmpty { ExistingPolicy.NeftStatus },
            RenewalType = IncomingPolicy.RenewalType.ifEmpty { ExistingPolicy.RenewalType },
            SumAssured = IncomingPolicy.SumAssured.ifEmpty { ExistingPolicy.SumAssured },
            TermPPT = IncomingPolicy.TermPPT.ifEmpty { ExistingPolicy.TermPPT },
            DateOfCommencement = IncomingPolicy.DateOfCommencement.ifEmpty {
                ExistingPolicy.DateOfCommencement
            },
            EndOfPremiumPayingTerm = IncomingPolicy.EndOfPremiumPayingTerm.ifEmpty {
                ExistingPolicy.EndOfPremiumPayingTerm
            },
            DateOfMaturity = IncomingPolicy.DateOfMaturity.ifEmpty { ExistingPolicy.DateOfMaturity },
            MobileNumber = IncomingPolicy.MobileNumber.ifEmpty { ExistingPolicy.MobileNumber },
            Dob = IncomingPolicy.Dob.ifEmpty { ExistingPolicy.Dob },
            Address = IncomingPolicy.Address.ifEmpty { ExistingPolicy.Address },
            Email = IncomingPolicy.Email.ifEmpty { ExistingPolicy.Email },
            Gender = IncomingPolicy.Gender.ifEmpty { ExistingPolicy.Gender },
            Education = IncomingPolicy.Education.ifEmpty { ExistingPolicy.Education },
            Occupation = IncomingPolicy.Occupation.ifEmpty { ExistingPolicy.Occupation },
            MaritalStatus = IncomingPolicy.MaritalStatus.ifEmpty {
                ExistingPolicy.MaritalStatus
            },
            AnnualIncome = IncomingPolicy.AnnualIncome.ifEmpty { ExistingPolicy.AnnualIncome },
            CommissionDateOfPremiumPayment = IncomingPolicy.CommissionDateOfPremiumPayment.ifEmpty {
                ExistingPolicy.CommissionDateOfPremiumPayment
            },
            CommissionDateOfPayment = IncomingPolicy.CommissionDateOfPayment.ifEmpty {
                ExistingPolicy.CommissionDateOfPayment
            },
            CommissionType = IncomingPolicy.CommissionType.ifEmpty { ExistingPolicy.CommissionType },
            BonusCommission = IncomingPolicy.BonusCommission.ifEmpty { ExistingPolicy.BonusCommission },
            CommissionPaidAmount = IncomingPolicy.CommissionPaidAmount.ifEmpty {
                ExistingPolicy.CommissionPaidAmount
            }
        )
    }

    private fun IsPolicyStatus(TextValue: String): Boolean {
        val LowerValue = TextValue.lowercase()
        return listOf(
            "lapsed", "inforce", "in force", "grace expiring", "renewal due",
            "premium due", "expired", "matured", "claim", "claimed"
        ).any { StatusText -> LowerValue.contains(StatusText) }
    }

    private fun IsRenewalType(TextValue: String): Boolean {
        val LowerValue = TextValue.lowercase()
        return listOf(
            "first year renewal", "1st year renewal", "second year renewal",
            "2nd year renewal", "third year renewal", "3rd year renewal",
            "regular renewal", "revival"
        ).any { TypeText -> LowerValue.contains(TypeText) }
    }

    private fun LooksLikePolicyHolder(TextValue: String): Boolean {
        val TrimmedValue = TextValue.trim()
        val LowerValue = TrimmedValue.lowercase()
        if (!HOLDER_NAME_REGEX.matches(TrimmedValue)) return false
        if (POLICY_LABELS.any { LabelText -> LowerValue.startsWith(LabelText) }) return false
        if (IsPolicyStatus(TextValue = TrimmedValue) || IsRenewalType(TextValue = TrimmedValue)) return false
        if (TrimmedValue.contains("LIC", ignoreCase = true)) return false
        if (LowerValue.contains("not updated")) return false
        if (LooksLikeIconDescription(LowerValue = LowerValue)) return false
        if (NON_HOLDER_PHRASES.any { PhraseText -> LowerValue == PhraseText }) return false
        return true
    }

    private fun LooksLikeIconDescription(LowerValue: String): Boolean {
        val WordList = LowerValue.split(Regex("\\s+")).filter { WordText -> WordText.isNotEmpty() }
        if (WordList.isEmpty()) return false
        return WordList.any { WordText -> ICON_WORDS.contains(WordText) }
    }

    fun ParseDetailedPolicyView(Nodes: List<String>): Map<String, String> {
        val ResultMap = mutableMapOf<String, String>()
        val CleanNodes = Nodes.map { it.trim() }.filter { it.isNotEmpty() }

        for (Index in CleanNodes.indices) {
            val TextValue = CleanNodes[Index]
            when {
                TextValue.equals("Sum Assured", ignoreCase = true) -> {
                    ResultMap["sumAssured"] = ReadFollowingValue(CleanNodes, Index)
                }
                Regex("^Term\\s*/\\s*PPT$", RegexOption.IGNORE_CASE).matches(TextValue) -> {
                    ResultMap["termPPT"] = ReadFollowingValue(CleanNodes, Index)
                }
                TextValue.equals("Date of Commencement", ignoreCase = true) -> {
                    ResultMap["dateOfCommencement"] = ReadFollowingValue(CleanNodes, Index)
                }
                TextValue.equals("End of Premium Paying Term", ignoreCase = true) -> {
                    ResultMap["endOfPremiumPayingTerm"] = ReadFollowingValue(CleanNodes, Index)
                }
                TextValue.equals("Date of Maturity", ignoreCase = true) -> {
                    ResultMap["dateOfMaturity"] = ReadFollowingValue(CleanNodes, Index)
                }
                TextValue.equals("Auto Pay", ignoreCase = true) -> {
                    ResultMap["autoPay"] = ReadFollowingValue(CleanNodes, Index)
                }
                TextValue.equals("Date of Premium Payment", ignoreCase = true) -> {
                    ResultMap["commissionDateOfPremiumPayment"] = ReadFollowingValue(CleanNodes, Index)
                }
                TextValue.equals("Date of Commission Payment", ignoreCase = true) -> {
                    ResultMap["commissionDateOfPayment"] = ReadFollowingValue(CleanNodes, Index)
                }
                TextValue.equals("Commission Type", ignoreCase = true) -> {
                    ResultMap["commissionType"] = ReadFollowingValue(CleanNodes, Index)
                }
                TextValue.equals("Bonus Commission", ignoreCase = true) -> {
                    ResultMap["bonusCommission"] = ReadFollowingValue(CleanNodes, Index)
                }
            }
        }
        val CommissionPaidIndex = CleanNodes.indexOfFirst { NodeText ->
            NodeText.equals("COMMISSION PAID", ignoreCase = true)
        }
        if (CommissionPaidIndex > 0) {
            ResultMap["commissionPaidAmount"] = CleanNodes
                .subList(maxOf(0, CommissionPaidIndex - 3), CommissionPaidIndex)
                .lastOrNull { NodeText -> NodeText.contains("₹") }
                .orEmpty()
        }
        return ResultMap
    }

    fun ParseDetailedPolicyRecord(Nodes: List<String>): CustomerPolicy? {
        val CleanNodes = Nodes
            .flatMap { NodeText -> NodeText.split("\n") }
            .map { NodeText -> NodeText.trim() }
            .filter { NodeText -> NodeText.isNotEmpty() }
        val PolicyIndex = CleanNodes.indexOfFirst { NodeText ->
            POLICY_NUMBER_REGEX.containsMatchIn(NodeText)
        }
        if (PolicyIndex < 0) return null

        val PolicyNumber = POLICY_NUMBER_REGEX.find(CleanNodes[PolicyIndex])
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        if (PolicyNumber.isEmpty()) return null

        var HolderName = ""
        var PlanName = ""
        for (NodeIndex in PolicyIndex + 1 until minOf(CleanNodes.size, PolicyIndex + 8)) {
            val NodeText = CleanNodes[NodeIndex]
            if (HolderName.isEmpty() && LooksLikePolicyHolder(NodeText) &&
                !NodeText.contains("Detailed Policy View", ignoreCase = true)
            ) {
                HolderName = NodeText
                continue
            }
            if (PlanName.isEmpty() &&
                (NodeText.contains("LIC", ignoreCase = true) ||
                        PLAN_CODE_REGEX.containsMatchIn(NodeText))
            ) {
                PlanName = NodeText
            }
        }

        val DetailsMap = ParseDetailedPolicyView(Nodes = CleanNodes)
        val PremiumLabelIndex = CleanNodes.indexOfFirst { NodeText ->
            NodeText.contains("Premium Amount", ignoreCase = true)
        }
        val PremiumText = if (PremiumLabelIndex >= 0) {
            ReadFollowingValue(CleanNodes, PremiumLabelIndex)
        } else {
            ""
        }
        val PremiumParts = PremiumText.split("/", limit = 2)
        val Status = CleanNodes.firstOrNull { NodeText -> IsPolicyStatus(NodeText) }.orEmpty()

        return CustomerPolicy(
            HolderName = HolderName,
            PolicyNumber = PolicyNumber,
            PlanName = PlanIdentity.Name(RawLabel = PlanName),
            PlanCode = PlanIdentity.Code(RawLabel = PlanName),
            PremiumAmount = PremiumParts.firstOrNull().orEmpty(),
            PremiumFrequency = PremiumParts.getOrNull(1).orEmpty(),
            AutoPay = DetailsMap["autoPay"].orEmpty(),
            Status = Status,
            SumAssured = DetailsMap["sumAssured"].orEmpty(),
            TermPPT = DetailsMap["termPPT"].orEmpty(),
            DateOfCommencement = DetailsMap["dateOfCommencement"].orEmpty(),
            EndOfPremiumPayingTerm = DetailsMap["endOfPremiumPayingTerm"].orEmpty(),
            DateOfMaturity = DetailsMap["dateOfMaturity"].orEmpty(),
            KycStatus = if (CleanNodes.any { it.contains("KYC not updated", true) }) {
                "Not Updated"
            } else {
                ""
            },
            NeftStatus = if (CleanNodes.any { it.contains("NEFT not updated", true) }) {
                "Not Updated"
            } else {
                ""
            },
            CommissionDateOfPremiumPayment = DetailsMap["commissionDateOfPremiumPayment"].orEmpty(),
            CommissionDateOfPayment = DetailsMap["commissionDateOfPayment"].orEmpty(),
            CommissionType = DetailsMap["commissionType"].orEmpty(),
            BonusCommission = DetailsMap["bonusCommission"].orEmpty(),
            CommissionPaidAmount = DetailsMap["commissionPaidAmount"].orEmpty()
        )
    }

    private fun ReadFollowingValue(CleanNodes: List<String>, LabelIndex: Int): String {
        if (LabelIndex < 0 || LabelIndex + 1 >= CleanNodes.size) return ""
        val FirstValue = CleanNodes[LabelIndex + 1].trim()
        if (FirstValue == "₹" && LabelIndex + 2 < CleanNodes.size) {
            return "₹${CleanNodes[LabelIndex + 2].trim()}"
        }
        return FirstValue.takeUnless { ValueText -> ValueText == "-" }.orEmpty()
    }

    fun ParseCustomerProfile(Nodes: List<String>): Map<String, String> {
        val ResultMap = mutableMapOf<String, String>()
        val CleanNodes = Nodes.map { it.trim() }.filter { it.isNotEmpty() }

        val PhonePattern = Pattern.compile("^[6-9]\\d{9}$")
        for (Index in CleanNodes.indices) {
            val TextValue = CleanNodes[Index]
            if (PhonePattern.matcher(TextValue).matches()) {
                ResultMap["mobileNumber"] = TextValue
            } else if (IsValidDate(InputStr = TextValue) && !ResultMap.containsKey("dob")) {
                ResultMap["dob"] = TextValue
            }
        }
        return ResultMap
    }
}
