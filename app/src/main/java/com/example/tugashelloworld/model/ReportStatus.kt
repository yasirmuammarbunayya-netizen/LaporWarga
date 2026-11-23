package com.example.tugashelloworld.model

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.example.tugashelloworld.R

enum class ReportStatus(@StringRes val label: Int, @ColorRes val color: Int) {
    SENT(R.string.status_sent, R.color.status_sent),
    PROCESSING(R.string.status_processing, R.color.status_processing),
    DONE(R.string.status_done, R.color.status_done);

    companion object {
        fun fromName(name: String?): ReportStatus =
            values().firstOrNull { it.name == name } ?: SENT
    }
}
