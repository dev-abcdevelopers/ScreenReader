@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.ui.adapter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bliss.screenreader.R
import com.bliss.screenreader.data.model.ParsedRecord
import com.bliss.screenreader.databinding.ItemReviewRecordBinding
import java.util.Locale

class ReviewRecordAdapter(
    private var RecordList: List<ParsedRecord> = emptyList()
) : RecyclerView.Adapter<ReviewRecordAdapter.RecordViewHolder>() {

    class RecordViewHolder(val BindingRef: ItemReviewRecordBinding) :
        RecyclerView.ViewHolder(BindingRef.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val BindingObj = ItemReviewRecordBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecordViewHolder(BindingRef = BindingObj)
    }

    override fun getItemCount(): Int = RecordList.size

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val RecordItem = RecordList[position]
        val ContextRef = holder.BindingRef.root.context

        holder.BindingRef.tvRecordPrimary.text = RecordItem.PrimaryLine
        holder.BindingRef.tvRecordSecondary.text = RecordItem.DetailLine
        holder.BindingRef.tvRecordSecondary.visibility =
            if (RecordItem.DetailLine.isEmpty()) View.GONE else View.VISIBLE
        holder.BindingRef.tvRecordNumber.text = NumberLine(RecordItem = RecordItem)
        holder.BindingRef.tvRecordAvatar.text = Initials(NameText = RecordItem.PrimaryLine)

        holder.BindingRef.tvRecordAmount.text = RecordItem.AmountText
        holder.BindingRef.tvRecordAmountLabel.text = RecordItem.AmountLabel
        holder.BindingRef.tvRecordAmountLabel.visibility =
            if (RecordItem.AmountLabel.isEmpty()) View.GONE else View.VISIBLE
        holder.BindingRef.recordAmountBox.visibility =
            if (RecordItem.AmountText.isEmpty()) View.GONE else View.VISIBLE

        if (RecordItem.HasWarning) {
            holder.BindingRef.recordContainer.setBackgroundResource(R.drawable.bg_review_card_warning)
            holder.BindingRef.tvRecordWarning.text = RecordItem.Warning
            holder.BindingRef.tvRecordWarning.visibility = View.VISIBLE
            TintAvatar(
                HolderRef = holder,
                BackgroundColorId = R.color.surface_light,
                TextColorId = R.color.status_amber_text
            )
            holder.BindingRef.tvRecordAmount.setTextColor(
                ContextCompat.getColor(ContextRef, R.color.status_amber_text)
            )
        } else {
            holder.BindingRef.recordContainer.setBackgroundResource(R.drawable.bg_review_card)
            holder.BindingRef.tvRecordWarning.visibility = View.GONE
            val ToneIndex = AvatarTone(RecordItem = RecordItem)
            TintAvatar(
                HolderRef = holder,
                BackgroundColorId = AVATAR_BACKGROUNDS[ToneIndex],
                TextColorId = AVATAR_TEXTS[ToneIndex]
            )
            holder.BindingRef.tvRecordAmount.setTextColor(
                ContextCompat.getColor(ContextRef, R.color.text_primary)
            )
        }
    }

    private fun TintAvatar(
        HolderRef: RecordViewHolder,
        @ColorRes BackgroundColorId: Int,
        @ColorRes TextColorId: Int
    ) {
        val ContextRef = HolderRef.BindingRef.root.context
        HolderRef.BindingRef.tvRecordAvatar.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(ContextRef, BackgroundColorId)
        )
        HolderRef.BindingRef.tvRecordAvatar.setTextColor(
            ContextCompat.getColor(ContextRef, TextColorId)
        )
    }

    private fun AvatarTone(RecordItem: ParsedRecord): Int {
        val SeedText = RecordItem.PrimaryLine.ifEmpty { RecordItem.PolicyNumber }
        if (SeedText.isEmpty()) return 0
        val SeedValue = SeedText.sumOf { CharItem -> CharItem.code }
        return SeedValue % AVATAR_BACKGROUNDS.size
    }

    private fun NumberLine(RecordItem: ParsedRecord): String {
        val NumberText = RecordItem.PolicyNumber.ifEmpty { "—" }
        if (RecordItem.DueText.isEmpty()) return NumberText
        return "$NumberText · Due ${RecordItem.DueText}"
    }

    private fun Initials(NameText: String): String {
        val WordList = NameText.trim().split(" ").filter { WordItem ->
            WordItem.isNotEmpty() && WordItem.first().isLetter()
        }
        if (WordList.isEmpty()) return "—"
        val FirstChar = WordList.first().first()
        val SecondChar = WordList.getOrNull(1)?.first()
        return if (SecondChar == null) {
            FirstChar.uppercase(Locale.getDefault())
        } else {
            "$FirstChar$SecondChar".uppercase(Locale.getDefault())
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun UpdateData(NewRecords: List<ParsedRecord>) {
        RecordList = NewRecords
        notifyDataSetChanged()
    }

    private companion object {
        val AVATAR_BACKGROUNDS = intArrayOf(
            R.color.status_green_bg,
            R.color.status_blue_bg,
            R.color.primary_container
        )

        val AVATAR_TEXTS = intArrayOf(
            R.color.status_green_text,
            R.color.status_blue_text,
            R.color.on_primary_container
        )
    }
}
