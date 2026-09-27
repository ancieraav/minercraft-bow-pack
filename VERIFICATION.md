# Verifikasi Bow Pack 1.1.0 🏹

Tanggal: 25 September 2026. Target: Minecraft Java 26.3, Fabric Loader 0.19.5,
Fabric API 0.161.0+26.3, Java 25.0.1 pada Windows.

## Perubahan

- Front maksimum IV hanya berlaku pada arah utama. Back I–V menambah tepat
  1–5 panah paralel ke arah berlawanan, termasuk kemiringan crosshair.
- Multi menggandakan semua jalur; Diagonal tidak mendapat kolom Front.
  Batas formasi menjadi 80 projectile, dengan 21 buku di katalog Creative.
- Panah custom mempunyai deadline 600 tick yang bertahan saat pantulan dan save/load.
  Panah tambahan menancap dibersihkan dalam 100 tick; panah hampir diam dalam 40 tick.
- Lima enchantment biasa masuk jalur Survival vanilla; Ricochet dan Anti-Gravity
  menjadi treasure. Biaya enchanting/anvil serta harga buku mengikuti data baru.
- Efek pada Bow mob memakai jalur Skeleton/variannya dan Illusioner. Crossbow mob
  mempertahankan bidikan native, bukan arah pandangan penembak.
- Perbaikan Ricochet pada penunggang/tunggangan, penghapusan command `/bowpack`,
  dan penonaktifan Multishot vanilla tetap dipertahankan.

## Pengujian otomatis

- `test runGameTest jar sourcesJar`: berhasil.
- **9 unit test lulus**, termasuk seluruh 600 kombinasi level, sudut, jarak,
  pembalikan pitch Back, pemisahan Front/Diagonal, dan refleksi.
- **57 GameTests wajib lulus** pada mesin Minecraft, termasuk tes bawaan Fabric.
  Rangkaian final selesai pukul 23:39:49 WIB; durasi tes 20,27 detik.
- Native Bow/Crossbow menghasilkan 80 projectile; pickup 1/79, ammo/durability,
  payload tipped/spectral, serta damage Back ke sasaran di belakang telah diuji.
- Enam tes mob mencakup Skeleton, Stray, Bogged, Pillager, Illusioner, dan senjata
  tanpa enchantment. Bidikan native dan payload Slowness/Poison dipertahankan.
- Tes lifecycle memeriksa Ricochet nyata selama 600 tick simulasi, deadline save/load,
  migrasi satu kali, penolakan data rusak, pembersihan panah menancap, dan apex normal.
- Uji 20 tembakan Bow maksimum menghasilkan 1.600 panah dan menghabiskan 20 ammo.
  Setelah diperlambat untuk mensimulasikan panah berhenti, seluruhnya terhapus pada
  tick tidak aktif ke-40. Ini tes pembersihan entity, bukan benchmark FPS.
- Tujuh tes acquisition memeriksa batas daya table, hasil table/loot/memancing native,
  kategori treasure/harga, penawaran librarian standar, dan grindstone.
- GameTestServer otomatis mengaktifkan Trade Rebalance. Tes librarian memverifikasi
  rute standar dari resource vanilla dan menjalankan definisi perdagangan native
  tersebut; tujuh rute biome eksperimen juga diperiksa tetap utuh. Runner tidak diubah.
- Tes anvil memverifikasi urutan README untuk ketujuh enchantment maksimum dalam
  Survival, konsumsi XP, upgrade III ke IV, Front V lama, serta batas Too Expensive.
- Seluruh delapan regresi Ricochet tunggangan dan pemeriksaan gameplay sebelumnya lulus.
- Selama pengujian ditemukan hook cleanup yang melewatkan return awal panah menancap;
  hook diperbaiki menjadi RETURN, lalu tes batas tick 99/100 lulus tanpa dilonggarkan.
- Bukti: `build/verification/minecraft-gametest-1.1.0.log`,
  `TEST-dev.bowpack.math.ShotPatternTest-1.1.0.xml`, dan `install-1.1.0.log`.

Build dilakukan dari salinan lokal di luar OneDrive. Semua file sumber dan resource
dicocokkan dengan checksum terhadap proyek ini sebelum artefak disalin.

## Pemasangan

- Versi 1.1.0 dipasang pada profil launcher `Bow Pack 26.3`.
- Cadangan: `.minecraft/bow-pack-backups/20260925-234043-3b6e99`.
- Hanya satu versi Bow Pack berada di folder mods. Metadata JAR, profil launcher,
  serta kecocokan checksum artefak dan salinan terpasang telah diverifikasi.
- SHA-256: `053fd7c326edca7136ecf1c0d750854c0f830e77efa901e1f4d1d6f982c3cc3b`.
- World dan item yang sudah diambil pemain tidak diedit oleh pemasang.

## Batas pemeriksaan

- Tidak menggunakan Computer Use untuk perubahan ini. Pemeriksaan visual versi 1.1.0
  diserahkan kepada pengguna; hasil di atas berasal dari tes server dan pemeriksaan berkas.
- Koneksi LAN dua komputer dan pengujian performa permainan berkepanjangan belum dilakukan.
- Peringatan deprecation helper GameTest dan diagnostik performance counter Windows
  tercatat pada log; seluruh tes tetap lulus.
