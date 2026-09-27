# Verifikasi Bow Pack 1.2.0 🏹

Tanggal: 26 September 2026. Target: Minecraft Java 26.3, Fabric Loader 0.19.5,
Fabric API 0.161.0+26.3, dan Java 25 pada Windows.

## Perubahan

- Ricochet I–IV memakai radius 10/20/30/35 blok dan batas 3/6/10/tanpa batas
  pantulan. Level IV menandai mob sebelumnya sebagai penyerang yang tampak,
  sementara pemilik panah dan kredit kill tetap penembak.
- Bouncy I–III memberi 10/20/30 pantulan blok. Level IV dihapus.
- Breakthrough I menembus lawan satu kali per panah. Saat digabung Ricochet,
  tiap hit yang punya sasaran pantul menghasilkan satu cabang tanpa Breakthrough.
- Follow I membelokkan panah hingga 10° per tick menuju lawan terdekat dalam
  64 blok, termasuk lawan di belakang; panah Rainy dikecualikan.
- Rainy Shot I–III menjadwalkan 6/8/10 panah ke sasaran terakhir tiap 200 tick
  setelah hit, berhenti setelah 420 tick tanpa hit. Panah membidik diam 40 tick,
  kemudian meluncur serentak tanpa gravitasi atau hambatan.
- Anti-Gravity diganti menjadi Anti Friction dengan ID baru
  `bowpack:anti_friction_shot`. ID lama tidak dimigrasikan untuk world baru.

## Pengujian otomatis

- `build` dari salinan proyek lokal di luar OneDrive: **berhasil**. Sebanyak
  87 file sumber, resource, dan konfigurasi dicocokkan checksum-nya terhadap
  proyek sebelum JAR final disalin. Semua file sumber dan skrip di bawah 250 baris.
- **11 unit test lulus, 0 gagal. 71 GameTest wajib lulus, 0 gagal** pada
  server Minecraft asli. Build final selesai pukul 19:08 WIB.
- Tes Ricochet memeriksa keempat radius, batas pantulan I–III, level IV yang
  berulang, respons mob, kredit kill, dan save/load sisa pantulan.
- Tes Bouncy memeriksa benturan blok ke-10/20/30 dan panah yang menancap
  setelah batasnya habis. Anti Friction diuji di udara dan air.
- Tes Breakthrough menembus **130 lawan berbeda**, menyimpan riwayat hit,
  menghindari hit ganda, membuat cabang pada tiap hit yang valid, dan tidak
  bercabang ketika sasaran pantul tidak ada.
- Tes Follow memeriksa belokan 10° terhadap lawan di belakang dan pergantian
  ke sasaran yang lebih dekat.
- Tes Rainy memeriksa jumlah 6/8/10, sasaran dan efek hit terbaru, jadwal
  10 detik yang tidak diulang saat sasaran berganti, penghentian setelah
  21 detik, logout, efek tipped/spectral/api/damage, masa diam dua detik,
  bidikan terhadap sasaran bergerak, serta save/load saat masih membidik.
- Regresi Bow/Crossbow, formasi 80 panah, akses Creative/Survival, kombinasi
  anvil, umur panah 600 tick, dan perilaku mob pemegang senjata juga lulus.
- Bukti: `build/verification/final-release-build-1.2.0.log`, dua file
  `TEST-*.xml` versi 1.2.0 di folder yang sama, serta `install-1.2.0.log`.

## Pemasangan

- Mode `-ValidateOnly` berhasil, lalu versi 1.2.0 dipasang ke profil launcher
  **Bow Pack 26.3**. Hanya satu Bow Pack ada di folder mods; versi 1.1.0
  dicadangkan dan diganti.
- Metadata JAR, profil launcher `fabric-loader-0.19.5-26.3`, dan SHA-256
  hasil build, salinan proyek, serta JAR terpasang cocok:
  `9DA58F7EB5FE38BC85D969020E00DEF49B51F1B03B73FB24EB67E84F2B96200C`.
- Cadangan pemasangan:
  `.minecraft/bow-pack-backups/20260926-190317-fb011f`.

## Batas pemeriksaan

- Pemeriksaan visual dan rasa bermain di klien Minecraft belum dilakukan;
  pengguna akan memeriksanya di game pada world baru.
- Koneksi LAN dua pemain dan pengujian performa permainan jangka panjang
  belum dilakukan.
