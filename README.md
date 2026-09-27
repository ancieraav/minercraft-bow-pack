# Bow Pack 🏹

Sepuluh enchantment untuk **Bow dan Crossbow**, untuk Minecraft Java **26.3** dengan Fabric Loader **0.19.5** dan Fabric API **0.161.0+26.3**. Versi mod: **1.2.0**.

## Memasang di Windows

1. Buka Minecraft Launcher setidaknya sekali. Tutup game dan launcher sebelum memasang.
2. Klik dua kali **Install Bow Pack.cmd**, atau jalankan dari PowerShell di folder proyek:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install.ps1
   ```

   Jika JAR berada di tempat lain, tambahkan `-ModJar "C:\lokasi\bow-pack-1.2.0.jar"`. Folder Minecraft khusus dapat dipilih dengan `-MinecraftDirectory "C:\lokasi\.minecraft"`.
3. Buka launcher, pilih instalasi **Bow Pack 26.3**, lalu **Play**. Aktifkan tampilan instalasi **Modded** jika profil tersembunyi. Launcher mengunduh komponen Minecraft dan Java yang diperlukan saat pertama kali bermain.
4. Uji dahulu di world baru. Profil menggunakan folder `.minecraft` yang sama sehingga world lama tetap terlihat.

Pemasang tidak membutuhkan administrator, tidak mengubah Java/PATH global, dan tidak mengedit world. Dependensi diunduh dari Fabric resmi dan diperiksa dengan SHA-256. Konfigurasi launcher dan berkas lama yang diganti dicadangkan di `.minecraft\bow-pack-backups\<waktu>`. Mod lain dipertahankan; versi lama Bow Pack dan Fabric API diganti untuk mencegah mod ganda. Kesalahan saat menyalin berkas memicu pemulihan otomatis dari cadangan.

Pemeriksaan lokal tanpa mengubah berkas:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\install.ps1 -ValidateOnly
```

Untuk LAN, pasang Bow Pack, Fabric Loader, dan Fabric API dengan versi yang sama pada host serta seluruh peserta. Host membuka world melalui **Open to LAN** seperti biasa. Dukungan koneksi aktual perlu diuji dengan dua pemain.

## Memakai enchantment

Buka tab Creative **Bow Pack** untuk mengambil salah satu dari 29 buku enchantment. Di anvil, letakkan Bow atau Crossbow di slot kiri dan buku di slot kanan, lalu ambil hasilnya. Cari `ricochet`, `friction`, atau `rainy` pada pencarian Creative. Katalog hanya berisi buku, tanpa senjata demo.

Dalam **Survival**, Multi, Front, Back, Diagonal, Bouncy, Breakthrough, dan Follow bisa diperoleh dari enchanting table pada buku/Bow/Crossbow, librarian, serta loot dan memancing yang menyediakan enchantment acak. Mob juga dapat muncul dengan perlengkapan ber-enchantment biasa ini dan memakai efeknya.

**Ricochet, Anti Friction, dan Rainy adalah enchantment harta karun**: didapat lewat librarian, loot, dan memancing, bukan enchanting table. Ketiganya memakai harga buku harta karun vanilla. Tidak setiap chest memiliki pilihan enchantment yang sama; hadiah khusus dengan daftar enchantment tetap tidak diubah.

Rak buku, lapis, XP, penggabungan level, penalti anvil, **Too Expensive**, dan grindstone mengikuti Minecraft. Trade Rebalance eksperimental tidak diubah; jalur librarian di atas memakai perdagangan vanilla standar. Loot yang sudah dibuka dan penawaran villager yang sudah tersimpan tidak diganti.

Dengan cheats aktif atau izin operator, gunakan command bawaan Minecraft untuk mendapatkan buku pada level maksimum:

```mcfunction
/give @s minecraft:enchanted_book[minecraft:stored_enchantments={"bowpack:multi_shot":4}] 1
/give @s minecraft:enchanted_book[minecraft:stored_enchantments={"bowpack:front_shot":4}] 1
/give @s minecraft:enchanted_book[minecraft:stored_enchantments={"bowpack:back_shot":5}] 1
/give @s minecraft:enchanted_book[minecraft:stored_enchantments={"bowpack:diagonal_shot":3}] 1
/give @s minecraft:enchanted_book[minecraft:stored_enchantments={"bowpack:ricochet_shot":4}] 1
/give @s minecraft:enchanted_book[minecraft:stored_enchantments={"bowpack:bouncy_shot":3}] 1
/give @s minecraft:enchanted_book[minecraft:stored_enchantments={"bowpack:anti_friction_shot":1}] 1
/give @s minecraft:enchanted_book[minecraft:stored_enchantments={"bowpack:breakthrough_shot":1}] 1
/give @s minecraft:enchanted_book[minecraft:stored_enchantments={"bowpack:rainy_shot":3}] 1
/give @s minecraft:enchanted_book[minecraft:stored_enchantments={"bowpack:follow_shot":1}] 1
```

Angka setelah ID enchantment dapat dikurangi untuk memilih level buku. Untuk langsung memasang enchantment pada Bow atau Crossbow yang sedang dipegang, gunakan `/enchant` bawaan Minecraft:

```mcfunction
/enchant @s bowpack:multi_shot 4
/enchant @s bowpack:front_shot 4
/enchant @s bowpack:back_shot 5
/enchant @s bowpack:diagonal_shot 3
/enchant @s bowpack:ricochet_shot 4
/enchant @s bowpack:bouncy_shot 3
/enchant @s bowpack:anti_friction_shot 1
/enchant @s bowpack:breakthrough_shot 1
/enchant @s bowpack:rainy_shot 3
/enchant @s bowpack:follow_shot 1
```

| Enchantment | Efek |
| --- | --- |
| **Multi Shot I–IV** | Tambah 1–4 panah di belakang panah utama, berjarak awal 0,4 block. |
| **Front Shot I–IV** | Tambah 1–4 panah paralel pada arah utama saja, berjarak 0,45 block. Panah tambahan bergantian kiri dan kanan. |
| **Back Shot I–V** | Tambah tepat 1–5 panah ke belakang, sejajar dan simetris dengan jarak 0,45 block. Arah kebalikan crosshair termasuk kemiringannya: depan-atas menghasilkan belakang-bawah. |
| **Diagonal Shot I** | 3 arah horizontal: −45°, 0°, +45°. |
| **Diagonal Shot II** | 5 arah horizontal: −90°, −45°, 0°, +45°, +90°. |
| **Diagonal Shot III** | 7 arah horizontal: −90°, −60°, −30°, 0°, +30°, +60°, +90°. |
| **Ricochet Shot I–III** | Setelah mengenai sasaran, panah menuju sasaran valid terdekat dalam radius 10/20/30 block dari benturan terbaru, maksimum 3/6/10 pantulan. |
| **Ricochet Shot IV** | Radius 35 block dan pantulan tanpa batas selama umur panah. Pantulan tampak berasal dari sasaran sebelumnya, tetapi kill tetap milik penembak asli. |
| **Bouncy Shot I–III** | Pantulan terhadap block mengikuti sudut datang, maksimum 10/20/30 kali per panah. Setelah itu panah menancap seperti biasa. |
| **Anti Friction Shot I** | Panah tidak dipengaruhi gravitasi atau hambatan saat terbang, termasuk hambatan udara dan air. |
| **Breakthrough Shot I** | Panah menembus lawan tanpa menambah damage tiap serangan. Jika digabung Ricochet, satu panah menembus dan satu lagi memantul saat ada sasaran pantulan. |
| **Rainy Shot I–III** | Setiap 10 detik selama penembak aktif mengenai lawan, munculkan 6/8/10 panah di atas sasaran. Panah diam dan membidik selama 2 detik, lalu melesat cepat tanpa gravitasi atau hambatan. Efek amunisi dan senjata dipertahankan; efek enchantment Bow Pack lain tidak ikut. Siklus berhenti setelah 21 detik tanpa serangan yang mengenai lawan. |
| **Follow Shot I** | Panah mengejar lawan dengan belokan maksimum 10° per tick. Berlaku juga untuk panah tambahan dari enchantment lain, kecuali Rainy Shot. |

**Ricochet tetap dipengaruhi gravitasi.** Panah diarahkan ke posisi sasaran saat pantulan, tanpa menaikkan arah untuk mengimbangi gravitasi. Panah bisa jatuh sebelum sampai, terhalang block, atau meleset. Follow dapat membelokkan panah menuju sasaran bergerak; Anti Friction menghilangkan gravitasi dan hambatan jika dipasang bersamaan.

Ricochet dapat menyasar mob dan pemain lain, selain penembak, selama aturan permainan mengizinkan damage. Sasaran yang baru terkena tidak dipilih langsung. Setelah mengenai sasaran lain, panah boleh kembali ke sasaran sebelumnya. Rangkaian berhenti jika tidak ada sasaran valid lain dalam radius.

Penunggang dan tunggangannya dihitung sebagai satu kelompok untuk pantulan berikutnya. Setelah mengenai salah satunya, panah mencari sasaran di luar kelompok itu dan keluar dari tubuh mereka tanpa tersangkut. Jika hanya kelompok tersebut yang tersisa, Ricochet berhenti.

Jika Ricochet dan Piercing bawaan dipasang bersamaan tanpa Breakthrough, **Ricochet diutamakan**: panah memantul ke sasaran berikutnya.

## Kombinasi dan damage

Semua sepuluh enchantment dapat digabungkan. Front hanya melebar pada arah utama, tidak menambah pasangan untuk Diagonal atau Back. Multi menggandakan setiap panah pada seluruh formasi, mengikuti arah masing-masing:

```text
Multi Shot II + Front Shot II = 9 panah
↑  ↑  ↑
↑  ↑  ↑
↑  ↑  ↑
```

Jumlah panah saat tembakan awal adalah `(level Multi + 1) × (1 + level Front + 2 × level Diagonal + level Back)`. Enchantment yang tidak dipasang dihitung level 0. Diagonal I + Front II menghasilkan 5 panah; ditambah Multi II menjadi 15 panah. Kombinasi maksimum menghasilkan **80 panah awal**; Breakthrough + Ricochet dan Rainy dapat menghasilkan panah susulan. Setiap panah bisa memberi damage sendiri; armor, shield, dan aturan perlindungan tetap berlaku.

Panah tambahan membawa efek tembakan, termasuk tipped dan spectral arrows. Ammo dan durability mengikuti satu tembakan biasa. Panah tambahan tidak dapat dipungut. Pantulan Bouncy tidak mengisi ulang batasnya. Firework dari Crossbow tetap berperilaku biasa.

**Multishot bawaan Minecraft dinonaktifkan**, termasuk spread dari enchantment lama. Gunakan `bowpack:multi_shot` sebagai penggantinya.

Front V dari versi lama tetap tersimpan, tetapi tembakannya dibatasi menjadi Front IV. Buku Front V yang diletakkan di slot kanan anvil menghasilkan level IV. Dua buku Front IV tidak menghasilkan level V. Command `/give` dengan komponen mentah tetap mengikuti kemampuan vanilla; batas efek pada senjata tetap diberlakukan.

### Merakit semua enchantment di anvil Survival

Mulai dengan senjata dan sepuluh buku level maksimum yang belum pernah dikerjakan di anvil. Pada setiap langkah, bahan pertama masuk slot kiri dan bahan kedua masuk slot kanan:

1. Pasangkan Multi IV ke senjata.
2. Gabungkan Front IV + Back V menjadi buku A, lalu Diagonal III + Ricochet IV menjadi buku B.
3. Gabungkan buku A + buku B, lalu pasangkan hasilnya ke senjata.
4. Gabungkan Anti Friction I + Breakthrough I menjadi buku C, lalu Rainy III + Follow I menjadi buku D.
5. Gabungkan buku C + buku D menjadi buku E, lalu Bouncy III + buku E menjadi buku F.
6. Pasangkan buku F ke senjata dari langkah 3.

Urutan ini mempertahankan batas biaya Survival. Buku/senjata yang sudah sering dikerjakan dapat tetap terkena **Too Expensive**. Untuk menaikkan level, gabungkan dua buku berlevel sama sampai batas enchantment; pengerjaan itu juga menambah penalti vanilla.

## Umur panah

Semua panah dengan efek Bow Pack memiliki batas **600 tick**, sekitar **30 detik permainan**, sejak ditembakkan. Ricochet, Bouncy, save/load, dan chunk unload tidak mengulang waktunya. Waktu berhenti saat simulasi dijeda atau world ditutup. Panah yang sudah kedaluwarsa saat chunk tidak aktif dibuang pada tick pertamanya setelah dimuat; mod tidak memaksa chunk tetap aktif.

- Panah tambahan yang sudah menancap dibersihkan setelah 100 tick, sekitar 5 detik.
- Panah di udara yang kecepatan dan perpindahannya di bawah 0,02 block/tick selama 40 tick berturut-turut dibersihkan lebih awal.
- Panah asli yang menancap tetap bisa dipungut sampai batas umurnya; pickup mengikuti izin vanilla.
- Panah tanpa efek Bow Pack tetap memakai umur vanilla.

Panah custom versi lama tanpa waktu kedaluwarsa mendapat satu masa transisi 600 tick saat pertama dimuat. Sesudah itu batasnya tersimpan dan tidak diulang.

## Build dan verifikasi

Jalankan dari folder proyek:

```powershell
.\scripts\build.ps1
```

Tes integrasi memakai mesin Minecraft asli dan dapat dijalankan dengan `./scripts/build.ps1 runGameTest`.
Jika OneDrive mengunci folder hasil kompilasi, jalankan build dari salinan proyek di folder lokal di luar OneDrive.

Skrip mencari JDK **25** melalui `JAVA_HOME`, `javac` pada PATH, lalu runtime bawaan Minecraft Launcher. Perubahan lingkungan hanya berlaku selama build dan dikembalikan setelah selesai. Tidak ada pemasangan Java atau perubahan pengaturan global. Task bawaan adalah `test build`; untuk task tertentu gunakan, misalnya, `.\scripts\build.ps1 test`.

Hasil: `build\libs\bow-pack-1.2.0.jar`. Fabric API dibutuhkan saat menjalankan game. Build dan pengujian otomatis tidak menggantikan pemeriksaan visual dalam game.

Pemeriksaan permainan yang perlu dilakukan sebelum memakai world utama:

- Tembakkan kedua senjata pada setiap level; hitung formasi dan uji damage beberapa panah pada sasaran yang sama.
- Uji Ricochet I–IV pada radius dan batas pantulan tiap level, sasaran bergerak, tembakan yang jatuh sebelum sampai, serta kredit kill pada level IV.
- Uji Bouncy pada lantai, dinding, langit-langit, serta batas 10/20/30 pantulan. Gabungkan dengan Anti Friction.
- Uji Breakthrough bersama Ricochet dan Follow, termasuk keadaan tanpa sasaran pantulan.
- Uji Rainy I–III: jumlah panah, jeda dua detik, arah ujung panah, kecepatan, siklus 10 detik, dan penghentian setelah 21 detik tanpa hit.
- Uji tembakan dekat dinding, tipped/spectral arrows, ammo, durability, dan larangan mengambil panah tambahan.
- Uji Back sambil membidik mendatar, ke atas, dan ke bawah; pastikan Front tidak menggandakan Diagonal.
- Simpan dan buka ulang world saat panah masih aktif; coba kombinasi 80 panah, batas 30 detik, dan permainan LAN.
- Coba enchanting table, buku librarian/loot/memancing, penggabungan anvil, dan grindstone.

[VERIFICATION-1.2.0.md](VERIFICATION-1.2.0.md) mencatat hasil build, pengujian, dan pemasangan versi ini. [VERIFICATION.md](VERIFICATION.md) tetap menyimpan riwayat versi 1.1.0.

Referensi pemasangan: [Fabric untuk Minecraft 26.3](https://fabricmc.net/2026/09/15/263.html), [metadata profil resmi](https://meta.fabricmc.net/v2/versions/loader/26.3/0.19.5/profile/json), dan [pemasang profil Fabric](https://github.com/FabricMC/fabric-installer/blob/master/src/main/java/net/fabricmc/installer/client/ProfileInstaller.java).
