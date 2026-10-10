# Verifikasi Bow Pack 1.4.0 🏹

Tanggal: 27 September 2026. Minecraft Java 26.3, Fabric Loader 0.19.5,
Fabric API 0.161.0+26.3, dan Java 25 pada Windows.

## Perubahan

- Accelerate I–III menambah laju 0,10/0,15/0,20 blok/tick setiap tick, dibatasi
  5/7/9 blok/tick. Damage memakai kecepatan awal; gravitasi tetap mengubah arah.
- Kecepatan, level, dan waktu kedaluwarsa bertahan saat save/load, Ricochet,
  pantulan, serta percabangan Breakthrough. Semua jenis panah didukung.
- Unstopable mencoba menghancurkan blok sebelum Bouncy memantulkannya.
  Blok yang berhasil dihancurkan tidak memakai jatah pantulan.
- Penghancuran Unstopable tidak mengirim efek suara/partikel pecahan blok.
  Ketika drop blok dimatikan, perhitungan loot dilewati. Pembaruan blok sekitar
  dan efek penghancuran biasa tetap berjalan.
- Follow mengurangi perhitungan berulang dan pembaruan jaringan saat arah
  tidak berubah, termasuk kasus pembulatan arah diagonal.
- Katalog berisi 11 enchantment dan 30 buku. Semua 11 enchantment dapat
  digabungkan melalui anvil Survival dengan urutan buku yang didokumentasikan.

## Pengujian otomatis

- **16 unit test lulus, 0 gagal; 99 GameTest wajib lulus, 0 gagal.**
- Build dijalankan dari salinan di luar OneDrive. SHA-256 dari 102 berkas
  sumber, skrip, dan konfigurasi dicocokkan dengan proyek sebelum pemasangan.
- Diuji: pertumbuhan/batas setiap level, udara/air, Follow, Anti Friction,
  damage tetap dan Power, Ricochet, cabang Breakthrough, Bouncy–Unstopable,
  normal/tipped/spectral arrows, tembakan mob, ammo/durability, dan firework.
- Save/load diperiksa pada kecepatan 3,3 serta batas 9 dengan arah sumbu dan
  diagonal; data lama tetap memakai Accelerate level 0. Panah berhenti,
  menancap, atau kedaluwarsa tidak dihidupkan kembali.
- Tes paket memastikan event pecahan 2001 hilang hanya untuk blok yang
  dihancurkan Unstopable. Torch yang runtuh karena blok penyangganya hilang
  masih mendapat pembaruan dan efek native. Drop aktif/nonaktif, obsidian,
  bedrock, blok waterlogged, dan aturan penghancuran dunia diperiksa.
- Percobaan awal menemukan fixture save/load berada di batas atas arena;
  fixture dipindah ke dalam arena dan ditambah pemeriksaan sebelum save.
  Seluruh pemeriksaan sebelum/sesudah load lulus pada build final.
- Semua berkas non-generated tetap di bawah 250 baris; `git diff --check`
  lulus. Tidak ada dependensi atau quality gate yang dihapus atau dilemahkan.

## Pengukuran performa

Pembanding adalah snapshot 1.3.0 dari commit
`b60f42bfbe85f20169e6983b03f4fbacb05688a1`, disimpan sebelum perubahan.
Kedua versi menjalankan workload identik pada proses terpisah secara berurutan:
80 panah Unstopable dengan Anti Friction, dinding batu, dan drop blok mati.
Accelerate tidak dipasang untuk memisahkan pengaruh optimasi dari perubahan laju.
Setiap kasus memiliki 50 putaran pemanasan dan 30 putaran pengukuran;
tiap putaran berisi 20 langkah pemrosesan hingga 80 panah yang masih hidup.

| Kasus | Versi | Median per langkah | P95 per langkah | Alokasi total | Blok hancur |
| --- | --- | ---: | ---: | ---: | ---: |
| Tanpa Follow | 1.3.0 | 1,2310 ms | 1,6839 ms | 211,197 MiB | 38.400 |
| Tanpa Follow | 1.4.0 | 1,0578 ms | 1,4756 ms | 153,171 MiB | 38.400 |
| Dengan Follow | 1.3.0 | 1,2052 ms | 1,7027 ms | 217,681 MiB | 24.660 |
| Dengan Follow | 1.4.0 | 1,2041 ms | 1,5768 ms | 173,149 MiB | 24.660 |

- Tanpa Follow: median turun 14,07%; alokasi turun 27,47%.
- Dengan Follow: median praktis tetap; alokasi turun 20,46%.
- Jumlah blok cocok pada setiap putaran antarversi. Jangan membandingkan dua
  kasus Follow sebagai beban identik: lintasan dan jumlah bloknya berbeda.
- Alokasi berarti jumlah memori sementara yang dibuat selama pengukuran,
  bukan penurunan kebutuhan RAM keseluruhan.
- Median paruh akhir kasus 1.4.0 tanpa Follow turun 10,35% dari paruh awal,
  sehingga hasil waktunya masih menunjukkan variasi selama proses berjalan.
  Ini observasi satu proses per versi, bukan jaminan peningkatan universal.
- CSV mengukur waktu pemrosesan panah manual, bukan total waktu tick server
  atau FPS. Setup dinding, pembaruan dunia yang ditunda, pengiriman jaringan,
  pencahayaan, serta rendering klien tidak masuk waktu tersebut.
- Rekaman CPU/alokasi JFR tersedia untuk kedua versi. Rekaman juga mencakup
  setup dan pemanasan; sampelnya harus dibaca terpisah dari pengukuran CSV.
- Pada stack yang mencakup `AbstractArrow.tick` dan `ArrowUnstopable.tryBreak`,
  sampel CPU terkait loot blok turun dari 42 menjadi 0; sampel alokasinya
  dari 126 menjadi 0. Ini mendukung bahwa pekerjaan loot blok dilewati saat
  drop mati. Hitungan sampel bersifat kualitatif, bukan persentase percepatan.

Workload dapat diulang pada salinan proyek tanpa folder `.git`:

```powershell
.\scripts\performance\profile.ps1 -BuildDirectory 'C:\lokasi\salinan-build'
.\scripts\performance\compare.ps1 -BaselineDirectory 'C:\lokasi\baseline\build\run\gameTest\bowpack-benchmark' -UpdatedDirectory 'C:\lokasi\updated\build\run\gameTest\bowpack-benchmark'
```

Skrip profil menambahkan fixture hanya selama benchmark di salinan build dan
mengembalikan manifest setelah selesai; suite pengujian proyek tetap utuh.

## Pemasangan dan bukti

- Installer `-ValidateOnly` dan pemasangan nyata berhasil. Profil tetap
  **Bow Pack 26.3**; folder mods kini memakai `bow-pack-1.4.0.jar`.
- JAR build lokal, JAR proyek, dan JAR terpasang memiliki SHA-256 identik:
  `42AE74892CDFA48A9A758B237792A61D63D8B3C1FDD9615F909530304F01EA31`.
- Cadangan versi sebelumnya:
  `.minecraft/bow-pack-backups/20260927-140446-d4931e`.
- Bukti berada di `build/verification/1.4.0/`: log build/pemasangan, hasil
  unit test XML, checksum sumber, ringkasan performa JSON, CSV, dan kedua JFR.

Klien Minecraft tidak dijalankan. Tampilan panah, FPS permainan, serta LAN
dua pemain masih perlu diperiksa dalam game; pengujian server tidak membuktikan
hasil visual atau peningkatan FPS.
