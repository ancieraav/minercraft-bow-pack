# Verifikasi Bow Pack 1.3.0 🏹

Tanggal: 27 September 2026. Target: Minecraft Java 26.3, Fabric Loader 0.19.5,
Fabric API 0.161.0+26.3, dan Java 25 pada Windows.

## Perubahan

- Rainy Shot dihapus dari katalog, kode efek, dan data enchantment.
- Unstopable I ditambahkan untuk Bow/Crossbow. Panah menghancurkan hanya blok
  yang terkena jika ketahanannya berada di bawah ambang daya dasar TNT. Panah
  dapat melanjutkan ke blok berikutnya. Efek ini tidak membuat ledakan.
- Unstopable dan Bouncy saling eksklusif. Mending dan Infinity dapat digabung
  pada Bow melalui anvil.
- Pemasang kini menentukan lokasi JAR bawaan setelah skrip mulai berjalan,
  sehingga `-ValidateOnly` dan pemasangan tanpa `-ModJar` berfungsi.

## Pengujian otomatis

- Build dari salinan lokal di luar OneDrive berhasil. Semua berkas `src` dan
  konfigurasi build yang ada di salinan dicocokkan SHA-256 terhadap proyek.
- **11 unit test lulus, 0 gagal. 77 GameTest wajib lulus, 0 gagal** pada server
  Minecraft tanpa menjalankan klien permainan.
- GameTest memeriksa dua blok berurutan, blok di luar lintasan, drop biasa,
  obsidian, bedrock, TNT terkena panah berapi tanpa menyala, aturan dunia,
  tembakan Bow/Crossbow, dan larangan gabungan Bouncy–Unstopable.
- GameTest anvil memeriksa Mending–Infinity dalam kedua urutan, batas sembilan
  enchantment Bow Pack yang kompatibel, serta larangan Infinity pada Crossbow.
- Data panah lama tetap dapat dibaca; panah Rainy lama dibuang. Item lama yang
  memiliki Rainy dan Mending tetap memuat item serta Mending, sambil membuang
  ID Rainy yang tidak dikenal dan melaporkannya sebagai kesalahan data.
- Bukti build dan tes: `build/verification/build-1.3.0.log`,
  `build/verification/gametest-1.3.0.log`, dan `TEST-*.xml` versi 1.3.0.

## Pemasangan

- `install.ps1 -ValidateOnly` berhasil. Pemasangan menghasilkan profil
  `fabric-loader-0.19.5-26.3` dan `mods/bow-pack-1.3.0.jar`.
- JAR hasil build, salinan di proyek, dan JAR terpasang memiliki SHA-256 sama:
  `611D73979D37931E458F3A2E0C25AB94A32E42011A067A420B06BF240ACEAAA2`.
- `bow-pack-1.2.0.jar` tidak lagi ada di folder mods. Cadangan dibuat di
  `.minecraft/bow-pack-backups/20260927-122744-7f024b`.

## Batas pemeriksaan

- Sesuai permintaan, klien Minecraft tidak dijalankan untuk mencoba gameplay.
- Pemeriksaan visual, LAN dua pemain, dan performa permainan jangka panjang
  belum dilakukan.
