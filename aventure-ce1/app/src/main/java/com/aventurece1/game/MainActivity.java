package com.aventurece1.game;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Random;

public class MainActivity extends Activity {
    private final Random random = new Random();
    private TextView operation, scoreView, hpView, message;
    private final Button[] answers = new Button[3];
    private int score = 0, hp = 6, max = 10, correct = 0;
    private boolean allowMinus = false, allowTimes = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHome();
    }

    private TextView text(String s, float sp) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(sp); v.setTextColor(Color.rgb(35,45,60));
        v.setGravity(Gravity.CENTER); v.setPadding(18,18,18,18);
        return v;
    }

    private Button button(String s) {
        Button b = new Button(this); b.setText(s); b.setTextSize(20);
        b.setAllCaps(false); b.setPadding(10,10,10,10);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(18,8,18,8); b.setLayoutParams(p); return b;
    }

    private LinearLayout root() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL); l.setPadding(22,36,22,22);
        l.setBackgroundColor(Color.rgb(248,251,255)); return l;
    }

    private void showHome() {
        LinearLayout l = root();
        l.addView(text("👧  🐶", 58));
        l.addView(text("Aventure CE1", 34));
        l.addView(text("Prototype de test\nBataille des monstres", 20));

        Button easy = button("⭐ Facile — additions jusqu'à 10");
        easy.setOnClickListener(v -> { max=10; allowMinus=false; allowTimes=false; startGame(); });
        l.addView(easy);
        Button ce1 = button("⭐⭐ CE1 — + et − jusqu'à 20");
        ce1.setOnClickListener(v -> { max=20; allowMinus=true; allowTimes=false; startGame(); });
        l.addView(ce1);
        Button hard = button("⭐⭐⭐ Champion — +, − et ×");
        hard.setOnClickListener(v -> { max=30; allowMinus=true; allowTimes=true; startGame(); });
        l.addView(hard);
        l.addView(text("But : répondre juste avant que le monstre n'arrive.\nChaque bonne réponse lui retire 1 ❤️.", 16));
        setContentView(l);
    }

    private void startGame() {
        score=0; hp=6;
        LinearLayout l = root();
        TextView title = text("👾  MONSTRE", 36); l.addView(title);
        hpView = text("❤️❤️❤️❤️❤️❤️", 24); l.addView(hpView);
        scoreView = text("Score : 0", 20); l.addView(scoreView);
        operation = text("", 42); l.addView(operation);
        message = text("Choisis la bonne réponse !", 17); l.addView(message);
        for (int i=0;i<3;i++) { answers[i]=button(""); final int idx=i; answers[i].setOnClickListener(v -> answer(idx)); l.addView(answers[i]); }
        Button quit = button("← Retour au menu"); quit.setOnClickListener(v -> showHome()); l.addView(quit);
        setContentView(l); nextQuestion();
    }

    private void nextQuestion() {
        int op = allowTimes ? random.nextInt(3) : (allowMinus ? random.nextInt(2) : 0);
        int a, b;
        if (op==2) {
            a=2+random.nextInt(8); b=2+random.nextInt(8); correct=a*b; operation.setText(a+" × "+b+" = ?");
        } else {
            a=random.nextInt(max+1); b=random.nextInt(max+1);
            if (op==1) { if (b>a) { int t=a; a=b; b=t; } correct=a-b; operation.setText(a+" − "+b+" = ?"); }
            else { correct=a+b; operation.setText(a+" + "+b+" = ?"); }
        }
        int good = random.nextInt(3);
        int[] vals = new int[3]; vals[good]=correct;
        for (int i=0;i<3;i++) if (i!=good) {
            int v; do { v=Math.max(0, correct + random.nextInt(9)-4); } while (v==correct || (i>0 && v==vals[0])); vals[i]=v;
        }
        for (int i=0;i<3;i++) { answers[i].setText(String.valueOf(vals[i])); answers[i].setTag(vals[i]); answers[i].setEnabled(true); }
    }

    private void answer(int idx) {
        int value = (Integer) answers[idx].getTag();
        if (value==correct) {
            score += 100; hp--; message.setText("✨ Bravo ! Tir magique ! +100");
            scoreView.setText("Score : "+score);
            StringBuilder hearts = new StringBuilder(); for(int i=0;i<hp;i++) hearts.append("❤️");
            hpView.setText(hearts.length()==0 ? "💥 VAINCU !" : hearts.toString());
            if (hp<=0) { win(); return; }
        } else {
            score = Math.max(0, score-25); scoreView.setText("Score : "+score);
            message.setText("Oups ! La bonne réponse était "+correct+". −25");
        }
        for (Button b: answers) b.setEnabled(false);
        operation.postDelayed(this::nextQuestion, 650);
    }

    private void win() {
        LinearLayout l=root();
        l.addView(text("🏆 Victoire !", 42)); l.addView(text("Le monstre est vaincu.\nScore : "+score, 25));
        l.addView(text("🐶 Ton compagnon fête la victoire !", 20));
        Button again=button("Rejouer"); again.setOnClickListener(v->startGame()); l.addView(again);
        Button menu=button("Menu principal"); menu.setOnClickListener(v->showHome()); l.addView(menu);
        setContentView(l);
    }
}
