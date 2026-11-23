package com.example.tugashelloworld.model

import androidx.annotation.StringRes
import com.example.tugashelloworld.R

enum class SortOrder(@StringRes val label: Int) {
    NEWEST(R.string.sort_newest),
    OLDEST(R.string.sort_oldest),
    TITLE(R.string.sort_title)
}
