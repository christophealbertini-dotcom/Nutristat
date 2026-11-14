package com.pulselab.huaweigt7;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Standalone, entirely local Huawei workout importer; no BLE or account credentials. */
public final class MainActivity extends Activity {
    private static final int PICK_FILES = 210, PICK_FOLDER = 211;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView status, log, folder, subheading;
    private Button pick, pickFolder, scan, stop;
    private int navy = Color.rgb(17, 38, 56);
    private int teal = Color.rgb(15, 118, 110);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(navy);
        buildUi();
        String uri = FileStore.prefs(this).getString(FileStore.KEY_FOLDER_URI, "");
        if (uri != null && !uri.isEmpty()) FolderImportWorker.schedule(this);
        refresh();
        processIncomingIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processIncomingIntent(intent);
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + .5f);
    }

    private TextView text(String content, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(content);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        v.setPadding(0, dp(7), 0, dp(7));
        return v;
    }

    private Button button(String label, int color, LinearLayout parent, View.OnClickListener handler) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackground(background(color, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.topMargin = dp(8);
        parent.addView(b, lp);
        b.setOnClickListener(handler);
        return b;
    }

    private LinearLayout card(LinearLayout root) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setBackground(background(Color.WHITE, 14));
        item.setPadding(dp(17), dp(16), dp(17), dp(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(14);
        root.addView(item, lp);
        return item;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 247, 250));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(25));
        scroll.addView(root);
        setContentView(scroll);

        root.addView(text("PULSE LAB", 28, navy, true));
        root.addView(text("HUAWEI WATCH GT 7  •  v2.0 FR", 17, teal, true));
        root.addView(text("Tes séances sportives sans connexion Bluetooth supplémentaire",
                14, Color.DKGRAY, false));

        LinearLayout first = card(root);
        first.addView(text("1. Synchroniser avec Huawei Santé", 18, navy, true));
        first.addView(text("Enregistre une séance sur la GT 7, puis ouvre Huawei Santé pour la synchroniser normalement.",
                14, Color.DKGRAY, false));
        first.addView(text("Aucun ACK, aucune clé Bluetooth et aucune connexion Pulse Lab à la montre.",
                13, teal, true));

        LinearLayout second = card(root);
        second.addView(text("2. Importer les séances", 18, navy, true));
        second.addView(text("Dans Health Sync : choisir Huawei Santé comme source et Google Drive ou Huawei Drive comme destination. Les activités sont exportées en FIT, avec GPX/TCX si GPS disponible.",
                14, Color.DKGRAY, false));
        second.addView(text("Tu peux aussi exporter un parcours GPX/TCX depuis Huawei Santé. Sélectionne un ou plusieurs fichiers FIT, GPX ou TCX, même depuis Google Drive.",
                14, Color.DKGRAY, false));
        pick = button("Importer des fichiers FIT / GPX / TCX", teal, second, v -> openFiles());

        LinearLayout third = card(root);
        third.addView(text("3. Dossier surveillé (facultatif)", 18, navy, true));
        third.addView(text("Si les fichiers sont visibles via le sélecteur Android (Documents, Google Drive…), choisis leur dossier. Pulse Lab peut l'analyser à la demande et périodiquement ; Android peut retarder les scans en arrière-plan.",
                14, Color.DKGRAY, false));
        folder = text("", 13, Color.DKGRAY, false);
        third.addView(folder);
        pickFolder = button("Choisir le dossier des activités", navy, third, v -> openFolder());
        scan = button("Chercher les nouvelles activités", teal, third, v -> scanFolder());
        stop = button("Arrêter la surveillance du dossier", Color.rgb(107, 114, 128), third,
            v -> {
                FileStore.prefs(this).edit().remove(FileStore.KEY_FOLDER_URI).apply();
                FolderImportWorker.stop(this);
                FileStore.note(this, "Surveillance du dossier arrêtée.");
                refresh();
            });

        LinearLayout fourth = card(root);
        fourth.addView(text("Résultat des imports", 18, navy, true));
        fourth.addView(text("JSON enregistrés dans Téléchargements/PulseLab/WatchInbox. "
                + "GPS et fréquences cardiaques horodatés conservés quand présents dans le fichier.",
                14, Color.DKGRAY, false));
        status = text("Prêt.", 15, teal, true);
        fourth.addView(status);
        subheading = text("Journal", 16, navy, true);
        fourth.addView(subheading);
        log = text("", 12, Color.DKGRAY, false);
        log.setTextIsSelectable(true);
        fourth.addView(log);
        button("Actualiser le journal", navy, fourth, v -> refresh());

        root.addView(text("Import entièrement local. Aucun compte Huawei ni accès Internet requis par Pulse Lab. "
                + "La récupération directe de la montre n'est pas activée : Huawei Santé reste maître de la synchronisation.",
                12, Color.GRAY, false));
    }

    private void openFiles() {
        Intent pickIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
        pickIntent.setType("*/*");
        pickIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(pickIntent, PICK_FILES);
    }

    private void openFolder() {
        Intent choose = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        choose.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(choose, PICK_FOLDER);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        if (request == PICK_FILES) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++)
                    uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) uris.add(data.getData());
            importUris(uris);
        } else if (request == PICK_FOLDER && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                FileStore.prefs(this).edit().putString(FileStore.KEY_FOLDER_URI, uri.toString()).apply();
                FolderImportWorker.schedule(this);
                FileStore.note(this, "Dossier autorisé : " + uri);
                refresh();
                scanFolder();
            } catch (Exception e) {
                status.setText("Dossier non autorisé : " + e.getMessage());
            }
        }
    }

    private void processIncomingIntent(Intent intent) {
        if (intent == null) return;
        List<Uri> uris = new ArrayList<>();
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri one = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (one != null) uris.add(one);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> many = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (many != null) uris.addAll(many);
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null)
            uris.add(intent.getData());
        if (!uris.isEmpty()) importUris(uris);
    }

    private void importUris(List<Uri> uris) {
        if (uris.isEmpty()) {
            Toast.makeText(this, "Aucun fichier sélectionné.", Toast.LENGTH_SHORT).show();
            return;
        }
        status.setText("Import de " + uris.size() + " fichier(s) en cours…");
        io.execute(() -> {
            int successes = 0, errors = 0;
            for (Uri uri : uris) {
                String result = FileStore.importUri(getApplicationContext(), uri);
                if (result.contains("erreur :")) errors++;
                else successes++;
            }
            final String summary = successes + " fichier(s) lus, " + errors + " erreur(s)";
            runOnUiThread(() -> { refresh(); status.setText(summary); });
        });
    }

    private void scanFolder() {
        String value = FileStore.prefs(this).getString(FileStore.KEY_FOLDER_URI, "");
        if (value == null || value.isEmpty()) {
            status.setText("Choisis d'abord un dossier accessible.");
            return;
        }
        status.setText("Analyse du dossier…");
        io.execute(() -> {
            String result = FileStore.scanTree(getApplicationContext(), Uri.parse(value));
            runOnUiThread(() -> { refresh(); status.setText(result); });
        });
    }

    private void refresh() {
        String value = FileStore.prefs(this).getString(FileStore.KEY_FOLDER_URI, "");
        if (folder != null) folder.setText(value == null || value.isEmpty() ?
                "Pas de dossier configuré. Import manuel disponible." : "Dossier sélectionné : " + Uri.parse(value).getLastPathSegment());
        if (scan != null) scan.setEnabled(value != null && !value.isEmpty());
        if (stop != null) stop.setEnabled(value != null && !value.isEmpty());
        if (log != null) {
            String text = FileStore.log(this);
            log.setText(text.length() > 6500 ? text.substring(text.length() - 6500) : text);
        }
    }

    @Override protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }
}
