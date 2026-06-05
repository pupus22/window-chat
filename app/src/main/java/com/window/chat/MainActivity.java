package com.window.chat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    // Window Terminal Theme
    private static final int BLUE = Color.rgb(0, 240, 110);          // neon terminal green
    private static final int DARK_BLUE = Color.rgb(0, 46, 24);       // dark green header
    private static final int BG = Color.rgb(0, 8, 4);                // near black
    private static final int TERMINAL_PANEL = Color.rgb(3, 18, 10);
    private static final int TERMINAL_PANEL_2 = Color.rgb(6, 30, 17);
    private static final int TERMINAL_LINE = Color.rgb(0, 96, 48);
    private static final int TERMINAL_TEXT = Color.rgb(185, 255, 205);
    private static final int TERMINAL_DIM = Color.rgb(88, 158, 104);
    private static final int REQ_PICK_MEDIA = 1001;
    private static final int REQ_CONTACTS = 1002;
    private static final int REQ_TAKE_PHOTO = 1003;
    private static final int REQ_TAKE_VIDEO = 1004;
    private static final int REQ_CAMERA_PERMISSION = 1005;
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final long MAX_VIDEO_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_VIDEO_DURATION_MS = 10L * 60L * 1000L;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private LinearLayout root;
    private String currentUid;
    private String currentName = "Saya";
    private String currentUsername = "";
    private String currentPhone = "";
    private String activeChatId;
    private String activeChatTitle;
    private String pendingMediaType;
    private String pendingCameraType;
    private Uri cameraMediaUri;
    private boolean activeChatIsGroup = false;
    private ListenerRegistration chatsListener;
    private ListenerRegistration messagesListener;
    private ListenerRegistration chatStatusListener;
    private ChatsAdapter chatsAdapter;
    private MessagesAdapter messagesAdapter;
    private EditText messageInput;
    private LinearLayout replyPreviewBar;
    private TextView replyPreviewText;
    private String replyingToMessageId;
    private String replyingToSenderName;
    private String replyingToText;
    private TextView typingView;
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private Runnable typingStopRunnable;
    private boolean lastTypingSent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        createNotificationChannel();
        askNotificationPermission();
        showLoading("Membuka Window...");

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showWelcomeScreen();
        } else {
            currentUid = user.getUid();
            loadProfileThenHome();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    getString(R.string.default_notification_channel_id),
                    "Pesan Window",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifikasi pesan masuk Window");
            manager.createNotificationChannel(channel);
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 50);
        }
    }

    private void setRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
    }

    private TextView tv(String text, int sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.MONOSPACE, style);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        return t;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setTextColor(TERMINAL_TEXT);
        e.setHintTextColor(TERMINAL_DIM);
        e.setTypeface(Typeface.MONOSPACE);
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        e.setBackground(terminalBox(TERMINAL_PANEL, 1, TERMINAL_LINE, 8));
        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(BLUE);
        b.setTextSize(15);
        b.setTypeface(Typeface.MONOSPACE, 1);
        b.setBackground(terminalBox(TERMINAL_PANEL_2, 1, BLUE, 6));
        b.setAllCaps(false);
        return b;
    }

    private void showLoading(String text) {
        setRoot();
        root.setGravity(Gravity.CENTER);
        ProgressBar pb = new ProgressBar(this);
        TextView label = tv(text, 16, TERMINAL_DIM, 0);
        label.setGravity(Gravity.CENTER);
        root.addView(pb);
        root.addView(label);
    }

    private void showWelcomeScreen() {
        setRoot();
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(BG);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.window_logo);
        logo.setBackground(terminalBox(TERMINAL_PANEL, 1, BLUE, 64));
        logo.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView title = tv("Window", 34, BLUE, 1);
        title.setGravity(Gravity.CENTER);
        TextView sub = tv("secure terminal chat // no otp sms", 14, TERMINAL_TEXT, 0);
        sub.setGravity(Gravity.CENTER);
        Button register = button("Daftar Akun Baru");
        Button login = button("Masuk Akun Lama");
        register.setBackground(terminalBox(TERMINAL_PANEL_2, 1, BLUE, 6));
        register.setTextColor(BLUE);
        login.setBackground(terminalBox(TERMINAL_PANEL, 1, TERMINAL_LINE, 6));
        login.setTextColor(BLUE);

        root.addView(logo, new LinearLayout.LayoutParams(dp(112), dp(112)));
        root.addView(title);
        root.addView(sub);
        addSpace(root, 70);
        root.addView(register, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        addSpace(root, 10);
        root.addView(login, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        root.addView(tv("> username + phone id + pin // no sms cost", 13, TERMINAL_DIM, 0));
        register.setOnClickListener(v -> showRegisterScreen());
        login.setOnClickListener(v -> showLoginScreen());
    }

    private void showRegisterScreen() {
        setRoot();
        root.setPadding(dp(20), dp(30), dp(20), dp(20));
        TextView title = tv("Daftar Window", 24, BLUE, 1);
        title.setGravity(Gravity.CENTER);
        TextView desc = tv("Buat username, isi nomor HP sebagai ID kontak, dan PIN. Nomor tidak diverifikasi OTP.", 14, TERMINAL_DIM, 0);
        desc.setGravity(Gravity.CENTER);
        EditText name = input("Nama tampilan, contoh: Angga");
        EditText username = input("Username, contoh: angga_sby");
        username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        EditText phone = input("Nomor HP, contoh: 081358578824");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText pin = input("PIN minimal 6 angka");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        Button save = button("Daftar");
        Button back = button("Kembali");
        back.setBackground(terminalBox(TERMINAL_PANEL, 1, TERMINAL_LINE, 6));
        back.setTextColor(TERMINAL_TEXT);

        root.addView(title);
        root.addView(desc);
        addSpace(root, 18);
        root.addView(name, lpMatchWrap());
        addSpace(root, 10);
        root.addView(username, lpMatchWrap());
        addSpace(root, 10);
        root.addView(phone, lpMatchWrap());
        addSpace(root, 10);
        root.addView(pin, lpMatchWrap());
        addSpace(root, 12);
        root.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        addSpace(root, 8);
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        save.setOnClickListener(v -> registerWithUsername(name.getText().toString(), username.getText().toString(), phone.getText().toString(), pin.getText().toString()));
        back.setOnClickListener(v -> showWelcomeScreen());
    }

    private void showLoginScreen() {
        setRoot();
        root.setPadding(dp(20), dp(30), dp(20), dp(20));
        TextView title = tv("Masuk Window", 24, BLUE, 1);
        title.setGravity(Gravity.CENTER);
        TextView desc = tv("Masuk pakai username dan PIN yang sudah dibuat.", 14, TERMINAL_DIM, 0);
        desc.setGravity(Gravity.CENTER);
        EditText username = input("Username");
        username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        EditText pin = input("PIN");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        Button login = button("Masuk");
        Button back = button("Kembali");
        back.setBackground(terminalBox(TERMINAL_PANEL, 1, TERMINAL_LINE, 6));
        back.setTextColor(TERMINAL_TEXT);

        root.addView(title);
        root.addView(desc);
        addSpace(root, 18);
        root.addView(username, lpMatchWrap());
        addSpace(root, 10);
        root.addView(pin, lpMatchWrap());
        addSpace(root, 12);
        root.addView(login, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        addSpace(root, 8);
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        login.setOnClickListener(v -> loginWithUsername(username.getText().toString(), pin.getText().toString()));
        back.setOnClickListener(v -> showWelcomeScreen());
    }

    private void registerWithUsername(String nameRaw, String usernameRaw, String phoneRaw, String pinRaw) {
        String n = nameRaw.trim();
        String username = cleanUsername(usernameRaw);
        String phone = normalizePhone(phoneRaw);
        String pin = pinRaw.trim();
        if (n.length() < 2) {
            toast("Nama terlalu pendek");
            return;
        }
        if (!isValidUsername(username)) {
            toast("Username 4-20 karakter, hanya huruf kecil, angka, dan garis bawah");
            return;
        }
        if (!isValidPhone(phone)) {
            toast("Nomor HP belum benar. Contoh: 081358578824");
            return;
        }
        if (pin.length() < 6) {
            toast("PIN minimal 6 angka");
            return;
        }
        showLoading("Mendaftarkan akun...");
        db.collection("users_by_phone").document(phone).get().addOnSuccessListener(phoneDoc -> {
            if (phoneDoc.exists()) {
                showRegisterScreen();
                toast("Nomor HP sudah dipakai akun lain");
                return;
            }
            auth.createUserWithEmailAndPassword(emailForUsername(username), pin)
                    .addOnSuccessListener(result -> {
                        currentUid = result.getUser().getUid();
                        currentName = n;
                        currentUsername = username;
                        currentPhone = phone;
                        saveProfile();
                    })
                    .addOnFailureListener(e -> {
                        showRegisterScreen();
                        String msg = e.getMessage() == null ? "Gagal daftar" : e.getMessage();
                        if (msg.toLowerCase(Locale.ROOT).contains("email address is already")) msg = "Username sudah dipakai";
                        toast(msg);
                    });
        }).addOnFailureListener(e -> {
            showRegisterScreen();
            toast("Gagal cek nomor HP: " + e.getMessage());
        });
    }

    private void loginWithUsername(String usernameRaw, String pinRaw) {
        String username = cleanUsername(usernameRaw);
        String pin = pinRaw.trim();
        if (!isValidUsername(username)) {
            toast("Username belum benar");
            return;
        }
        if (pin.length() < 6) {
            toast("PIN minimal 6 angka");
            return;
        }
        showLoading("Masuk akun...");
        auth.signInWithEmailAndPassword(emailForUsername(username), pin)
                .addOnSuccessListener(result -> {
                    currentUid = result.getUser().getUid();
                    currentUsername = username;
                    loadProfileThenHome();
                })
                .addOnFailureListener(e -> {
                    showLoginScreen();
                    toast("Username atau PIN salah");
                });
    }

    private void showNameScreen() {
        setRoot();
        root.setPadding(dp(20), dp(30), dp(20), dp(20));
        TextView title = tv("Edit Nama", 24, BLUE, 1);
        title.setGravity(Gravity.CENTER);
        EditText name = input("Nama tampilan");
        name.setText(currentName);
        Button save = button("Simpan");
        Button cancel = button("Batal");
        cancel.setBackground(terminalBox(TERMINAL_PANEL, 1, TERMINAL_LINE, 6));
        cancel.setTextColor(TERMINAL_TEXT);
        root.addView(title);
        root.addView(tv("Nama ini akan terlihat di chat dan grup.", 14, TERMINAL_DIM, 0));
        addSpace(root, 18);
        root.addView(name, lpMatchWrap());
        addSpace(root, 12);
        root.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        addSpace(root, 8);
        root.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.length() < 2) {
                toast("Nama terlalu pendek");
                return;
            }
            currentName = n;
            saveProfile();
        });
        cancel.setOnClickListener(v -> showHome());
    }

    private void saveProfile() {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return;
        currentUid = u.getUid();
        if (currentUsername == null || currentUsername.trim().isEmpty()) {
            currentUsername = usernameFromEmail(value(u.getEmail(), ""));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("uid", currentUid);
        data.put("name", currentName);
        data.put("username", currentUsername);
        data.put("phone", currentPhone);
        data.put("updatedAt", FieldValue.serverTimestamp());
        if (currentUsername != null && !currentUsername.isEmpty()) {
            data.put("createdAt", FieldValue.serverTimestamp());
        }
        db.collection("users").document(currentUid).set(data, SetOptions.merge()).addOnSuccessListener(x -> {
            Map<String, Object> p = new HashMap<>();
            p.put("uid", currentUid);
            p.put("name", currentName);
            p.put("username", currentUsername);
            p.put("phone", currentPhone);
            db.collection("users_by_username").document(currentUsername).set(p, SetOptions.merge());
            if (isValidPhone(currentPhone)) {
                db.collection("users_by_phone").document(currentPhone).set(p, SetOptions.merge());
            }
            saveFcmToken();
            showHome();
        }).addOnFailureListener(e -> toast("Gagal simpan profil: " + e.getMessage()));
    }

    private void loadProfileThenHome() {
        db.collection("users").document(currentUid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                currentName = value(doc.getString("name"), "Saya");
                currentUsername = value(doc.getString("username"), usernameFromEmail(value(auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getEmail(), "")));
                currentPhone = value(doc.getString("phone"), "");
                saveFcmToken();
                showHome();
            } else {
                currentUsername = usernameFromEmail(value(auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getEmail(), ""));
                currentName = "Saya";
                currentPhone = "";
                showNameScreen();
            }
        }).addOnFailureListener(e -> showNameScreen());
    }

    private void saveFcmToken() {
        if (currentUid == null) return;
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> db.collection("users").document(currentUid).set(Collections.singletonMap("fcmToken", token), SetOptions.merge()));
    }

    private void showHome() {
        if (activeChatId != null) setTyping(false);
        if (messagesListener != null) messagesListener.remove();
        if (chatsListener != null) chatsListener.remove();
        if (chatStatusListener != null) chatStatusListener.remove();
        chatStatusListener = null;
        activeChatId = null;
        activeChatIsGroup = false;
        clearReplyStateOnly();
        setRoot();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(10), dp(8));
        header.setBackgroundColor(DARK_BLUE);
        TextView title = tv("Window", 24, BLUE, 1);
        Button profile = new Button(this);
        profile.setText("Saya");
        profile.setAllCaps(false);
        profile.setTextColor(BLUE);
        profile.setBackground(terminalBox(TERMINAL_PANEL, 1, TERMINAL_LINE, 6));
        profile.setOnClickListener(v -> showProfileDialog());
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(profile);
        root.addView(header);

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(8), dp(8), dp(8), dp(8));
        actions.setBackgroundColor(BG);

        Button newChat = button("+ Chat");
        Button newGroup = button("+ Grup");

        actions.addView(newChat, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(newGroup, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(actions);

        RecyclerView recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        chatsAdapter = new ChatsAdapter();
        recycler.setAdapter(chatsAdapter);
        root.addView(recycler, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        attachSwipeDeleteToChatList(recycler);

        newChat.setOnClickListener(v -> showStartPrivateDialog());
        newGroup.setOnClickListener(v -> showCreateGroupDialog());

        chatsListener = db.collection("chats")
                .whereArrayContains("members", currentUid)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        toast("Gagal ambil chat: " + e.getMessage());
                        return;
                    }
                    List<DocumentSnapshot> list = snap == null ? new ArrayList<>() : snap.getDocuments();
                    chatsAdapter.submit(list);
                });
    }


    private void attachSwipeDeleteToChatList(RecyclerView recycler) {
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                int pos = holder.getAdapterPosition();
                if (chatsAdapter == null || pos < 0 || pos >= chatsAdapter.getItemCount()) return;
                DocumentSnapshot d = chatsAdapter.getItem(pos);
                chatsAdapter.notifyItemChanged(pos); // balikin row supaya tidak hilang sebelum user konfirmasi
                confirmDeleteChatFromHome(d);
            }
        });
        helper.attachToRecyclerView(recycler);
    }

    private void showChatOptionsDialog() {
        if (activeChatId == null) return;
        String[] items = {"Clear chat"};
        new AlertDialog.Builder(this)
                .setTitle(value(activeChatTitle, "Chat"))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) confirmClearCurrentChat();
                })
                .show();
    }

    private void confirmClearCurrentChat() {
        new AlertDialog.Builder(this)
                .setTitle("Clear chat?")
                .setMessage("Semua riwayat pesan di chat ini akan dihapus permanen dari Firebase untuk semua member. Chat room tetap ada, tapi pesannya kosong.")
                .setPositiveButton("Clear", (d, w) -> clearCurrentChatHistory())
                .setNegativeButton("Batal", null)
                .show();
    }

    private void clearCurrentChatHistory() {
        if (activeChatId == null) return;
        String chatId = activeChatId;
        String title = value(activeChatTitle, "Chat");
        boolean isGroup = activeChatIsGroup;
        showLoading("Menghapus riwayat chat...");
        deleteAllMessagesInChat(chatId, () -> {
            Map<String, Object> update = new HashMap<>();
            update.put("lastMessage", "");
            update.put("lastSenderId", "");
            update.put("updatedAt", FieldValue.serverTimestamp());
            db.collection("chats").document(chatId).set(update, SetOptions.merge())
                    .addOnSuccessListener(x -> {
                        toast("Riwayat chat sudah dihapus");
                        activeChatIsGroup = isGroup;
                        openChat(chatId, title);
                    })
                    .addOnFailureListener(e -> {
                        openChat(chatId, title);
                        toast("Gagal update chat: " + e.getMessage());
                    });
        }, e -> {
            activeChatIsGroup = isGroup;
            openChat(chatId, title);
            toast("Gagal clear chat: " + e.getMessage());
        });
    }

    private void confirmDeleteChatFromHome(DocumentSnapshot d) {
        String title = chatTitle(d);
        String chatId = d.getId();
        new AlertDialog.Builder(this)
                .setTitle("Hapus chat?")
                .setMessage("Chat \"" + title + "\" akan dihapus permanen dari halaman utama dan dari Firebase, termasuk semua pesan di dalamnya. Ini juga hilang untuk member lain.")
                .setPositiveButton("Hapus", (dialog, which) -> deleteWholeChat(chatId))
                .setNegativeButton("Batal", null)
                .show();
    }

    private void deleteWholeChat(String chatId) {
        showLoading("Menghapus chat...");
        deleteAllMessagesInChat(chatId, () -> {
            db.collection("chats").document(chatId).delete()
                    .addOnSuccessListener(x -> {
                        toast("Chat sudah dihapus");
                        showHome();
                    })
                    .addOnFailureListener(e -> {
                        showHome();
                        toast("Gagal hapus chat: " + e.getMessage());
                    });
        }, e -> {
            showHome();
            toast("Gagal hapus pesan: " + e.getMessage());
        });
    }

    private interface DoneCallback { void onDone(); }
    private interface ErrorCallback { void onError(Exception e); }

    private void deleteAllMessagesInChat(String chatId, DoneCallback done, ErrorCallback fail) {
        db.collection("chats").document(chatId).collection("messages")
                .limit(300)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || snap.isEmpty()) {
                        done.onDone();
                        return;
                    }

                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        batch.delete(doc.getReference());
                    }

                    batch.commit()
                            .addOnSuccessListener(x -> deleteAllMessagesInChat(chatId, done, fail))
                            .addOnFailureListener(fail::onError);
                })
                .addOnFailureListener(fail::onError);
    }

    private void showProfileDialog() {
        String msg = "Nama: " + currentName + "\nUsername: @" + currentUsername + "\nNomor ID: " + displayPhone(currentPhone) + "\n\nNomor dipakai untuk pencocokan kontak. Jika keluar, bisa masuk lagi pakai username + PIN.";
        new AlertDialog.Builder(this)
                .setTitle("Profil Saya")
                .setMessage(msg)
                .setPositiveButton("Edit Nama", (d, w) -> showNameScreen())
                .setNegativeButton("Keluar", (d, w) -> {
                    auth.signOut();
                    currentUid = null;
                    currentUsername = "";
                    showWelcomeScreen();
                })
                .setNeutralButton("Tutup", null)
                .show();
    }

    private void showStartPrivateDialog() {
        EditText username = input("Username atau nomor HP teman");
        username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Mulai Chat Pribadi")
                .setView(username)
                .setPositiveButton("Mulai", (d, w) -> startPrivateChat(username.getText().toString()))
                .setNegativeButton("Batal", null)
                .show();
    }

    private void startPrivateChat(String rawIdentifier) {
        String identifier = cleanIdentifier(rawIdentifier);
        if (identifier.isEmpty()) {
            toast("Isi username atau nomor HP teman");
            return;
        }
        findUserByIdentifier(identifier, doc -> {
            if (doc == null || !doc.exists()) {
                toast("Akun belum terdaftar di Window");
                return;
            }
            String otherUid = doc.getString("uid");
            String otherName = value(doc.getString("name"), "Teman");
            if (currentUid.equals(otherUid)) {
                toast("Itu akun Mas sendiri");
                return;
            }
            createOrOpenPrivateChat(otherUid, otherName);
        });
    }

    private void createOrOpenPrivateChat(String otherUid, String otherName) {
        String chatId = privateChatId(currentUid, otherUid);
        Map<String, Object> names = new HashMap<>();
        names.put(currentUid, currentName);
        names.put(otherUid, otherName);
        Map<String, Object> chat = new HashMap<>();
        chat.put("type", "private");
        chat.put("members", Arrays.asList(currentUid, otherUid));
        chat.put("memberNames", names);
        chat.put("lastMessage", "");
        chat.put("updatedAt", FieldValue.serverTimestamp());
        db.collection("chats").document(chatId).set(chat, SetOptions.merge()).addOnSuccessListener(x -> { activeChatIsGroup = false; openChat(chatId, otherName); });
    }

    private interface UserFoundCallback { void onFound(DocumentSnapshot doc); }

    private void findUserByIdentifier(String raw, UserFoundCallback cb) {
        String phone = normalizePhone(raw);
        if (isValidPhone(phone)) {
            db.collection("users_by_phone").document(phone).get()
                    .addOnSuccessListener(cb::onFound)
                    .addOnFailureListener(e -> { toast("Gagal cari nomor: " + e.getMessage()); cb.onFound(null); });
            return;
        }
        String username = cleanUsername(raw);
        if (!isValidUsername(username)) {
            toast("Username/nomor belum benar");
            cb.onFound(null);
            return;
        }
        db.collection("users_by_username").document(username).get()
                .addOnSuccessListener(cb::onFound)
                .addOnFailureListener(e -> { toast("Gagal cari username: " + e.getMessage()); cb.onFound(null); });
    }

    private void showCreateGroupDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        EditText groupName = input("Nama grup");
        EditText usernames = input("Username/nomor anggota, pisahkan koma");
        usernames.setSingleLine(false);
        usernames.setMinLines(2);
        usernames.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        box.addView(groupName, lpMatchWrap());
        addSpace(box, 8);
        box.addView(usernames, lpMatchWrap());
        new AlertDialog.Builder(this)
                .setTitle("Buat Grup")
                .setView(box)
                .setPositiveButton("Buat", (d, w) -> createGroup(groupName.getText().toString(), usernames.getText().toString()))
                .setNegativeButton("Batal", null)
                .show();
    }

    private void createGroup(String nameRaw, String usernameListRaw) {
        String groupName = nameRaw.trim();
        if (groupName.length() < 2) {
            toast("Nama grup terlalu pendek");
            return;
        }
        List<String> identifiers = new ArrayList<>();
        for (String u : usernameListRaw.split(",")) {
            String clean = cleanIdentifier(u);
            if (!clean.isEmpty() && !identifiers.contains(clean)) identifiers.add(clean);
        }
        List<String> members = new ArrayList<>();
        Map<String, Object> names = new HashMap<>();
        members.add(currentUid);
        names.put(currentUid, currentName);
        if (identifiers.isEmpty()) {
            finishCreateGroup(groupName, members, names);
            return;
        }
        AtomicInteger left = new AtomicInteger(identifiers.size());
        for (String identifier : identifiers) {
            findUserByIdentifier(identifier, doc -> {
                if (doc != null && doc.exists()) {
                    String uid = doc.getString("uid");
                    if (uid != null && !members.contains(uid)) {
                        members.add(uid);
                        names.put(uid, value(doc.getString("name"), "Anggota"));
                    }
                }
                if (left.decrementAndGet() == 0) finishCreateGroup(groupName, members, names);
            });
        }
    }

    private void finishCreateGroup(String groupName, List<String> members, Map<String, Object> names) {
        Map<String, Object> chat = new HashMap<>();
        chat.put("type", "group");
        chat.put("name", groupName);
        chat.put("members", members);
        chat.put("memberNames", names);
        chat.put("createdBy", currentUid);
        chat.put("lastMessage", "Grup dibuat");
        chat.put("createdAt", FieldValue.serverTimestamp());
        chat.put("updatedAt", FieldValue.serverTimestamp());
        db.collection("chats").add(chat).addOnSuccessListener(ref -> { activeChatIsGroup = true; openChat(ref.getId(), groupName); });
    }

    private void openChat(String chatId, String title) {
        if (activeChatId != null) setTyping(false);
        if (chatsListener != null) chatsListener.remove();
        if (messagesListener != null) messagesListener.remove();
        if (chatStatusListener != null) chatStatusListener.remove();
        chatStatusListener = null;
        activeChatId = chatId;
        activeChatTitle = title;
        clearReplyStateOnly();
        setRoot();

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(6), dp(8), dp(6));
        header.setBackgroundColor(DARK_BLUE);
        Button back = new Button(this);
        back.setText("←");
        back.setTextColor(BLUE);
        back.setBackground(terminalBox(TERMINAL_PANEL, 1, TERMINAL_LINE, 6));
        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        titleStack.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = tv(title, 19, BLUE, 1);
        titleView.setPadding(dp(8), 0, dp(8), 0);
        typingView = tv("", 12, BLUE, 0);
        typingView.setPadding(dp(8), 0, dp(8), 0);
        typingView.setVisibility(View.GONE);
        titleStack.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        titleStack.addView(typingView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button menu = new Button(this);
        menu.setText("⋮");
        menu.setAllCaps(false);
        menu.setTextColor(BLUE);
        menu.setBackground(terminalBox(TERMINAL_PANEL, 1, BLUE, 6));

        header.addView(back, new LinearLayout.LayoutParams(dp(56), dp(48)));
        header.addView(titleStack, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(menu, new LinearLayout.LayoutParams(dp(52), dp(48)));
        root.addView(header);
        back.setOnClickListener(v -> showHome());
        menu.setOnClickListener(v -> showChatOptionsDialog());
        listenTypingStatus();

        RecyclerView recycler = new RecyclerView(this);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        recycler.setLayoutManager(lm);
        messagesAdapter = new MessagesAdapter();
        recycler.setAdapter(messagesAdapter);
        root.addView(recycler, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout inputArea = new LinearLayout(this);
        inputArea.setOrientation(LinearLayout.VERTICAL);
        inputArea.setBackgroundColor(BG);

        replyPreviewBar = new LinearLayout(this);
        replyPreviewBar.setOrientation(LinearLayout.HORIZONTAL);
        replyPreviewBar.setGravity(Gravity.CENTER_VERTICAL);
        replyPreviewBar.setPadding(dp(10), dp(6), dp(8), dp(6));
        replyPreviewBar.setBackgroundColor(BG);
        replyPreviewBar.setVisibility(View.GONE);

        replyPreviewText = tv("", 13, TERMINAL_DIM, 0);
        replyPreviewText.setPadding(dp(10), dp(6), dp(10), dp(6));
        replyPreviewText.setBackground(terminalBox(TERMINAL_PANEL_2, 1, TERMINAL_LINE, 8));
        Button cancelReply = new Button(this);
        cancelReply.setText("×");
        cancelReply.setAllCaps(false);
        cancelReply.setTextColor(BLUE);
        cancelReply.setBackgroundColor(Color.TRANSPARENT);
        cancelReply.setOnClickListener(v -> clearReply());
        replyPreviewBar.addView(replyPreviewText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        replyPreviewBar.addView(cancelReply, new LinearLayout.LayoutParams(dp(48), dp(44)));
        inputArea.addView(replyPreviewBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        bar.setBackgroundColor(BG);
        Button attach = new Button(this);
        attach.setText("📎");
        attach.setAllCaps(false);
        attach.setTextColor(BLUE);
        attach.setTypeface(Typeface.MONOSPACE, 1);
        attach.setBackground(terminalBox(TERMINAL_PANEL, 1, TERMINAL_LINE, 6));
        messageInput = input("Ketik pesan...");
        messageInput.setSingleLine(false);
        messageInput.setMinLines(1);
        messageInput.setMaxLines(4);
        messageInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        messageInput.setHorizontallyScrolling(false);
        messageInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        messageInput.setImeOptions(EditorInfo.IME_ACTION_NONE);
        messageInput.setMinHeight(dp(48));
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                handleTypingChanged(value != null && value.toString().trim().length() > 0);
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
        Button send = button("➤");
        bar.addView(attach, new LinearLayout.LayoutParams(dp(56), dp(48)));
        bar.addView(messageInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(send, new LinearLayout.LayoutParams(dp(64), dp(48)));
        inputArea.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(inputArea);
        protectBottomFromNavigationBar(inputArea, 6);

        send.setOnClickListener(v -> sendTextMessage());
        attach.setOnClickListener(v -> showAttachDialog());

        messagesListener = db.collection("chats").document(chatId).collection("messages")
                .orderBy("createdAt")
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        toast("Gagal ambil pesan: " + e.getMessage());
                        return;
                    }
                    List<DocumentSnapshot> docs = snap == null ? new ArrayList<>() : snap.getDocuments();
                    messagesAdapter.submit(docs);
                    recycler.scrollToPosition(Math.max(docs.size() - 1, 0));
                    markMessagesRead(docs);
                });
    }

    @SuppressWarnings("unchecked")
    private void listenTypingStatus() {
        if (activeChatId == null || currentUid == null) return;
        if (chatStatusListener != null) chatStatusListener.remove();
        chatStatusListener = db.collection("chats").document(activeChatId).addSnapshotListener((snap, e) -> {
            if (typingView == null || snap == null || !snap.exists()) return;
            Map<String, Object> typing = (Map<String, Object>) snap.get("typing");
            boolean otherTyping = false;
            if (typing != null) {
                for (String uid : typing.keySet()) {
                    Object val = typing.get(uid);
                    if (!uid.equals(currentUid) && Boolean.TRUE.equals(val)) {
                        otherTyping = true;
                        break;
                    }
                }
            }
            typingView.setText(otherTyping ? "typing..." : "");
            typingView.setVisibility(otherTyping ? View.VISIBLE : View.GONE);
        });
    }

    private void handleTypingChanged(boolean hasText) {
        if (activeChatId == null || currentUid == null) return;
        if (hasText) {
            if (!lastTypingSent) setTyping(true);
            if (typingStopRunnable != null) typingHandler.removeCallbacks(typingStopRunnable);
            typingStopRunnable = () -> setTyping(false);
            typingHandler.postDelayed(typingStopRunnable, 2500);
        } else {
            if (typingStopRunnable != null) typingHandler.removeCallbacks(typingStopRunnable);
            setTyping(false);
        }
    }

    private void setTyping(boolean typing) {
        if (activeChatId == null || currentUid == null) return;
        if (lastTypingSent == typing) return;
        lastTypingSent = typing;
        Map<String, Object> typingMap = new HashMap<>();
        typingMap.put(currentUid, typing);
        db.collection("chats").document(activeChatId)
                .set(Collections.singletonMap("typing", typingMap), SetOptions.merge());
    }

    private void markMessagesRead(List<DocumentSnapshot> docs) {
        for (DocumentSnapshot d : docs) {
            String sender = d.getString("senderId");
            if (sender != null && !sender.equals(currentUid)) {
                d.getReference().set(new HashMap<String, Object>() {{
                    put("deliveredTo", FieldValue.arrayUnion(currentUid));
                    put("readBy", FieldValue.arrayUnion(currentUid));
                }}, SetOptions.merge());
            }
        }
    }

    private void sendTextMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty() || activeChatId == null) return;
        messageInput.setText("");
        setTyping(false);
        saveMessage("text", text, null, null, 0L);
    }

    private void setReply(DocumentSnapshot d) {
        replyingToMessageId = d.getId();
        replyingToSenderName = value(d.getString("senderName"), "Pesan");
        replyingToText = quotePreviewText(d);
        updateReplyPreview();
        if (messageInput != null) {
            messageInput.requestFocus();
        }
    }

    private void clearReply() {
        clearReplyStateOnly();
        updateReplyPreview();
    }

    private void clearReplyStateOnly() {
        replyingToMessageId = null;
        replyingToSenderName = null;
        replyingToText = null;
    }

    private void updateReplyPreview() {
        if (replyPreviewBar == null || replyPreviewText == null) return;
        if (replyingToMessageId == null || replyingToMessageId.trim().isEmpty()) {
            replyPreviewBar.setVisibility(View.GONE);
            replyPreviewText.setText("");
        } else {
            replyPreviewText.setText("Membalas " + value(replyingToSenderName, "Pesan") + "\n" + value(replyingToText, "Pesan"));
            replyPreviewBar.setVisibility(View.VISIBLE);
        }
    }

    private String quotePreviewText(DocumentSnapshot d) {
        String type = value(d.getString("type"), "text");
        if ("image".equals(type)) return "📷 Foto";
        if ("video".equals(type)) return "🎥 Video";
        return shortText(value(d.getString("text"), "Pesan"), 90);
    }

    private void showAttachDialog() {
        String[] items = {"Kamera Foto", "Kamera Video", "Galeri Foto", "Galeri Video"};
        new AlertDialog.Builder(this)
                .setTitle("Kirim Media")
                .setItems(items, (d, which) -> {
                    if (which == 0) captureMedia("image");
                    else if (which == 1) captureMedia("video");
                    else if (which == 2) pickMedia("image");
                    else pickMedia("video");
                })
                .show();
    }

    private void openContacts() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
            return;
        }
        syncContacts();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CONTACTS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) syncContacts();
            else toast("Izin kontak dibutuhkan agar teman otomatis muncul");
            return;
        }
        if (requestCode == REQ_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCameraIntent(value(pendingCameraType, "image"));
            } else {
                toast("Izin kamera dibutuhkan untuk ambil foto/video langsung");
            }
        }
    }

    private void syncContacts() {
        showLoading("Mencari kontak yang sudah pakai Window...");
        List<ContactItem> contacts = readPhoneContacts();
        if (contacts.isEmpty()) {
            showHome();
            toast("Tidak ada kontak bernomor HP yang terbaca");
            return;
        }
        List<ContactItem> found = new ArrayList<>();
        AtomicInteger left = new AtomicInteger(contacts.size());
        for (ContactItem c : contacts) {
            db.collection("users_by_phone").document(c.phone).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult().exists()) {
                    DocumentSnapshot doc = task.getResult();
                    String uid = doc.getString("uid");
                    if (uid != null && !uid.equals(currentUid)) {
                        c.uid = uid;
                        c.windowName = value(doc.getString("name"), c.name);
                        c.username = value(doc.getString("username"), "");
                        found.add(c);
                    }
                }
                if (left.decrementAndGet() == 0) showContactsResult(found);
            });
        }
    }

    private List<ContactItem> readPhoneContacts() {
        List<ContactItem> list = new ArrayList<>();
        Map<String, ContactItem> byPhone = new HashMap<>();
        try (Cursor c = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
            if (c != null) {
                int nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int phoneIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                while (c.moveToNext()) {
                    String name = nameIdx >= 0 ? c.getString(nameIdx) : "Kontak";
                    String phone = phoneIdx >= 0 ? normalizePhone(c.getString(phoneIdx)) : "";
                    if (isValidPhone(phone) && !byPhone.containsKey(phone)) {
                        ContactItem item = new ContactItem();
                        item.name = value(name, "Kontak");
                        item.phone = phone;
                        byPhone.put(phone, item);
                    }
                }
            }
        } catch (Exception e) {
            toast("Gagal membaca kontak: " + e.getMessage());
        }
        list.addAll(byPhone.values());
        return list;
    }

    private void showContactsResult(List<ContactItem> found) {
        setRoot();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(6), dp(8), dp(6));
        header.setBackgroundColor(DARK_BLUE);
        Button back = new Button(this);
        back.setText("←");
        back.setTextColor(BLUE);
        back.setBackground(terminalBox(TERMINAL_PANEL, 1, TERMINAL_LINE, 6));
        TextView titleView = tv("Kontak Window", 20, BLUE, 1);
        header.addView(back, new LinearLayout.LayoutParams(dp(56), dp(48)));
        header.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(header);
        back.setOnClickListener(v -> showHome());

        if (found.isEmpty()) {
            TextView empty = tv("Belum ada kontak HP Mas yang terdaftar di Window.\n\nTeman harus daftar dulu dengan nomor HP yang sama seperti di kontak Mas.", 15, TERMINAL_DIM, 0);
            empty.setGravity(Gravity.CENTER);
            root.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            return;
        }

        RecyclerView recycler = new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new ContactsAdapter(found));
        root.addView(recycler, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void pickMedia(String type) {
        pendingMediaType = type;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType(type.equals("image") ? "image/*" : "video/*");
        startActivityForResult(Intent.createChooser(i, type.equals("image") ? "Pilih Foto dari Galeri" : "Pilih Video dari Galeri"), REQ_PICK_MEDIA);
    }

    private void captureMedia(String type) {
        pendingCameraType = type;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
            return;
        }
        startCameraIntent(type);
    }

    private void startCameraIntent(String type) {
        try {
            cameraMediaUri = createCameraUri(type);
            Intent i = new Intent("video".equals(type) ? MediaStore.ACTION_VIDEO_CAPTURE : MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraMediaUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if ("video".equals(type)) {
                i.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 10 * 60);
                i.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
            }

            try {
                startActivityForResult(i, "video".equals(type) ? REQ_TAKE_VIDEO : REQ_TAKE_PHOTO);
            } catch (android.content.ActivityNotFoundException e) {
                toast("Aplikasi kamera tidak ditemukan");
            }
        } catch (Exception e) {
            toast("Gagal membuka kamera: " + e.getMessage());
        }
    }

    private Uri createCameraUri(String type) throws Exception {
        String dirType = "video".equals(type) ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES;
        File dir = new File(getExternalFilesDir(dirType), "window_camera");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("Folder kamera tidak bisa dibuat");
        }

        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String ext = "video".equals(type) ? ".mp4" : ".jpg";
        File file = new File(dir, "WINDOW_" + stamp + ext);
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_MEDIA && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            uploadMedia(pendingMediaType, data.getData());
            return;
        }
        if (requestCode == REQ_TAKE_PHOTO && resultCode == Activity.RESULT_OK && cameraMediaUri != null) {
            uploadMedia("image", cameraMediaUri);
            return;
        }
        if (requestCode == REQ_TAKE_VIDEO && resultCode == Activity.RESULT_OK && cameraMediaUri != null) {
            uploadMedia("video", cameraMediaUri);
        }
    }

    private void uploadMedia(String type, Uri uri) {
        if (activeChatId == null || uri == null) return;
        long size = getFileSize(uri);
        if ("image".equals(type) && size > MAX_IMAGE_BYTES) {
            toast("Foto maksimal 5 MB");
            return;
        }
        if ("video".equals(type)) {
            if (size > MAX_VIDEO_BYTES) {
                toast("Video maksimal 100 MB");
                return;
            }
            long duration = getVideoDuration(uri);
            if (duration > MAX_VIDEO_DURATION_MS) {
                toast("Video maksimal 10 menit");
                return;
            }
        }
        String cloudName = getString(R.string.cloudinary_cloud_name).trim();
        String uploadPreset = getString(R.string.cloudinary_upload_preset).trim();
        if (cloudName.isEmpty() || cloudName.contains("GANTI") || uploadPreset.isEmpty() || uploadPreset.contains("GANTI")) {
            toast("Cloudinary belum disetting. Isi cloudinary_cloud_name dan cloudinary_upload_preset di strings.xml");
            return;
        }

        toast("Upload ke Cloudinary dimulai...");
        new Thread(() -> {
            try {
                String secureUrl = uploadToCloudinary(type, uri, cloudName, uploadPreset);
                runOnUiThread(() -> saveMessage(type, type.equals("image") ? "Foto" : "Video", secureUrl, getDisplayName(uri), size));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Upload gagal: " + e.getMessage()));
            }
        }).start();
    }

    private String uploadToCloudinary(String type, Uri uri, String cloudName, String uploadPreset) throws Exception {
        String resourceType = "video".equals(type) ? "video" : "image";
        String endpoint = "https://api.cloudinary.com/v1_1/" + cloudName + "/" + resourceType + "/upload";
        String boundary = "WindowBoundary" + System.currentTimeMillis();
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream raw = new BufferedOutputStream(conn.getOutputStream())) {
            writeFormField(raw, boundary, "upload_preset", uploadPreset);

            String fileName = getDisplayName(uri);
            String mime = getContentResolver().getType(uri);
            if (mime == null || mime.trim().isEmpty()) {
                mime = "video".equals(type) ? "video/mp4" : "image/jpeg";
            }

            raw.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
            raw.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName.replace("\"", "") + "\"\r\n").getBytes("UTF-8"));
            raw.write(("Content-Type: " + mime + "\r\n\r\n").getBytes("UTF-8"));

            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception("File tidak bisa dibaca");
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    raw.write(buffer, 0, read);
                }
            }

            raw.write("\r\n".getBytes("UTF-8"));
            raw.write(("--" + boundary + "--\r\n").getBytes("UTF-8"));
            raw.flush();
        }

        int code = conn.getResponseCode();
        InputStream responseStream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String response = readAll(responseStream);
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new Exception("Cloudinary HTTP " + code + ": " + response);
        }

        JSONObject json = new JSONObject(response);
        String secureUrl = json.optString("secure_url", "");
        if (secureUrl.isEmpty()) throw new Exception("Cloudinary tidak mengembalikan secure_url");
        return secureUrl;
    }

    private void writeFormField(OutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes("UTF-8"));
        out.write(value.getBytes("UTF-8"));
        out.write("\r\n".getBytes("UTF-8"));
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            bos.write(buffer, 0, read);
        }
        return bos.toString("UTF-8");
    }

    private void saveMessage(String type, String text, String mediaUrl, String fileName, long size) {
        List<String> initial = new ArrayList<>();
        initial.add(currentUid);
        Map<String, Object> msg = new HashMap<>();
        msg.put("senderId", currentUid);
        msg.put("senderName", currentName);
        msg.put("type", type);
        msg.put("text", text);
        msg.put("mediaUrl", mediaUrl);
        msg.put("fileName", fileName);
        msg.put("size", size);
        if (replyingToMessageId != null && !replyingToMessageId.trim().isEmpty()) {
            msg.put("replyToMessageId", replyingToMessageId);
            msg.put("replyToSenderName", value(replyingToSenderName, "Pesan"));
            msg.put("replyToText", value(replyingToText, "Pesan"));
        }
        msg.put("createdAt", FieldValue.serverTimestamp());
        msg.put("deliveredTo", initial);
        msg.put("readBy", initial);

        DocumentReference chatRef = db.collection("chats").document(activeChatId);
        chatRef.collection("messages").add(msg).addOnSuccessListener(ref -> {
            Map<String, Object> update = new HashMap<>();
            update.put("lastMessage", previewText(type, text));
            update.put("lastSenderId", currentUid);
            update.put("updatedAt", FieldValue.serverTimestamp());
            chatRef.set(update, SetOptions.merge());
            clearReply();
        }).addOnFailureListener(e -> toast("Gagal kirim: " + e.getMessage()));
    }

    private String previewText(String type, String text) {
        if ("image".equals(type)) return "📷 Foto";
        if ("video".equals(type)) return "🎥 Video";
        return text;
    }

    private void openMedia(String url, String type) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.setDataAndType(Uri.parse(url), "image".equals(type) ? "image/*" : "video/*");
            startActivity(i);
        } catch (Exception e) {
            toast("Tidak bisa membuka media");
        }
    }

    private static class ContactItem {
        String name;
        String phone;
        String uid;
        String windowName;
        String username;
    }

    private class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.Holder> {
        private final List<ContactItem> items;
        ContactsAdapter(List<ContactItem> items) { this.items = items; }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(16), dp(10), dp(16), dp(10));
            row.setBackground(terminalBox(TERMINAL_PANEL, 1, TERMINAL_LINE, 6));
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView name = tv("", 17, TERMINAL_TEXT, 1);
            TextView sub = tv("", 14, TERMINAL_DIM, 0);
            row.addView(name);
            row.addView(sub);
            return new Holder(row, name, sub);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int pos) {
            ContactItem c = items.get(pos);
            h.name.setText(c.windowName);
            h.sub.setText("@" + c.username + " • " + c.name + " • " + displayPhone(c.phone));
            h.itemView.setOnClickListener(v -> createOrOpenPrivateChat(c.uid, c.windowName));
        }

        @Override public int getItemCount() { return items.size(); }
        class Holder extends RecyclerView.ViewHolder {
            TextView name, sub;
            Holder(@NonNull View itemView, TextView n, TextView s) { super(itemView); name = n; sub = s; }
        }
    }

    private class ChatsAdapter extends RecyclerView.Adapter<ChatsAdapter.Holder> {
        private final List<DocumentSnapshot> items = new ArrayList<>();

        DocumentSnapshot getItem(int position) {
            return items.get(position);
        }

        void submit(List<DocumentSnapshot> docs) {
            items.clear();
            items.addAll(docs);
            items.sort((a, b) -> {
                Timestamp ta = a.getTimestamp("updatedAt");
                Timestamp tb = b.getTimestamp("updatedAt");
                if (ta == null && tb == null) return 0;
                if (ta == null) return 1;
                if (tb == null) return -1;
                return tb.compareTo(ta);
            });
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(16), dp(10), dp(16), dp(10));
            row.setBackground(terminalBox(TERMINAL_PANEL, 1, TERMINAL_LINE, 6));
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView name = tv("", 17, TERMINAL_TEXT, 1);
            TextView last = tv("", 14, TERMINAL_DIM, 0);
            row.addView(name);
            row.addView(last);
            return new Holder(row, name, last);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int pos) {
            DocumentSnapshot d = items.get(pos);
            String title = chatTitle(d);
            String last = value(d.getString("lastMessage"), "");
            h.name.setText(title);
            h.last.setText(last);
            h.itemView.setOnClickListener(v -> { activeChatIsGroup = "group".equals(value(d.getString("type"), "private")); openChat(d.getId(), title); });
        }

        @Override public int getItemCount() { return items.size(); }

        class Holder extends RecyclerView.ViewHolder {
            TextView name, last;
            Holder(@NonNull View itemView, TextView n, TextView l) { super(itemView); name = n; last = l; }
        }
    }

    private class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.Holder> {
        private final List<DocumentSnapshot> items = new ArrayList<>();
        void submit(List<DocumentSnapshot> docs) { items.clear(); items.addAll(docs); notifyDataSetChanged(); }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout outer = new LinearLayout(parent.getContext());
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setPadding(dp(8), dp(4), dp(8), dp(4));
            outer.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new Holder(outer);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int pos) {
            DocumentSnapshot d = items.get(pos);
            h.outer.removeAllViews();
            String senderId = d.getString("senderId");
            boolean mine = currentUid.equals(senderId);
            String type = value(d.getString("type"), "text");
            String senderName = value(d.getString("senderName"), "");
            String text = value(d.getString("text"), "");
            String url = d.getString("mediaUrl");

            LinearLayout bubble = new LinearLayout(MainActivity.this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
            bubble.setBackground(terminalBox(mine ? DARK_BLUE : TERMINAL_PANEL_2, 1, mine ? BLUE : TERMINAL_LINE, 8));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.gravity = mine ? Gravity.END : Gravity.START;
            bubble.setLayoutParams(bp);
            bubble.setMinimumWidth(dp(70));

            if (!mine && isActiveGroup()) {
                TextView sender = tv(senderName, 12, BLUE, 1);
                sender.setPadding(0, 0, 0, dp(2));
                bubble.addView(sender);
            }

            String replyText = value(d.getString("replyToText"), "");
            String replySender = value(d.getString("replyToSenderName"), "");
            if (!replyText.isEmpty()) {
                LinearLayout quoted = new LinearLayout(MainActivity.this);
                quoted.setOrientation(LinearLayout.VERTICAL);
                quoted.setPadding(dp(8), dp(5), dp(8), dp(5));
                quoted.setBackground(terminalBox(mine ? TERMINAL_PANEL : BG, 1, TERMINAL_LINE, 6));

                TextView qSender = tv(value(replySender, "Pesan"), 11, BLUE, 1);
                qSender.setPadding(0, 0, 0, 0);
                TextView qBody = tv(shortText(replyText, 70), 12, TERMINAL_DIM, 0);
                qBody.setPadding(0, dp(1), 0, 0);

                quoted.addView(qSender);
                quoted.addView(qBody);

                LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                qp.setMargins(0, 0, 0, dp(6));
                bubble.addView(quoted, qp);
            }

            attachSwipeToReply(bubble, d);

            if ("image".equals(type) && url != null) {
                ImageView img = new ImageView(MainActivity.this);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                bubble.addView(img, new LinearLayout.LayoutParams(dp(220), dp(160)));
                Glide.with(MainActivity.this).load(url).into(img);
                img.setOnClickListener(v -> openMedia(url, "image"));
            } else if ("video".equals(type) && url != null) {
                TextView video = tv("▶  Preview Video\nTap untuk putar", 16, Color.WHITE, 1);
                video.setGravity(Gravity.CENTER);
                video.setBackground(terminalBox(Color.rgb(0, 16, 8), 1, TERMINAL_LINE, 6));
                bubble.addView(video, new LinearLayout.LayoutParams(dp(220), dp(150)));
                video.setOnClickListener(v -> openMedia(url, "video"));
            } else {
                TextView body = tv(text, 16, TERMINAL_TEXT, 0);
                body.setPadding(0, 0, 0, 0);
                bubble.addView(body);
            }

            TextView meta = tv("", 11, TERMINAL_DIM, 0);
            meta.setText(statusSpannable(d, mine));
            meta.setGravity(Gravity.END);
            meta.setPadding(0, dp(4), 0, 0);
            bubble.addView(meta);
            h.outer.addView(bubble);
        }

        @Override public int getItemCount() { return items.size(); }
        class Holder extends RecyclerView.ViewHolder { LinearLayout outer; Holder(@NonNull View v) { super(v); outer = (LinearLayout) v; } }
    }

    @Override
    public void onBackPressed() {
        if (activeChatId != null) {
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    private boolean isActiveGroup() {
        return activeChatIsGroup;
    }

    private void attachSwipeToReply(View bubble, DocumentSnapshot d) {
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        final boolean[] replied = new boolean[1];
        final boolean[] dragging = new boolean[1];

        bubble.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getRawX();
                    downY[0] = event.getRawY();
                    replied[0] = false;
                    dragging[0] = false;
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float moveDx = event.getRawX() - downX[0];
                    float moveDy = Math.abs(event.getRawY() - downY[0]);

                    if (moveDx > dp(10) && moveDy < dp(45)) {
                        dragging[0] = true;
                        float limited = Math.min(moveDx, dp(75));
                        v.setTranslationX(limited * 0.45f);
                    }

                    if (!replied[0] && moveDx > dp(55) && moveDy < dp(45)) {
                        replied[0] = true;
                        setReply(d);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    v.animate().translationX(0).setDuration(120).start();
                    return dragging[0] || replied[0];
            }

            return false;
        });
    }

    @SuppressWarnings("unchecked")
    private String chatTitle(DocumentSnapshot d) {
        String type = value(d.getString("type"), "private");
        if ("group".equals(type)) return value(d.getString("name"), "Grup");
        Map<String, Object> names = (Map<String, Object>) d.get("memberNames");
        if (names != null) {
            for (String uid : names.keySet()) {
                if (!uid.equals(currentUid)) return String.valueOf(names.get(uid));
            }
        }
        return "Chat Pribadi";
    }

    @SuppressWarnings("unchecked")
    private CharSequence statusSpannable(DocumentSnapshot d, boolean mine) {
        Timestamp t = d.getTimestamp("createdAt");
        String time = t == null ? "" : String.format(Locale.getDefault(), "%02d:%02d", t.toDate().getHours(), t.toDate().getMinutes());

        if (!mine) {
            return time;
        }

        // Sesuai request: pesan milik kita selalu tampil centang dua.
        // Kalau belum dibaca, centang dua tetap putih.
        // Kalau sudah dibaca, hanya centang dua yang berubah hijau gelap, jam tetap putih.
        String full = time + "  ✓✓";
        SpannableString span = new SpannableString(full);

        List<String> readBy = (List<String>) d.get("readBy");
        boolean isRead = readBy != null && readBy.size() > 1;

        int checkStart = full.indexOf("✓✓");
        if (checkStart >= 0) {
            int checkColor = isRead ? Color.rgb(0, 170, 80) : TERMINAL_TEXT;
            span.setSpan(
                    new ForegroundColorSpan(checkColor),
                    checkStart,
                    full.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        return span;
    }

    @Override
    protected void onStop() {
        super.onStop();
        setTyping(false);
    }

    private String privateChatId(String a, String b) {
        return a.compareTo(b) < 0 ? "private_" + a + "_" + b : "private_" + b + "_" + a;
    }

    private long getFileSize(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0) return c.getLong(idx);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private String getDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        return "media";
    }

    private long getVideoDuration(Uri uri) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(this, uri);
            String dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return dur == null ? 0 : Long.parseLong(dur);
        } catch (Exception ignored) {
            return 0;
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
    }

    private String cleanIdentifier(String raw) {
        if (raw == null) return "";
        String phone = normalizePhone(raw);
        if (isValidPhone(phone)) return phone;
        return cleanUsername(raw);
    }

    private String normalizePhone(String raw) {
        if (raw == null) return "";
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) digits = digits.substring(1);
        digits = digits.replaceAll("[^0-9]", "");
        while (digits.startsWith("00")) digits = digits.substring(2);
        if (digits.startsWith("0")) digits = "62" + digits.substring(1);
        return digits;
    }

    private boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^62[0-9]{8,13}$");
    }

    private String displayPhone(String phone) {
        if (phone == null || phone.isEmpty()) return "-";
        if (phone.startsWith("62")) return "+" + phone;
        return phone;
    }

    private String cleanUsername(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }

    private boolean isValidUsername(String username) {
        return username != null && username.matches("^[a-z0-9_]{4,20}$");
    }

    private String emailForUsername(String username) {
        return username + "@window.local";
    }

    private String usernameFromEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : "";
    }

    private String shortText(String text, int max) {
        String v = value(text, "").replace("\n", " ").trim();
        if (v.length() <= max) return v;
        return v.substring(0, Math.max(0, max - 3)) + "...";
    }

    private String value(String v, String fallback) { return v == null || v.trim().isEmpty() ? fallback : v; }
    private LinearLayout.LayoutParams lpMatchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private void addSpace(LinearLayout parent, int h) { Space s = new Space(this); parent.addView(s, new LinearLayout.LayoutParams(1, dp(h))); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable terminalBox(int color, int strokeDp, int strokeColor, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radiusDp));
        gd.setStroke(dp(strokeDp), strokeColor);
        return gd;
    }

    private GradientDrawable roundedBg(int color, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radiusDp));
        return gd;
    }

    private void protectBottomFromNavigationBar(View view, int extraDp) {
        final int startLeft = view.getPaddingLeft();
        final int startTop = view.getPaddingTop();
        final int startRight = view.getPaddingRight();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                int bottomInset;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bottomInset = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                } else {
                    bottomInset = insets.getSystemWindowInsetBottom();
                }

                v.setPadding(
                        startLeft,
                        startTop,
                        startRight,
                        bottomInset + dp(extraDp)
                );

                return insets;
            });

            view.requestApplyInsets();
        } else {
            view.setPadding(startLeft, startTop, startRight, dp(extraDp));
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
