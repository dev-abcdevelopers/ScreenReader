@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName")

package com.bliss.screenreader.utils

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import com.bliss.screenreader.R

object Skeleton {
    private const val PULSE_MILLIS = 900L
    private const val FADE_MILLIS = 150L

    fun Show(SkeletonView: View) {
        if (SkeletonView.visibility == View.VISIBLE) return
        SkeletonView.alpha = 1f
        SkeletonView.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        SkeletonView.contentDescription = SkeletonView.context.getString(R.string.loading_announce)
        SkeletonView.visibility = View.VISIBLE
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val PulseRef = ObjectAnimator.ofFloat(SkeletonView, View.ALPHA, 1f, 0.45f).apply {
            duration = PULSE_MILLIS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        SkeletonView.setTag(R.id.skeletonRoot, PulseRef)
        PulseRef.start()
    }

    fun Hide(SkeletonView: View, ContentView: View? = null) {
        (SkeletonView.getTag(R.id.skeletonRoot) as? ValueAnimator)?.cancel()
        SkeletonView.setTag(R.id.skeletonRoot, null)
        val WasShowing = SkeletonView.visibility == View.VISIBLE
        SkeletonView.visibility = View.GONE
        SkeletonView.alpha = 1f
        if (!WasShowing || ContentView == null || ContentView.visibility != View.VISIBLE) return
        if (!ValueAnimator.areAnimatorsEnabled()) return
        ContentView.alpha = 0f
        ContentView.animate().alpha(1f).setDuration(FADE_MILLIS).start()
    }
}
