package com.example.tugashelloworld.util

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormat {
    private val locale = Locale("id", "ID")

    fun full(millis: Long): String =
        if (millis <= 0L) "-" else SimpleDateFormat("dd MMMM yyyy, HH:mm", locale).format(Date(millis))

    fun relative(millis: Long): String =
        if (millis <= 0L) "-"
        else DateUtils.getRelativeTimeSpanString(
            millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        ).toString()
}
