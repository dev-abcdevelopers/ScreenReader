@file:Suppress("FunctionName", "PrivatePropertyName", "LocalVariableName", "PropertyName", "unused",
    "SpellCheckingInspection"
)

package com.bliss.screenreader.ui.toast

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import com.bliss.screenreader.R
import com.bliss.screenreader.databinding.ViewAppToastBinding
import com.bliss.screenreader.utils.HapticFeedback

object AppToast {

    enum class Kind { Success, Error, Warning, Info }

    private const val SHORT_MS = 2400L
    private const val LONG_MS = 4200L
    private const val ENTER_MS = 340L
    private const val EXIT_MS = 220L
    private const val VIEW_TAG = "app_toast_card"

    private val MainHandler = Handler(Looper.getMainLooper())

    private var PendingDismiss: Runnable? = null
    private var OverlayRootView: View? = null
    private var OverlayWindowMgr: WindowManager? = null
    private var OverlayDismiss: Runnable? = null

    fun Success(ContextRef: Context?, MessageText: CharSequence, LongDuration: Boolean = false) =
        Show(
            ContextRef = ContextRef,
            MessageText = MessageText,
            KindVal = Kind.Success,
            LongDuration = LongDuration
        )

    fun Error(ContextRef: Context?, MessageText: CharSequence, LongDuration: Boolean = true) =
        Show(
            ContextRef = ContextRef,
            MessageText = MessageText,
            KindVal = Kind.Error,
            LongDuration = LongDuration
        )

    fun Warning(ContextRef: Context?, MessageText: CharSequence, LongDuration: Boolean = true) =
        Show(
            ContextRef = ContextRef,
            MessageText = MessageText,
            KindVal = Kind.Warning,
            LongDuration = LongDuration
        )

    fun Info(ContextRef: Context?, MessageText: CharSequence, LongDuration: Boolean = false) =
        Show(
            ContextRef = ContextRef,
            MessageText = MessageText,
            KindVal = Kind.Info,
            LongDuration = LongDuration
        )

    fun Show(
        ContextRef: Context?,
        MessageText: CharSequence,
        KindVal: Kind = Kind.Info,
        LongDuration: Boolean = false,
        TitleText: CharSequence = ""
    ) {
        if (MessageText.isBlank()) return
        val HostContext = ContextRef ?: return
        MainHandler.post {
            ShowOnMain(
                HostContext = HostContext,
                MessageText = MessageText,
                KindVal = KindVal,
                LongDuration = LongDuration,
                TitleText = TitleText
            )
        }
    }

    private fun ShowOnMain(
        HostContext: Context,
        MessageText: CharSequence,
        KindVal: Kind,
        LongDuration: Boolean,
        TitleText: CharSequence
    ) {
        val ActivityRef = ActivityOf(ContextRef = HostContext)
        if (ActivityRef == null || ActivityRef.isFinishing || ActivityRef.isDestroyed) {
            ShowSystemFallback(
                ContextRef = HostContext,
                MessageText = MessageText,
                KindVal = KindVal,
                LongDuration = LongDuration
            )
            return
        }

        val ContentRoot = ActivityRef.findViewById<ViewGroup>(android.R.id.content) ?: return
        ClearPending(ParentRef = ContentRoot)

        val BindingObj = try {
            ViewAppToastBinding.inflate(LayoutInflater.from(ActivityRef))
        } catch (_: Exception) {
            ShowSystemFallback(
                ContextRef = HostContext,
                MessageText = MessageText,
                KindVal = KindVal,
                LongDuration = LongDuration
            )
            return
        }

        Paint(
            BindingObj = BindingObj,
            KindVal = KindVal,
            MessageText = MessageText,
            TitleText = TitleText
        )

        val RootView = BindingObj.root
        RootView.tag = VIEW_TAG
        val LayoutParamsObj = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        )
        LayoutParamsObj.topMargin = TopInsetOf(HostView = ContentRoot)
        ContentRoot.addView(RootView, LayoutParamsObj)

        val DismissRunnable = Runnable {
            PendingDismiss = null
            if (RootView.parent == null) return@Runnable
            SlideOut(CardRef = BindingObj.toastCard) {
                ContentRoot.removeView(RootView)
            }
        }
        PendingDismiss = DismissRunnable

        BindingObj.toastCard.setOnClickListener {
            MainHandler.removeCallbacks(DismissRunnable)
            DismissRunnable.run()
        }

        SlideIn(CardRef = BindingObj.toastCard)
        HapticFor(ContextRef = HostContext, KindVal = KindVal)
        MainHandler.postDelayed(DismissRunnable, if (LongDuration) LONG_MS else SHORT_MS)
    }

    fun ShowOverlay(
        ContextRef: Context,
        WindowMgrRef: WindowManager?,
        MessageText: CharSequence,
        KindVal: Kind = Kind.Info,
        LongDuration: Boolean = true
    ) {
        if (MessageText.isBlank()) return
        val WindowMgr = WindowMgrRef ?: run {
            ShowSystemFallback(
                ContextRef = ContextRef,
                MessageText = MessageText,
                KindVal = KindVal,
                LongDuration = LongDuration
            )
            return
        }

        MainHandler.post {
            RemoveOverlay()
            try {
                val ThemedContext = OverlayContextOf(ContextRef = ContextRef)
                val BindingObj = ViewAppToastBinding.inflate(LayoutInflater.from(ThemedContext))
                Paint(
                    BindingObj = BindingObj,
                    KindVal = KindVal,
                    MessageText = MessageText,
                    TitleText = ""
                )

                val LayoutParamsObj = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    y = ContextRef.resources.getDimensionPixelSize(R.dimen.toast_top_inset) +
                            StatusBarHeightOf(ContextRef = ContextRef)
                }

                val RootView = BindingObj.root
                BindingObj.toastCard.isClickable = false
                BindingObj.toastCard.isFocusable = false
                WindowMgr.addView(RootView, LayoutParamsObj)
                OverlayRootView = RootView
                OverlayWindowMgr = WindowMgr

                val DismissRunnable = Runnable {
                    if (OverlayRootView !== RootView) return@Runnable
                    SlideOut(CardRef = BindingObj.toastCard) { RemoveOverlay() }
                }
                OverlayDismiss = DismissRunnable
                MainHandler.postDelayed(
                    DismissRunnable,
                    if (LongDuration) LONG_MS else SHORT_MS
                )

                SlideIn(CardRef = BindingObj.toastCard)
                HapticFor(ContextRef = ContextRef, KindVal = KindVal)
            } catch (_: Exception) {
                RemoveOverlay()
                ShowSystemFallback(
                    ContextRef = ContextRef,
                    MessageText = MessageText,
                    KindVal = KindVal,
                    LongDuration = LongDuration
                )
            }
        }
    }

    fun RemoveOverlay() {
        OverlayDismiss?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        OverlayDismiss = null
        val RootView = OverlayRootView
        val WindowMgr = OverlayWindowMgr
        OverlayRootView = null
        OverlayWindowMgr = null
        if (RootView == null || WindowMgr == null) return
        CancelCardAnimation(RootView = RootView)
        try {
            WindowMgr.removeView(RootView)
        } catch (_: Exception) {
        }
    }

    private fun Paint(
        BindingObj: ViewAppToastBinding,
        KindVal: Kind,
        MessageText: CharSequence,
        TitleText: CharSequence
    ) {
        val ContextRef = BindingObj.root.context
        val TintColor = ContextCompat.getColor(ContextRef, TintOf(KindVal = KindVal))

        BindingObj.toastCard.setBackgroundResource(BackgroundOf(KindVal = KindVal))
        BindingObj.ivToastIcon.setImageResource(IconOf(KindVal = KindVal))
        BindingObj.ivToastIcon.imageTintList =
            ContextCompat.getColorStateList(ContextRef, TintOf(KindVal = KindVal))
        BindingObj.tvToastMessage.text = MessageText
        BindingObj.tvToastMessage.setTextColor(TintColor)

        if (TitleText.isBlank()) {
            BindingObj.tvToastTitle.visibility = View.GONE
        } else {
            BindingObj.tvToastTitle.visibility = View.VISIBLE
            BindingObj.tvToastTitle.text = TitleText
            BindingObj.tvToastTitle.setTextColor(TintColor)
        }
    }

    private fun SlideIn(CardRef: View) {
        CardRef.alpha = 0f
        CardRef.doOnLayout { LaidView ->
            LaidView.translationX = TravelOf(CardRef = LaidView)
            LaidView.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(ENTER_MS)
                .setInterpolator(OvershootInterpolator(0.9f))
                .start()
        }
    }

    private fun SlideOut(CardRef: View, OnDone: () -> Unit) {
        CardRef.animate()
            .translationX(TravelOf(CardRef = CardRef))
            .alpha(0f)
            .setDuration(EXIT_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { OnDone() }
            .start()
    }

    private fun TravelOf(CardRef: View): Float {
        val MarginPx = CardRef.context.resources.getDimensionPixelSize(R.dimen.toast_margin)
        val WidthPx = if (CardRef.width > 0) CardRef.width else CardRef.measuredWidth
        return (WidthPx + MarginPx).toFloat()
    }

    private fun TopInsetOf(HostView: View): Int {
        val BasePx = HostView.context.resources.getDimensionPixelSize(R.dimen.toast_top_inset)
        val InsetsObj = ViewCompat.getRootWindowInsets(HostView) ?: return BasePx
        val StatusTop = InsetsObj.getInsets(WindowInsetsCompat.Type.statusBars()).top
        return BasePx + StatusTop
    }

    private fun StatusBarHeightOf(ContextRef: Context): Int {
        val ResourceId = ContextRef.resources.getIdentifier(
            "status_bar_height", "dimen", "android"
        )
        if (ResourceId <= 0) return 0
        return ContextRef.resources.getDimensionPixelSize(ResourceId)
    }

    private fun ClearPending(ParentRef: ViewGroup) {
        PendingDismiss?.let { RunnableRef -> MainHandler.removeCallbacks(RunnableRef) }
        PendingDismiss = null
        val ExistingView = ParentRef.findViewWithTag<View>(VIEW_TAG) ?: return
        CancelCardAnimation(RootView = ExistingView)
        ParentRef.removeView(ExistingView)
    }

    private fun CancelCardAnimation(RootView: View) {
        try {
            RootView.findViewById<View>(R.id.toastCard)?.animate()?.cancel()
        } catch (_: Exception) {
        }
        RootView.animate().cancel()
    }

    private fun OverlayContextOf(ContextRef: Context): Context {
        val NightFlag = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_NO -> Configuration.UI_MODE_NIGHT_NO
            AppCompatDelegate.MODE_NIGHT_YES -> Configuration.UI_MODE_NIGHT_YES
            else -> 0
        }
        if (NightFlag == 0) return ContextThemeWrapper(ContextRef, R.style.Theme_DataReaderApp)

        val BaseContext = try {
            val ConfigObj = Configuration(ContextRef.resources.configuration)
            ConfigObj.uiMode =
                (ConfigObj.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or NightFlag
            ContextRef.createConfigurationContext(ConfigObj)
        } catch (_: Exception) {
            ContextRef
        }
        return ContextThemeWrapper(BaseContext, R.style.Theme_DataReaderApp)
    }

    private fun ActivityOf(ContextRef: Context?): Activity? {
        var CursorRef = ContextRef
        while (CursorRef is ContextWrapper) {
            if (CursorRef is Activity) return CursorRef
            CursorRef = CursorRef.baseContext
        }
        return null
    }

    private fun ShowSystemFallback(
        ContextRef: Context,
        MessageText: CharSequence,
        KindVal: Kind,
        LongDuration: Boolean
    ) {
        HapticFor(ContextRef = ContextRef, KindVal = KindVal)
        try {
            android.widget.Toast.makeText(
                ContextRef.applicationContext,
                MessageText,
                if (LongDuration) {
                    android.widget.Toast.LENGTH_LONG
                } else {
                    android.widget.Toast.LENGTH_SHORT
                }
            ).show()
        } catch (_: Exception) {
        }
    }

    private fun BackgroundOf(KindVal: Kind): Int = when (KindVal) {
        Kind.Success -> R.drawable.bg_toast_success
        Kind.Error -> R.drawable.bg_toast_error
        Kind.Warning -> R.drawable.bg_toast_warning
        Kind.Info -> R.drawable.bg_toast_info
    }

    private fun IconOf(KindVal: Kind): Int = when (KindVal) {
        Kind.Success -> R.drawable.ic_check_circle
        Kind.Error -> R.drawable.ic_error
        Kind.Warning -> R.drawable.ic_alert
        Kind.Info -> R.drawable.ic_info
    }

    private fun TintOf(KindVal: Kind): Int = when (KindVal) {
        Kind.Success -> R.color.status_green_text
        Kind.Error -> R.color.status_red_text
        Kind.Warning -> R.color.status_amber_text
        Kind.Info -> R.color.status_blue_text
    }

    private fun HapticFor(ContextRef: Context, KindVal: Kind) {
        when (KindVal) {
            Kind.Success -> HapticFeedback.Success(ContextRef = ContextRef)
            Kind.Error -> HapticFeedback.Failure(ContextRef = ContextRef)
            Kind.Warning -> HapticFeedback.Reject(ContextRef = ContextRef)
            Kind.Info -> HapticFeedback.Tap(ContextRef = ContextRef)
        }
    }
}
