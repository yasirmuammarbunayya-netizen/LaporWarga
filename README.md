# Lapor Warga (Versi Lokal) — Aplikasi Pelaporan Kerusakan Fasilitas Umum

UAS Pemrograman Mobile · Android (Kotlin) · MVVM · Room (SQLite) · OpenStreetMap

**Tidak membutuhkan Firebase, `google-services.json`, maupun API key apa pun.**
Cukup buka di Android Studio, sync, lalu Run.

## Persyaratan (sudah disesuaikan untuk emulator **Medium Phone API 37.2 / Android 17**)
- Android Studio versi terbaru (disarankan **Panda 3 / 2025.3.3 atau lebih baru**)
- **Gradle JDK 17 atau 21** (Settings → Build Tools → Gradle → Gradle JDK)
- Di **SDK Manager** pasang: *Android 16.0 (API 36)* SDK Platform dan *Android SDK Build-Tools 36.0.0*
  (Gradle juga akan mengunduhnya otomatis jika lisensi sudah diterima)
- Versi toolchain: AGP 8.13.0 · Gradle 8.13 · Kotlin 2.1.21 · KSP 2.1.21-2.0.2 · `compileSdk` 36 · `targetSdk` 36 · `minSdk` 29
- Aplikasi yang dikompilasi dengan SDK 36 berjalan normal di Android 17 (API 37.x)
- Koneksi internet saat sync pertama dan untuk memuat tile peta

## Cara menjalankan
1. `File → Open` → pilih folder project ini → tunggu *Gradle sync* selesai.
2. Pilih emulator (API 26+) atau perangkat fisik → tombol **Run**.
3. Untuk mencoba GPS di emulator: *Extended controls (⋯) → Location → Set location*.

## Catatan kamera
- **FOTO**: mode kualitas maksimum, disimpan hingga 2560 px (JPEG 92%).
- **POTRET**: efek blur latar (CameraX Extensions) bila didukung perangkat; jika tidak, memakai bingkai potret dengan zoom ringan.
- **VIDEO**: Full HD (turun otomatis ke HD/SD bila perlu), maks. 30 detik, dengan suara jika izin mikrofon diberikan.
- Database naik ke versi 2 (kolom `videoPath`). Data lama otomatis direset saat pertama kali menjalankan versi ini
  (atau hapus instalasi aplikasi di emulator/HP terlebih dahulu).

## Galeri
- Foto & video dari kamera aplikasi otomatis tersimpan ke **Galeri** perangkat (resolusi penuh):
  foto di `Pictures/LaporWarga`, video di `Movies/LaporWarga`. Tidak butuh izin penyimpanan (Android 10+).
- Di layar tinjau kamera tombol **Hapus** menghapus hasil beserta salinannya di galeri.
- Di form: menghapus foto/video menawarkan "hapus dari laporan saja" atau "hapus juga dari galeri".
- Di Detail: saat menghapus laporan ada kotak centang "Hapus juga foto & video dari galeri".
- `minSdk` = 29 (Android 10). Database versi 3 (data lama otomatis direset).

## Fitur (pemetaan ke syarat UAS)

| Syarat | Implementasi |
|---|---|
| Autentikasi | Registrasi, login, logout, **lupa password** (verifikasi email + nama lengkap, lalu setel password baru). Password di-hash (PBKDF2 + salt), sesi tersimpan di SharedPreferences |
| CRUD persisten | Create/Read/Update/Delete laporan di SQLite (Room). Hapus memakai dialog konfirmasi |
| Entitas utama (≥ 4 atribut) | `Report`: title, description, category, address, latitude, longitude, photoPath, status, userId, userName, createdAt, updatedAt |
| Pencarian / filter | SearchView (judul/deskripsi/alamat), filter kategori, filter status, pengurutan |
| ≥ 3 layar + navigasi | Login, Register, Beranda, Peta, Laporan Saya, Profil, Detail, Form (Navigation Component + BottomNavigationView) |
| Integrasi perangkat/layanan | **Kamera dalam aplikasi (CameraX) dengan mode POTRET / FOTO / VIDEO**, kamera depan & belakang, blitz/senter, fokus & zoom, tinjau hasil; cadangan otomatis ke kamera bawaan HP, Galeri (Photo Picker), GPS (LocationManager) + Geocoder, Peta OpenStreetMap (sebaran laporan + pemilih lokasi per laporan), notifikasi lokal, Intent aplikasi peta, Share |
| Izin runtime | Kamera, lokasi, notifikasi. Jika ditolak aplikasi tetap berjalan (input manual / tanpa notifikasi) |
| UI/UX | Material 3, dark mode, **wallpaper gradasi berganti warna otomatis (dapat dimatikan di Profil)**, loading / empty / error state, pull-to-refresh |
| Validasi & error | Validasi semua form, pesan error jelas dalam Bahasa Indonesia |
| Rotasi layar | State disimpan di ViewModel (form, filter, foto, koordinat) |
| Arsitektur | MVVM: Fragment → ViewModel → Repository → Room DAO |

## Struktur project

```
app/src/main/java/com/laporwarga/app/
├── LaporWargaApp.kt          # Application: tema + channel notifikasi
├── MainActivity.kt           # Single activity + NavHost + BottomNav
├── model/                    # Report, User, ReportCategory, ReportStatus, SortOrder
├── db/                       # AppDatabase, ReportDao, UserDao (Room)
├── data/                     # AuthRepository, ReportRepository, SessionManager, PasswordHasher
├── util/                     # Resource, ErrorMapper, Validators, ImageUtils, LocationHelper, ...
└── ui/
    ├── auth/                 # Login, Register, AuthViewModel
    ├── home/                 # HomeFragment, ReportAdapter, ReportViewModel (shared)
    ├── map/                  # MapFragment (osmdroid)
    ├── myreports/            # MyReportsFragment
    ├── detail/               # DetailFragment, DetailViewModel
    ├── form/                 # FormFragment, FormViewModel (tambah & edit)
    └── profile/              # ProfileFragment
```

Foto laporan disimpan sebagai file JPEG terkompresi di penyimpanan internal (`files/photos`),
database hanya menyimpan path-nya.

## Jika terjadi error saat Sync / Build

| Gejala | Solusi |
|---|---|
| *Unsupported Java / Unsupported class file major version* | Settings → Build Tools → Gradle → **Gradle JDK = 17 atau 21** (project ini memakai Gradle 8.5) |
| *Plugin … was not found* / gagal unduh | Pastikan internet aktif, lalu **File → Sync Project with Gradle Files** |
| *SDK location not found* | Android Studio otomatis membuat `local.properties`; atau set di File → Project Structure |
| *Failed to find Platform SDK 36* | Tools → SDK Manager → install **Android 16.0 (API 36)** dan **Build-Tools 36.0.0** |
| Studio menawarkan *AGP Upgrade Assistant* | Boleh diabaikan (**Remind me later**); project ini sudah memakai AGP 8.13 yang stabil. Jangan upgrade ke AGP 9 karena mengubah format build |
| *Gradle version … required by AGP* | Pastikan memakai `gradle-wrapper.properties` bawaan project (Gradle 8.13) |
| Peta kosong / abu-abu | Perangkat butuh internet untuk memuat tile OpenStreetMap |

## Membuat Signed APK (deployment)

**Build → Generate Signed App Bundle / APK → APK** → buat keystore baru → pilih `release` → Finish.
Hasil: `app/release/app-release.apk`. Unggah ke **GitHub Releases**. Jangan commit file `.jks`.

## Skenario demo (disarankan)

1. Daftar akun baru → otomatis masuk Beranda (tutup & buka ulang app: tetap login).
2. Buat laporan: foto dari kamera, "Gunakan lokasi saat ini", pilih kategori, kirim.
3. Cari kata kunci, filter kategori/status, urutkan. Cari kata acak untuk menampilkan pesan "tidak ditemukan".
4. Tab Peta → ketuk marker → "Lihat detail".
5. Di Detail: Ubah status (muncul notifikasi), Ubah, Hapus (dialog konfirmasi).
6. Putar layar saat mengisi form (data tetap).
7. Profil: statistik, mode gelap, keluar. Masuk lagi dengan akun yang sama.

## Checklist pengujian
- [ ] Registrasi dengan email yang sudah terpakai → pesan error jelas
- [ ] Login salah password → pesan error jelas
- [ ] Form kosong → semua error validasi tampil
- [ ] Tolak izin kamera / lokasi → aplikasi tidak crash
- [ ] Rotasi layar di semua halaman
- [ ] Dark mode terbaca baik di semua layar
