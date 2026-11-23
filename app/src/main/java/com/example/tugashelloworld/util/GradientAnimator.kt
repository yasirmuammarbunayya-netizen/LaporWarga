package com.example.tugashelloworld.util

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Memberi latar gradasi yang berganti warna otomatis pada sebuah View.
 * Animasi hanya berjalan saat layar tampil, dan berhenti saat layar tidak terlihat (hemat baterai).
 */
class GradientAnimator(private val target: View, bottomRadiusDp: Float) {

    private val drawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, WallpaperPalette.static
    )
    private var animator: ValueAnimator? = null

    init {
        val radius = bottomRadiusDp * target.resources.displayMetrics.density
        if (radius > 0f) {
            // Urutan: kiri-atas, kanan-atas, kanan-bawah, kiri-bawah (masing-masing x,y)
            drawable.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
        }
        target.background = drawable
    }

    fun start() {
        stop()
        if (!WallpaperPreference.isAnimated(target.context)) {
            drawable.setColors(WallpaperPalette.static)
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1_000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                drawable.setColors(WallpaperPalette.colorsAt(System.currentTimeMillis()))
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    /** Dipanggil saat pengguna mengubah pengaturan wallpaper. */
    fun refresh() = start()
}

/** Pasang wallpaper berganti warna pada [target] mengikuti siklus hidup tampilan fragment. */
fun Fragment.animateWallpaper(target: View, bottomRadiusDp: Float = 0f): GradientAnimator {
    val animator = GradientAnimator(target, bottomRadiusDp)
    viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = animator.start()
        override fun onStop(owner: LifecycleOwner) = animator.stop()
    })
    return animator
}
