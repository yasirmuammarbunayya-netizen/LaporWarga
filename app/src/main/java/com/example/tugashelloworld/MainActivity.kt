package com.example.tugashelloworld

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.tugashelloworld.data.SessionManager
import com.example.tugashelloworld.databinding.ActivityMainBinding
import com.example.tugashelloworld.util.NotificationHelper
import kotlin.math.max

/**
 * Satu-satunya Activity. Seluruh layar adalah Fragment yang dihubungkan Navigation Component.
 * Bottom navigation hanya tampil di 4 layar utama.
 *
 * Mendukung tampilan layar penuh (edge-to-edge) yang diwajibkan mulai Android 15:
 * ruang status bar, bilah navigasi sistem, dan keyboard diatur lewat WindowInsets.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val topLevelDestinations = setOf(
        R.id.homeFragment, R.id.mapFragment, R.id.myReportsFragment, R.id.profileFragment
    )

    private var onTopLevel = false
    private var onCamera = false
    private var systemBottomInset = 0
    private var imeVisible = false

    // Izin notifikasi (Android 13+). Jika ditolak, aplikasi tetap berjalan normal.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* opsional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Status bar: ikon terang di atas header biru gelap. Bilah navigasi mengikuti mode terang/gelap.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        navController = navHost.navController

        // Sesi login tersimpan di SharedPreferences: jika sudah login, langsung ke Beranda.
        val loggedIn = SessionManager(this).isLoggedIn
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(if (loggedIn) R.id.homeFragment else R.id.loginFragment)
        navController.graph = graph

        binding.bottomNav.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            onTopLevel = destination.id in topLevelDestinations
            onCamera = destination.id == R.id.cameraFragment
            val dark = onCamera || destination.id == R.id.mediaViewerFragment
            binding.root.setBackgroundColor(if (dark) Color.BLACK else themeBackgroundColor())
            updateBottomInsets()
        }

        if (loggedIn) requestNotificationPermissionIfNeeded()
    }

    /** Mengatur jarak untuk status bar, bilah navigasi sistem, notch, dan keyboard. */
    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

            systemBottomInset = bars.bottom
            imeVisible = imeBottom > max(bars.bottom, 1)

            binding.statusBarSpace.updateLayoutParams { height = bars.top }
            // Saat keyboard tampil, seluruh konten naik setinggi keyboard
            binding.root.setPadding(bars.left, 0, bars.right, if (imeVisible) imeBottom else 0)
            updateBottomInsets()
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun updateBottomInsets() {
        val showNav = onTopLevel && !imeVisible
        binding.bottomNav.isVisible = showNav
        // Latar bottom nav meluas sampai ke bawah bilah navigasi sistem
        binding.bottomNav.setPadding(0, 0, 0, if (showNav) systemBottomInset else 0)
        // Layar tanpa bottom nav: sisakan ruang untuk bilah navigasi sistem
        binding.navHost.setPadding(0, 0, 0, if (!showNav && !imeVisible) systemBottomInset else 0)
    }

    private fun themeBackgroundColor(): Int {
        val value = TypedValue()
        theme.resolveAttribute(android.R.attr.colorBackground, value, true)
        return value.data
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.hasPermission(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
