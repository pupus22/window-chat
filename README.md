# Window Chat Starter - Username + Nomor HP ID Kontak

Starter project Android chat sederhana memakai Firebase dan GitHub Actions.

## Sistem akun

- Username bebas dipilih user.
- Nomor HP dipakai sebagai ID pencocokan kontak otomatis.
- PIN dipakai sebagai password login ulang.
- Tidak memakai OTP SMS, jadi nomor HP belum diverifikasi 100%.

## Fitur

- Registrasi: nama, username, nomor HP, PIN.
- Login ulang: username + PIN.
- Auto-login setelah register/login pertama.
- Chat pribadi.
- Grup chat.
- Kirim teks.
- Kirim foto maksimal 5 MB.
- Kirim video maksimal 100 MB dan durasi 10 menit.
- Preview foto/video di chat.
- Centang pesan sederhana.
- Sinkron kontak HP: kontak otomatis muncul jika nomor itu sudah daftar Window.
- FCM token tersimpan untuk notifikasi.
- Cloud Functions contoh untuk push notification.
- GitHub Actions build APK tanpa Android Studio lokal.

## Firebase yang dipakai

- Firebase Authentication: Email/Password.
- Cloud Firestore.
- Firebase Storage.
- Firebase Cloud Messaging.
- Cloud Functions untuk notifikasi otomatis.

## Cara setup singkat

1. Buat Firebase project bernama `Window`.
2. Tambahkan Android app dengan package:

```text
com.window.chat
```

3. Download `google-services.json` dari Firebase.
4. Ganti file:

```text
app/google-services.json
```

5. Enable Firebase Authentication:

```text
Authentication → Sign-in method → Email/Password → Enable
```

6. Buat Firestore Database.
7. Publish `firestore.rules`.
8. Buat Storage.
9. Publish `storage.rules`.
10. Upload project ke GitHub.
11. GitHub → Actions → Build Window APK → Run workflow.
12. Download artifact `Window-debug-apk`.

## Cara kerja kontak otomatis

Saat user klik tombol `Kontak`, aplikasi akan:

1. Minta izin baca kontak.
2. Membaca nomor HP di kontak.
3. Normalisasi nomor ke format Indonesia, contoh `0813...` menjadi `62813...`.
4. Cek ke Firestore collection `users_by_phone`.
5. Jika nomor kontak sudah terdaftar, kontak muncul di daftar `Kontak Window`.
6. Klik kontak untuk mulai chat pribadi.

## Struktur data penting

```text
users/{uid}
users_by_username/{username}
users_by_phone/{phone_normalized}
chats/{chatId}
chats/{chatId}/messages/{messageId}
```

## Catatan keamanan

Karena tidak memakai OTP, nomor HP hanya dipakai sebagai ID pencocokan, bukan bukti kepemilikan nomor. Untuk percobaan/MVP tidak masalah, tetapi untuk aplikasi publik serius sebaiknya menambah verifikasi di tahap berikutnya.
