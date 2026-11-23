package com.example.tugashelloworld.model

import androidx.annotation.StringRes
import com.example.tugashelloworld.R

enum class ReportCategory(@StringRes val label: Int) {
    ROAD(R.string.cat_road),
    LIGHTING(R.string.cat_lighting),
    GARBAGE(R.string.cat_garbage),
    WATER(R.string.cat_water),
    FACILITY(R.string.cat_facility),
    OTHER(R.string.cat_other);

    companion object {
        fun fromName(name: String?): ReportCategory =
            values().firstOrNull { it.name == name } ?: OTHER
    }
}
