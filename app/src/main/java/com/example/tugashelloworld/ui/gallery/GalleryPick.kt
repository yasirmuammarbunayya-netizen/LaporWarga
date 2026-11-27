package com.example.tugashelloworld.ui.gallery

import android.net.Uri
import androidx.navigation.NavController
import com.example.tugashelloworld.R

/**
 * Mengirim media pilihan dari galeri kembali ke formulir laporan.
 */
object GalleryPick {

    const val KEY_URI = "gallery_pick_uri"
    const val KEY_IS_VIDEO = "gallery_pick_is_video"

    /** true = item berasal dari album LaporWarga (boleh ikut dihapus dari galeri). */
    const val KEY_FROM_ALBUM = "gallery_pick_from_album"

    const val ARG_PICK_MODE = "pickMode"
    const val ARG_INDEX = "index"
    const val ARG_FILE_PATH = "filePath"
    const val ARG_FILE_IS_VIDEO = "fileIsVideo"

    fun deliver(
        nav: NavController,
        uri: Uri,
        isVideo: Boolean,
        fromAlbum: Boolean
    ) {
        val handle = runCatching {
            nav.getBackStackEntry(R.id.formFragment).savedStateHandle
        }.getOrNull() ?: return

        // URI diisi paling akhir karena FormFragment mengamati kunci ini.
        handle[KEY_IS_VIDEO] = isVideo
        handle[KEY_FROM_ALBUM] = fromAlbum
        handle[KEY_URI] = uri.toString()

        nav.popBackStack(R.id.formFragment, false)
    }
}
