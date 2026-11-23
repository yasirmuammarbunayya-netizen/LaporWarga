package com.example.tugashelloworld.util

import android.animation.ArgbEvaluator

/**
 * Palet warna wallpaper. Warna dihitung dari waktu sistem, sehingga semua layar
 * berganti warna secara serempak dan tetap berkesinambungan saat berpindah halaman.
 */
object WallpaperPalette {

    private val palettes = arrayOf(
        intArrayOf(0xFF1E3A8A.toInt(), 0xFF2563EB.toInt()), // Biru
        intArrayOf(0xFF5B21B6.toInt(), 0xFFDB2777.toInt()), // Ungu - merah muda
        intArrayOf(0xFF065F46.toInt(), 0xFF0891B2.toInt()), // Hijau - tosca
        intArrayOf(0xFF9A3412.toInt(), 0xFFDC2626.toInt()), // Jingga - merah
        intArrayOf(0xFF312E81.toInt(), 0xFF7C3AED.toInt())  // Indigo - ungu
    )

    /** Lama satu perpindahan warna (milidetik). */
    private const val STEP_MS = 6_000L

    private val evaluator = ArgbEvaluator()

    /** Warna awal / saat animasi dimatikan. */
    val static: IntArray get() = palettes[0]

    fun colorsAt(timeMillis: Long): IntArray {
        val cycle = STEP_MS * palettes.size
        val position = (timeMillis % cycle).toFloat() / STEP_MS
        val index = position.toInt()
        val fraction = position - index
        // Easing smoothstep agar perpindahan warna terasa halus
        val t = fraction * fraction * (3f - 2f * fraction)

        val from = palettes[index % palettes.size]
        val to = palettes[(index + 1) % palettes.size]
        return intArrayOf(
            evaluator.evaluate(t, from[0], to[0]) as Int,
            evaluator.evaluate(t, from[1], to[1]) as Int
        )
    }
}
