#!/usr/bin/env python3
"""Build the standalone Gadgetbridge-based GT7 diagnostic APK.
Source code at https://codeberg.org/Freeyourgadget/Gadgetbridge
Gadgetbridge source is AGPL-3.0-or-later; all local modifications below are source-available.
No third-party Huawei keys or user identifiers are compiled in the APK.
"""
from pathlib import Path
import sys

root=Path(sys.argv[1]).resolve()
app=root/"app"
assert (app/"build.gradle").is_file(), "Expected Gadgetbridge source layout"
gradle=app/"build.gradle"
s=gradle.read_text()
# Keep a separate app ID so the test can coexist with Huawei Health and Pulse Lab.
before='applicationId "nodomain.freeyourgadget.gadgetbridge"'
assert s.count(before)==1, "Unexpected Gadgetbridge Gradle project"
s=s.replace(before,'applicationId "com.pulselab.gt7directtest"')
gradle.write_text(s)

# Keep the existing complete pairing, device discovery, Huawei protocol and
# workout export pipeline from upstream. No direct patches to sync state/ACK.
# The diagnostic activity only enables logging and lets the tester copy/share it.
java=app/"src/main/java/nodomain/freeyourgadget/gadgetbridge/activities/PulseLabGT7DiagnosticActivity.java"
java.parent.mkdir(parents=True,exist_ok=True)
java.write_text(r'''/*
 * Pulse Lab GT7 Bluetooth diagnostic screen - independent test.
 * Copyright (c) 2026 Pulse Lab contributors.
 * Licensed under AGPL-3.0-or-later; upstream Gadgetbridge AGPL license retained.
 */
package nodomain.freeyourgadget.gadgetbridge.activities;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import nodomain.freeyourgadget.gadgetbridge.Logging;

public class PulseLabGT7DiagnosticActivity extends Activity {
    private static final Logger LOG = LoggerFactory.getLogger(PulseLabGT7DiagnosticActivity.class);
    private TextView report;
    private TextView status;
    private int dp(int n) {return (int) (n * getResources().getDisplayMetrics().density + 0.5f);}

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(24,51,70));
        // Gadgetbridge sets up its logger in GBApplication. Turn on file logging
        // before opening the watch screen, to capture authentication and sync errors.
        try {
            android.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putBoolean("log_to_file",true).apply();
            Logging.getInstance().setFileLoggingEnabled(true);
            LOG.info("Pulse Lab GT7 diagnostic started; no automatic sync issued");
        } catch (Exception e) { android.util.Log.w("PulseLabGT7", "Cannot enable file logs", e); }
        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(20),dp(18),dp(24));
        root.setBackgroundColor(Color.rgb(246,248,250));
        sc.addView(root);
        setContentView(sc);
        TextView header=new TextView(this);
        header.setText("PULSE LAB • HUAWEI GT 7\nTest Bluetooth indépendant v0.1");
        header.setTextSize(23);
        header.setTextColor(Color.rgb(16,43,58));
        root.addView(header);
        TextView guide=new TextView(this);
        guide.setText("\n1. Huawei Santé a déjà récupéré ta séance.\n"
          +"2. Force l'arrêt de Huawei Santé (sans la désinstaller ni dissocier la montre).\n"
          +"3. Ouvre Gadgetbridge avec le bouton ci-dessous et associe la GT 7 "
          +"sans réinitialiser la montre.\n"
          +"4. Dans Gadgetbridge, ouvre Sports → Rafraîchir pour demander les entraînements.\n"
          +"5. Reviens ici et copie/partage le journal.\n\n"
          +"Si l'association échoue, arrête les essais. L'utilisation avec Huawei Santé "
          +"peut nécessiter ton identifiant de compte Huawei (17 chiffres), "
          +"à saisir localement sans le partager.\n\n"
          +"N'efface AUCUNE activité, ne réinitialise/dissocie PAS la GT 7.");
        guide.setTextSize(15);
        guide.setTextColor(Color.DKGRAY);
        root.addView(guide);
        button(root,"Ouvrir Gadgetbridge • appareils et sports",()->{
            LOG.info("Tester opened Gadgetbridge; awaiting manual Huawei workout fetch");
            startActivity(new Intent(this,ControlCenterv2.class));
        });
        button(root,"Actualiser le journal",this::refresh);
        button(root,"Copier le journal complet",()-> {
            String content=readLog();
            ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("Pulse Lab GT7 journal",content));
            status.setText("Journal copié ("+content.length()+" caractères). Vérifie et masque les identifiants avant l'envoi.");
            LOG.info("Pulse Lab diagnostic log copied");
        });
        button(root,"Partager le journal",()->{
            String content=readLog();
            Intent in=new Intent(Intent.ACTION_SEND);
            in.setType("text/plain");
            in.putExtra(Intent.EXTRA_SUBJECT,"Diagnostic Pulse Lab GT7 Bluetooth");
            in.putExtra(Intent.EXTRA_TEXT,content);
            startActivity(Intent.createChooser(in,"Partager journal GT7"));
        });
        status=new TextView(this);
        status.setTextSize(14);
        status.setTextColor(Color.rgb(16,104,94));
        status.setPadding(0,dp(12),0,dp(10));
        root.addView(status);
        report=new TextView(this);
        report.setTextSize(11);
        report.setTextColor(Color.DKGRAY);
        report.setTextIsSelectable(true);
        root.addView(report);
        refresh();
    }
    private void button(LinearLayout root,String title,Runnable click) {
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(title);
        b.setOnClickListener(v -> click.run());
        root.addView(b,new LinearLayout.LayoutParams(-1,dp(53)));
    }
    private String readLog() {
        String path=Logging.getInstance().getLogPath();
        if(path==null) return "Fichier journal non disponible. Ouvre la page Debug dans Gadgetbridge puis Partager le journal.";
        try {
            File f=new File(path);
            if (!f.exists()) return "Journal encore vide : "+path;
            byte[] data=Files.readAllBytes(f.toPath());
            String text=new String(data,StandardCharsets.UTF_8);
            // Minimize risk of sharing historic logs. Max 400KiB recent event text.
            if(text.length()>400000) text=text.substring(text.length()-400000);
            return "PULSE LAB GT7 - LOG (avant partage, masquer MAC/identifiant Huawei)\n"+text;
        } catch(Exception e) { return "Impossible de lire le journal: "+e; }
    }
    private void refresh(){
        String text=readLog();
        status.setText("Journal enregistré : "+String.valueOf(Logging.getInstance().getLogPath())
           +" • "+text.length()+" caractères");
        report.setText(text.length()>12000?text.substring(text.length()-12000):text);
    }
}
''',encoding="utf-8")

manifest=app/"src/main/AndroidManifest.xml"
text=manifest.read_text()
add='''        <activity
            android:name=".activities.PulseLabGT7DiagnosticActivity"
            android:exported="true"
            android:label="Pulse Lab GT7 • Journal">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
'''
assert text.count("</application>")==1
manifest.write_text(text.replace("</application>",add+"    </application>"))

# Label the original GB launcher as the test connector, while keeping
# the native Gadgetbridge screens and translations.
strings=app/"src/main/res/values/strings.xml"
s=strings.read_text()
old='<string name="application_name_generic">Gadgetbridge</string>'
assert s.count(old)==1
strings.write_text(s.replace(old,'<string name="application_name_generic">Pulse Lab GT7 - Gadgetbridge</string>'))

print("Pulse Lab GT7: upstream Huawei transport retained, manual workout fetch, full shareable diagnostic, source AGPL")
