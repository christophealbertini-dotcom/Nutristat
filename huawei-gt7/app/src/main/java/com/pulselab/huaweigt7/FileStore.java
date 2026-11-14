package com.pulselab.huaweigt7;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** User-consented import, local-only export and idempotent activity merge. */
final class FileStore {
    private static final String TAG = "PulseLabGT7";
    private static final String FOLDER = Environment.DIRECTORY_DOWNLOADS + "/PulseLab/WatchInbox/";
    private static final int MAX_FILE_BYTES = 45 * 1024 * 1024;
    private static final String PREFS = "pulselab_gt7_v20";
    static final String KEY_FOLDER_URI = "watch_folder_uri";

    static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized void note(Context ctx, String message) {
        String stamp = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(new Date());
        SharedPreferences p = prefs(ctx);
        String old = p.getString("log", "Journal des imports Huawei GT 7\n");
        String log = (old + stamp + " • " + message + "\n");
        if (log.length() > 30000) log = log.substring(log.length() - 30000);
        p.edit().putString("log", log).apply();
        Log.i(TAG, message);
    }

    static String log(Context ctx) { return prefs(ctx).getString("log", ""); }

    static String displayName(Context ctx, Uri uri) {
        String name = uri.getLastPathSegment();
        try (Cursor c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String s = c.getString(0);
                if (s != null && !s.isEmpty()) name = s;
            }
        } catch (Exception ignored) { }
        return name == null ? "activité" : name;
    }

    private static byte[] readBytes(Context ctx, Uri uri) throws Exception {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IllegalArgumentException("Impossible d'ouvrir le fichier.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[16 * 1024];
            int n;
            while ((n = in.read(chunk)) != -1) {
                if (out.size() + n > MAX_FILE_BYTES)
                    throw new IllegalArgumentException("Fichier trop volumineux (>45 Mo).");
                out.write(chunk, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder s = new StringBuilder();
        for (byte b : d) s.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return s.toString();
    }

    static synchronized String importUri(Context ctx, Uri uri) {
        String name = displayName(ctx, uri);
        try {
            byte[] original = readBytes(ctx, uri);
            String digest = sha256(original);
            SharedPreferences p = prefs(ctx);
            if (p.getBoolean("imported_sha_" + digest, false)) {
                return name + " : déjà importé";
            }
            ActivityData activity = WorkoutParser.parse(original, name);
            JSONObject json = activity.toJson();
            String result = saveJson(ctx, activity.fileName(), json);
            if (!p.edit().putBoolean("imported_sha_" + digest, true).commit())
                throw new IllegalStateException("Impossible de mémoriser le fichier.");
            note(ctx, name + " → " + result + " (" + activity.samples.size() + " échantillons)");
            return result;
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            note(ctx, "ÉCHEC " + name + " : " + reason);
            return name + " : erreur : " + reason;
        }
    }

    private static Uri existing(Context ctx, String fileName) {
        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        try (Cursor c = ctx.getContentResolver().query(collection,
                new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
                        MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                new String[]{fileName, FOLDER}, null)) {
            if (c != null && c.moveToFirst())
                return ContentUris.withAppendedId(collection, c.getLong(0));
        }
        return null;
    }

    private static String saveJson(Context ctx, String filename, JSONObject latest) throws Exception {
        Uri old = existing(ctx, filename);
        JSONObject completed = latest;
        if (old != null) {
            try {
                byte[] previous = readBytes(ctx, old);
                JSONObject oldJson = new JSONObject(new String(previous, StandardCharsets.UTF_8));
                if (oldJson.optInt("schemaVersion") == 1) completed = merge(oldJson, latest);
            } catch (Exception e) {
                note(ctx, "Ancien fichier illisible, nouvelle sauvegarde : " + filename);
            }
        }
        byte[] bytes = completed.toString(2).getBytes(StandardCharsets.UTF_8);
        Uri uri = old;
        if (uri == null) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = ctx.getContentResolver().insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
        } else {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            ctx.getContentResolver().update(uri, v, null, null);
        }
        if (uri == null) throw new IllegalStateException("Impossible de créer le JSON dans Téléchargements.");
        try {
            try (java.io.OutputStream stream = ctx.getContentResolver().openOutputStream(uri, "wt")) {
                if (stream == null) throw new IllegalStateException("Accès refusé au fichier JSON.");
                stream.write(bytes);
                stream.flush();
            }
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            ctx.getContentResolver().update(uri, v, null, null);
        } catch (Exception e) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            ctx.getContentResolver().update(uri, v, null, null);
            throw e;
        }
        return "Téléchargements/PulseLab/WatchInbox/" + filename;
    }

    // GPS and HR are merged by UTC timestamp. Importing GPX after FIT can
    // improve the route without deleting the detailed heart-rate FIT records.
    private static JSONObject merge(JSONObject old, JSONObject current) throws Exception {
        JSONObject root = score(current) >= score(old) ? current : old;
        JSONObject secondary = root == current ? old : current;
        for (String key : new String[]{"gpsPoints","heartRateSamples","samples"})
            root.put(key, union(old.optJSONArray(key), current.optJSONArray(key)));
        JSONObject a = root.optJSONObject("activity"), b = secondary.optJSONObject("activity");
        if (a != null && b != null) {
            for (String key : new String[]{"distanceMeters","durationSeconds","caloriesKcal","endTimeMs"}) {
                double now = a.optDouble(key, 0), other = b.optDouble(key, 0);
                if (other > now) a.put(key, other);
            }
            JSONObject ah = a.optJSONObject("heartRate"), bh = b.optJSONObject("heartRate");
            if (ah == null) { ah = new JSONObject(); a.put("heartRate", ah); }
            if (bh != null) {
                for (String k : new String[]{"averageBpm","minimumBpm","maximumBpm"}) {
                    if (!ah.has(k) && bh.has(k)) ah.put(k, bh.opt(k));
                }
            }
        }
        JSONObject src = root.optJSONObject("source"), otherSrc = secondary.optJSONObject("source");
        if (src != null && otherSrc != null) {
            String f1 = src.optString("format"), f2 = otherSrc.optString("format");
            if (!f1.contains(f2) && !f2.isEmpty()) src.put("format", f1 + "+" + f2);
        }
        return root;
    }

    private static int score(JSONObject j) {
        String fmt = j.optJSONObject("source") == null ? "" :
            j.optJSONObject("source").optString("format").toUpperCase(Locale.ROOT);
        int base = fmt.contains("FIT") ? 300 : fmt.contains("TCX") ? 200 : 100;
        JSONArray hr = j.optJSONArray("heartRateSamples"), gps = j.optJSONArray("gpsPoints");
        return base + (hr == null ? 0 : Math.min(hr.length(), 300)) +
                (gps == null ? 0 : Math.min(gps.length(), 300));
    }

    private static JSONArray union(JSONArray a, JSONArray b) throws Exception {
        TreeMap<Long,JSONObject> sorted = new TreeMap<>();
        for (JSONArray input : new JSONArray[]{a, b}) {
            if (input == null) continue;
            for (int i = 0; i < input.length(); i++) {
                JSONObject point = input.optJSONObject(i);
                if (point == null || !point.has("timeMs")) continue;
                long time = point.optLong("timeMs", 0);
                if (time <= 0) continue;
                JSONObject target = sorted.get(time);
                if (target == null) sorted.put(time, new JSONObject(point.toString()));
                else {
                    for (java.util.Iterator<String> it = point.keys(); it.hasNext();) {
                        String key = it.next();
                        if (!target.has(key) || target.isNull(key)) target.put(key, point.opt(key));
                    }
                }
            }
        }
        JSONArray out = new JSONArray();
        for (JSONObject p : sorted.values()) out.put(p);
        return out;
    }

    static boolean isWorkoutFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".fit") || lower.endsWith(".gpx") ||
                lower.endsWith(".tcx"); // do not scan all XML or privacy ZIPs blindly
    }

    static String scanTree(Context ctx, Uri treeUri) {
        int[] counts = {0, 0};
        try {
            DocumentFile root = DocumentFile.fromTreeUri(ctx, treeUri);
            if (root == null || !root.isDirectory()) throw new IllegalArgumentException("Dossier non accessible");
            walk(ctx, root, 0, counts);
        } catch (Exception e) {
            note(ctx, "Dossier inaccessible : " + e.getMessage());
        }
        String summary = "Analyse dossier : " + counts[0] + " fichier(s) traité(s), " + counts[1] + " dossier(s)";
        note(ctx, summary);
        return summary;
    }

    private static void walk(Context ctx, DocumentFile folder, int depth, int[] counts) {
        if (depth > 4 || counts[0] >= 500 || counts[1] >= 100) return;
        counts[1]++;
        for (DocumentFile child : folder.listFiles()) {
            if (counts[0] >= 500) break;
            if (child.isDirectory()) walk(ctx, child, depth + 1, counts);
            else if (child.isFile() && isWorkoutFile(child.getName())) {
                counts[0]++;
                importUri(ctx, child.getUri());
            }
        }
    }
}
