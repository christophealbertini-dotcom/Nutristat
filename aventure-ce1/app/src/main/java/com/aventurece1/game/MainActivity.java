package com.aventurece1.game;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private final Random random = new Random();
    private final Handler handler = new Handler();
    private SharedPreferences prefs;
    private ToneGenerator tone;

    private final String[] LEVELS = {"CP", "CE1", "CHAMPION"};
    private final String[] DIFFS = {"Normal", "Difficile", "Légendaire", "Chrono"};
    private String level = "CP", difficulty = "Normal";

    private int score, totalGood, totalBad, stageGood, stageBad, stage, monsterHp, lives, correct, maxGame;
    private boolean gameOver, paused;
    private long startMs, pauseStarted, pausedTotal;
    private Runnable timerTick;

    private TextView scoreView, livesView, monsterHpView, operationView, messageView, timerView, typedView;
    private ImageView heroView;
    private MonsterView monsterView;
    private final Button[] answers = new Button[3];

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("avent_ce1", MODE_PRIVATE);
        try { tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80); } catch (Throwable ignored) {}
        immersive();
        showHome();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    private void immersive() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private int col(String hex) { return Color.parseColor(hex); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }

    private GradientDrawable bg(int start, int end, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{start, end});
        g.setCornerRadius(dp((int)radius));
        return g;
    }

    private GradientDrawable solid(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp((int)radius));
        return g;
    }

    private LinearLayout root(int top, int bottom) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(dp(14), dp(16), dp(14), dp(18));
        l.setBackground(bg(top, bottom, 0));
        return l;
    }

    private ScrollView scroll(View v) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.addView(v);
        return s;
    }

    private TextView text(String s, float size, int color) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER);
        v.setPadding(dp(7), dp(7), dp(7), dp(7));
        return v;
    }

    private Button button(String label, int c1, int c2) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(19); b.setTextColor(Color.WHITE); b.setAllCaps(false);
        b.setBackground(bg(c1, c2, 18));
        b.setElevation(dp(4));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(8), dp(6), dp(8), dp(6)); b.setLayoutParams(p);
        b.setPadding(dp(10), dp(12), dp(10), dp(12));
        return b;
    }

    private ImageView image(int res, int height) {
        ImageView x = new ImageView(this);
        x.setImageResource(res); x.setScaleType(ImageView.ScaleType.CENTER_CROP); x.setAdjustViewBounds(true);
        x.setBackground(solid(Color.WHITE, 22)); x.setClipToOutline(true); x.setElevation(dp(5));
        x.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(height)));
        return x;
    }

    private LinearLayout card(int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER); c.setPadding(dp(12),dp(12),dp(12),dp(12));
        c.setBackground(solid(color, 22)); c.setElevation(dp(5));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.setMargins(dp(8),dp(8),dp(8),dp(8)); c.setLayoutParams(p);
        return c;
    }

    private int prefInt(String k, int def) { return prefs.getInt(k, def); }
    private int minLevel() { return prefInt(level + "_min", 0); }
    private int maxLevel() { return prefInt(level + "_max", level.equals("CP") ? 100 : level.equals("CE1") ? 200 : 300); }
    private int maxMult() { return prefInt(level + "_mult", level.equals("CE1") ? 5 : 10); }

    private void setScreen(View v) { setContentView(v); immersive(); }

    private void showHome() {
        stopTimer(); gameOver = true;
        LinearLayout l = root(col("#55C9FF"), col("#DFF7FF"));

        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("🌟 Aventure CE1", 31, col("#083B66")); title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        top.addView(title, new LinearLayout.LayoutParams(0,-2,1));
        Button settings = button("⚙", col("#FFB300"), col("#F57C00")); settings.setTextSize(24);
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings, new LinearLayout.LayoutParams(dp(64),dp(58)));
        l.addView(top);

        l.addView(image(R.drawable.rules_hero, 210));
        TextView choose = text("Choisis ton niveau", 23, col("#083B66")); choose.setPadding(0,dp(13),0,dp(6)); l.addView(choose);

        int[][] colors = {{0xFF008DFF,0xFF0069D8},{0xFF36C943,0xFF159B2A},{0xFFA74DEB,0xFF7131BA}};
        for (int i=0;i<LEVELS.length;i++) {
            final String q = LEVELS[i];
            Button b = button((i==2?"👑  ":"⭐  ") + q, colors[i][0], colors[i][1]); b.setTextSize(24);
            b.setOnClickListener(v -> { level=q; showDifficulty(); }); l.addView(b);
        }

        Button quit = button("🚪 Quitter", col("#EF5350"), col("#C62828"));
        quit.setOnClickListener(v -> confirmQuitApp()); l.addView(quit);
        l.addView(text("4 monstres • calcul mental • records", 15, col("#365A75")));
        setScreen(scroll(l));
    }

    private void showDifficulty() {
        LinearLayout l = root(col("#FFF1A8"), col("#FFF9DF"));
        l.addView(text(level, 34, col("#5D3B00")));
        l.addView(text("Choisis la difficulté", 21, col("#6B4E16")));
        int[][] cc={{0xFF39C95A,0xFF168B38},{0xFFFFA726,0xFFE66A00},{0xFFB655DE,0xFF7B2CBF},{0xFF28A7F2,0xFF096FBE}};
        for(int i=0;i<DIFFS.length;i++) {
            final String d = DIFFS[i];
            String record;
            if (d.equals("Chrono")) {
                long best = prefs.getLong("time_"+level,0);
                record = "⏱ Meilleur temps : " + (best==0 ? "0 s" : seconds(best));
            } else record = "🏆 Meilleur score : " + prefs.getInt("record_"+level+"_"+d,0);
            Button b=button(d+"\n"+record,cc[i][0],cc[i][1]); b.setOnClickListener(v->{difficulty=d;showRules();}); l.addView(b);
        }
        Button back=button("← Retour",col("#78909C"),col("#546E7A")); back.setOnClickListener(v->showHome()); l.addView(back);
        setScreen(scroll(l));
    }

    private EditText numberEdit(int n) {
        EditText e=new EditText(this); e.setInputType(2); e.setText(String.valueOf(n)); e.setGravity(Gravity.CENTER); e.setTextSize(18);
        e.setTextColor(col("#263238")); e.setBackground(solid(Color.WHITE,12)); e.setPadding(dp(8),dp(8),dp(8),dp(8));
        e.setLayoutParams(new LinearLayout.LayoutParams(dp(110),-2)); return e;
    }

    private void showSettings() {
        LinearLayout l=root(col("#B9F6CA"),col("#E8F5E9"));
        l.addView(text("⚙ Paramètres",30,col("#145A32")));
        final Map<String,EditText[]> fields=new LinkedHashMap<>();
        for(String q:LEVELS) {
            LinearLayout box=card(Color.WHITE); box.addView(text(q,24,col("#0B7A3E")));
            EditText mn=numberEdit(prefInt(q+"_min",0));
            EditText mx=numberEdit(prefInt(q+"_max",q.equals("CP")?100:q.equals("CE1")?200:300));
            EditText mm=q.equals("CP")?null:numberEdit(prefInt(q+"_mult",q.equals("CE1")?5:10));
            box.addView(settingRow("Mini",mn)); box.addView(settingRow("Maxi",mx));
            if(mm!=null) box.addView(settingRow("Maxi multiplication",mm));
            fields.put(q,new EditText[]{mn,mx,mm}); l.addView(box);
        }
        Button save=button("💾 Enregistrer",col("#2ECC71"),col("#159447"));
        save.setOnClickListener(v->{
            SharedPreferences.Editor ed=prefs.edit();
            for(String q:LEVELS){EditText[] a=fields.get(q);int mi=parse(a[0],0),ma=Math.max(mi+1,parse(a[1],100));ed.putInt(q+"_min",Math.max(0,mi));ed.putInt(q+"_max",ma);if(a[2]!=null)ed.putInt(q+"_mult",Math.max(3,Math.min(10,parse(a[2],5))));}
            ed.apply(); Toast.makeText(this,"Paramètres enregistrés",Toast.LENGTH_SHORT).show(); showHome();
        }); l.addView(save);
        Button back=button("← Retour",col("#78909C"),col("#546E7A"));back.setOnClickListener(v->showHome());l.addView(back);
        setScreen(scroll(l));
    }

    private LinearLayout settingRow(String label, EditText field){
        LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(4),dp(5),dp(4),dp(5));
        r.addView(text(label,17,col("#333333")),new LinearLayout.LayoutParams(0,-2,1));r.addView(field);return r;
    }
    private int parse(EditText e,int d){try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception ex){return d;}}

    private void showRules(){
        LinearLayout l=root(col("#FFD86F"),col("#FFF5CB"));
        l.addView(text(difficulty.equals("Chrono")?"⏱ MODE CHRONO":"⚔ RÈGLES DU JEU",29,col("#7A3E00")));
        l.addView(image(R.drawable.rules_hero,200));
        LinearLayout box=card(Color.WHITE);
        String q=difficulty.equals("Chrono")
                ?"Bats les 4 monstres le plus vite possible !\n\n⚠ Attention, sois prudent : tu n'as qu'une seule vie.\n\n⏸ Le bouton Pause arrête complètement le chrono."
                :"Prêt à affronter le monstre ?\n\n✅ Chaque bonne réponse fait gagner 10 points et retire une vie au monstre.\n\n❌ Chaque mauvaise réponse fait perdre 5 points et une vie.\n\nSi tu n'as plus de vie, le monstre a gagné.";
        box.addView(text(q,19,col("#4E342E")));l.addView(box);
        Button go=button("🚀 C'est parti !",col("#38D34F"),col("#149631"));go.setTextSize(23);go.setOnClickListener(v->startGame());l.addView(go);
        Button back=button("← Retour",col("#78909C"),col("#546E7A"));back.setOnClickListener(v->showDifficulty());l.addView(back);
        setScreen(scroll(l));
    }

    private void startGame(){
        score=totalGood=totalBad=0;stage=1;gameOver=false;paused=false;pausedTotal=0;
        int base=maxLevel();maxGame=difficulty.equals("Normal")?Math.max(minLevel()+1,(int)Math.floor(base*.70)):base;
        lives=difficulty.equals("Normal")?10:(difficulty.equals("Chrono")?1:5);
        startMs=System.currentTimeMillis();showGame();if(difficulty.equals("Chrono"))startTimer();
    }

    private String monsterName(){return stage==1?"Monstre Caca":stage==2?"Monstre qui pète":stage==3?"Monstre Zombie":"Monstre Rigolo";}
    private int monsterColor(){return stage==1?col("#A86027"):stage==2?col("#8E44AD"):stage==3?col("#68A83E"):col("#198CFF");}

    private void showGame(){
        monsterHp=5;stageGood=stageBad=0;
        LinearLayout l=root(col("#74D6FF"),col("#E7FFD9"));

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView stageText=text("Monstre "+stage+" / 4",17,col("#063B5C"));header.addView(stageText,new LinearLayout.LayoutParams(0,-2,1));
        Button leave=button("✕ Quitter",col("#FF6B6B"),col("#D93636"));leave.setTextSize(13);leave.setOnClickListener(v->confirmLeaveGame());
        header.addView(leave,new LinearLayout.LayoutParams(dp(105),dp(48)));l.addView(header);

        LinearLayout arena=new LinearLayout(this);arena.setGravity(Gravity.CENTER);arena.setPadding(0,dp(5),0,dp(5));
        LinearLayout heroes=card(Color.WHITE);heroes.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        heroView=image(R.drawable.rules_hero,125);heroView.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(125)));heroes.addView(heroView);
        heroes.addView(text("Nos héros",16,col("#1565C0")));livesView=text("",15,col("#C62828"));heroes.addView(livesView);arena.addView(heroes);

        LinearLayout monster=card(Color.WHITE);monster.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        monsterView=new MonsterView(this,stage,monsterColor());monster.addView(monsterView,new LinearLayout.LayoutParams(-1,dp(125)));
        monster.addView(text(monsterName(),15,col("#6D3D18")));monsterHpView=text("",15,col("#C62828"));monster.addView(monsterHpView);arena.addView(monster);
        l.addView(arena);

        LinearLayout stats=card(col("#FFF7C2"));
        scoreView=text("",19,col("#164B60"));stats.addView(scoreView);
        if(difficulty.equals("Chrono")){timerView=text(formatTime(elapsed()),25,col("#0D63B8"));stats.addView(timerView);}l.addView(stats);

        operationView=text("",42,col("#17202A"));operationView.setBackground(solid(Color.WHITE,18));operationView.setElevation(dp(4));
        LinearLayout.LayoutParams opP=new LinearLayout.LayoutParams(-1,-2);opP.setMargins(dp(8),dp(8),dp(8),dp(8));operationView.setLayoutParams(opP);l.addView(operationView);
        messageView=text("À toi de jouer !",18,col("#7B1FA2"));l.addView(messageView);

        if(difficulty.equals("Légendaire"))addKeypad(l);else addChoices(l);
        if(difficulty.equals("Chrono")){Button pauseButton=button("⏸ Pause",col("#2196F3"),col("#1565C0"));pauseButton.setOnClickListener(v->togglePause(pauseButton));l.addView(pauseButton);}
        setScreen(scroll(l));updateHud();nextQuestion();
    }

    private void addChoices(LinearLayout l){
        LinearLayout row=new LinearLayout(this);int[][] cc={{0xFF00A8E8,0xFF0076B8},{0xFFFFB300,0xFFF57C00},{0xFFE84393,0xFFB5286B}};
        for(int i=0;i<3;i++){final int ix=i;answers[i]=button("",cc[i][0],cc[i][1]);answers[i].setTextSize(24);answers[i].setOnClickListener(v->{Object tag=answers[ix].getTag();if(tag instanceof Integer)submit((Integer)tag);});row.addView(answers[i],new LinearLayout.LayoutParams(0,dp(62),1));}
        l.addView(row);
    }

    private void addKeypad(LinearLayout l){
        typedView=text("—",32,col("#222222"));typedView.setBackground(solid(Color.WHITE,14));l.addView(typedView);
        int n=1;for(int y=0;y<3;y++){LinearLayout row=new LinearLayout(this);for(int x=0;x<3;x++){final int k=n++;Button b=button(String.valueOf(k),col("#3498DB"),col("#1474B8"));b.setOnClickListener(v->digit(k));row.addView(b,new LinearLayout.LayoutParams(0,dp(55),1));}l.addView(row);}
        LinearLayout row=new LinearLayout(this);Button zero=button("0",col("#3498DB"),col("#1474B8"));zero.setOnClickListener(v->digit(0));row.addView(zero,new LinearLayout.LayoutParams(0,dp(55),1));Button erase=button("⌫ Effacer",col("#FF8C42"),col("#E35F12"));erase.setOnClickListener(v->{typedView.setTag("");typedView.setText("—");});row.addView(erase,new LinearLayout.LayoutParams(0,dp(55),1));l.addView(row);
        Button ok=button("✅ VALIDER",col("#38D34F"),col("#149631"));ok.setTextSize(25);ok.setOnClickListener(v->{String s=typedView.getTag()==null?"":typedView.getTag().toString();if(s.isEmpty())Toast.makeText(this,"Tape une réponse",Toast.LENGTH_SHORT).show();else submit(Integer.parseInt(s));});l.addView(ok);
    }

    private void digit(int k){if(paused||gameOver)return;String s=typedView.getTag()==null?"":typedView.getTag().toString();if(s.length()<4){s+=k;typedView.setTag(s);typedView.setText(s);}}

    private void updateHud(){
        StringBuilder h=new StringBuilder(),m=new StringBuilder();for(int i=0;i<lives;i++)h.append("❤️");for(int i=0;i<monsterHp;i++)m.append("❤️");
        livesView.setText("Vies  "+(h.length()==0?"💔":h));monsterHpView.setText("PV  "+(m.length()==0?"💥":m));
        scoreView.setText(difficulty.equals("Chrono")?"⏱ Bats-les le plus vite possible":"🏆 Score : "+score);
    }

    private int stageMax(){int n=minLevel(),span=Math.max(1,maxGame-n);double f=stage==1?.40:stage==2?.70:1.0;return Math.max(n+2,n+(int)Math.floor(span*f));}

    private void nextQuestion(){
        if(gameOver||paused)return;int M=stageMax();int type=random.nextInt(level.equals("CP")?2:3);
        if(type==2 && !makeMultiplication(M))makeAddition(M);else if(type==1)makeSubtraction(M);else makeAddition(M);
        if(difficulty.equals("Légendaire")){typedView.setTag("");typedView.setText("—");}else fillChoices(M);
    }

    private void makeAddition(int M){int min=Math.max(0,Math.min(minLevel(),M));correct=min+random.nextInt(Math.max(1,M-min+1));int a=min+random.nextInt(Math.max(1,correct-min+1));int b=correct-a;operationView.setText(a+" + "+b+" = ?");}

    private void makeSubtraction(int M){
        int min=Math.max(0,minLevel()),upper=Math.min(M,100);ArrayList<int[]> valid=new ArrayList<>();
        for(int a=min;a<=upper;a++)for(int b=min;b<=a;b++)if(a+b<=100)valid.add(new int[]{a,b});
        if(valid.isEmpty()){int a=Math.min(50,upper),b=0;correct=a;operationView.setText(a+" − "+b+" = ?");return;}
        int[] q=valid.get(random.nextInt(valid.size()));correct=q[0]-q[1];operationView.setText(q[0]+" − "+q[1]+" = ?");
    }

    private boolean makeMultiplication(int M){
        ArrayList<int[]> valid=new ArrayList<>();int tableMax=Math.max(3,Math.min(10,maxMult()));
        for(int a=2;a<=tableMax;a++)for(int b=3;b<=10;b++)if(a*b<=M)valid.add(new int[]{a,b});
        if(valid.isEmpty())return false;int[] q=valid.get(random.nextInt(valid.size()));correct=q[0]*q[1];operationView.setText(q[0]+" × "+q[1]+" = ?");return true;
    }

    private void fillChoices(int M){
        int goodIndex=random.nextInt(3);int[] vals={Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE};vals[goodIndex]=correct;
        for(int i=0;i<3;i++)if(i!=goodIndex){int v,tries=0;do{int delta=1+random.nextInt(Math.max(3,Math.min(18,M/10+3)));v=Math.max(0,correct+(random.nextBoolean()?delta:-delta));tries++;}while((v==correct||contains(vals,v))&&tries<100);vals[i]=v;}
        for(int i=0;i<3;i++){answers[i].setText(String.valueOf(vals[i]));answers[i].setTag(vals[i]);answers[i].setEnabled(true);answers[i].setAlpha(1f);}
    }
    private boolean contains(int[] vals,int v){for(int n:vals)if(n==v)return true;return false;}

    private void submit(int value){
        if(paused||gameOver)return;
        if(value==correct){totalGood++;stageGood++;monsterHp--;if(!difficulty.equals("Chrono"))score+=10;messageView.setText("🐶 ATTAQUE !   👾 « Aïe ! »");soundGood();animateAttack(true);}
        else{totalBad++;stageBad++;lives--;if(!difficulty.equals("Chrono"))score=Math.max(0,score-5);messageView.setText("👾 ATTAQUE !   👧🐶 « Ouille ! »");soundBad();animateAttack(false);}
        updateHud();if(!difficulty.equals("Légendaire"))for(Button b:answers){b.setEnabled(false);b.setAlpha(.65f);}
        if(lives<=0){gameOver=true;handler.postDelayed(()->showFinal(false),650);return;}
        if(monsterHp<=0){handler.postDelayed(this::showStageSummary,650);return;}
        handler.postDelayed(this::nextQuestion,650);
    }

    private void animateAttack(boolean heroesAttack){
        View attacker=heroesAttack?heroView:monsterView;View victim=heroesAttack?monsterView:heroView;
        attacker.animate().translationX(heroesAttack?dp(28):-dp(28)).scaleX(1.08f).scaleY(1.08f).setDuration(140).withEndAction(()->attacker.animate().translationX(0).scaleX(1).scaleY(1).setDuration(180)).start();
        victim.animate().rotationBy(8).alpha(.55f).setDuration(100).withEndAction(()->victim.animate().rotation(0).alpha(1).setDuration(180)).start();
    }

    private void showStageSummary(){
        if(difficulty.equals("Chrono"))holdTimer();soundLevel();
        LinearLayout l=root(col("#FFE66D"),col("#FFF8D6"));
        l.addView(text("🎉 BRAVO !",40,col("#D35400")));l.addView(text("Tu as vaincu le "+monsterName()+" !",23,col("#6D3D18")));
        LinearLayout box=card(Color.WHITE);box.addView(text("✅ Bonnes réponses : "+stageGood+"\n❌ Mauvaises réponses : "+stageBad+(difficulty.equals("Chrono")?"\n⏱ Temps actuel : "+formatTime(elapsed()):"\n🏆 Score total : "+score),20,col("#333333")));l.addView(box);
        Button next=button(stage<4?"➡ Monstre suivant":"🏁 Voir le résultat",col("#38D34F"),col("#149631"));
        next.setOnClickListener(v->{if(stage>=4){if(difficulty.equals("Chrono"))resumeTimer();showFinal(true);}else{stage++;if(difficulty.equals("Chrono"))resumeTimer();showGame();}});l.addView(next);
        setScreen(scroll(l));
    }

    private int stars(){if(score>190)return 5;if(score>=171)return 4;if(score>=141)return 3;if(score>=100)return 2;return 1;}

    private void showFinal(boolean win){
        gameOver=true;long elapsed=elapsed();stopTimer();if(win)soundWin();
        LinearLayout l=root(win?col("#8BE28B"):col("#FFB3B3"),win?col("#EDFFE7"):col("#FFF0F0"));
        if(win)l.addView(image(R.drawable.victory_family,235));
        l.addView(text(win?"🏆 VICTOIRE !":"💥 Le monstre a gagné",34,win?col("#145A32"):col("#A00000")));
        boolean record=false;
        if(difficulty.equals("Chrono")){
            long old=prefs.getLong("time_"+level,0);record=win&&(old==0||elapsed<old);if(record)prefs.edit().putLong("time_"+level,elapsed).apply();
            l.addView(text("⏱ Score chrono : "+seconds(elapsed),29,col("#0D63B8")));
        } else {
            int old=prefs.getInt("record_"+level+"_"+difficulty,0);record=score>old;if(record)prefs.edit().putInt("record_"+level+"_"+difficulty,score).apply();
            l.addView(text("Score final : "+score+" / 200",29,col("#164B60")));StringBuilder s=new StringBuilder();for(int i=0;i<stars();i++)s.append("⭐");l.addView(text(s.toString(),39,col("#FFB300")));
        }
        if(record)l.addView(text("🎉 RECORD ! 🎉",32,col("#E65100")));
        l.addView(text("✅ Bonnes réponses : "+totalGood+"   •   ❌ Mauvaises : "+totalBad,18,col("#333333")));
        Button home=button("🏠 Menu principal",col("#2196F3"),col("#0D63B8"));home.setOnClickListener(v->showHome());l.addView(home);
        Button quit=button("🚪 Quitter",col("#EF5350"),col("#C62828"));quit.setOnClickListener(v->confirmQuitApp());l.addView(quit);
        setScreen(scroll(l));
    }

    private void startTimer(){
        stopTimer();timerTick=new Runnable(){@Override public void run(){if(!gameOver){if(timerView!=null&&!paused)timerView.setText(formatTime(elapsed()));handler.postDelayed(this,100);}}};handler.post(timerTick);
    }
    private long elapsed(){long now=paused?pauseStarted:System.currentTimeMillis();return Math.max(0,now-startMs-pausedTotal);}
    private String formatTime(long ms){long s=ms/1000;return String.format(Locale.FRANCE,"%02d:%02d.%d",s/60,s%60,(ms%1000)/100);}
    private String seconds(long ms){return String.format(Locale.FRANCE,"%.1f s",ms/1000.0);}
    private void togglePause(Button b){if(!paused){paused=true;pauseStarted=System.currentTimeMillis();b.setText("▶ Reprendre");messageView.setText("⏸ PAUSE");}else{resumeTimer();b.setText("⏸ Pause");messageView.setText("C'est reparti !");}}
    private void holdTimer(){if(!paused){paused=true;pauseStarted=System.currentTimeMillis();}}
    private void resumeTimer(){if(paused){paused=false;pausedTotal+=System.currentTimeMillis()-pauseStarted;}}
    private void stopTimer(){if(timerTick!=null)handler.removeCallbacks(timerTick);timerTick=null;}

    private void soundGood(){tone(ToneGenerator.TONE_PROP_ACK,180);} private void soundBad(){tone(ToneGenerator.TONE_PROP_NACK,230);} private void soundLevel(){tone(ToneGenerator.TONE_DTMF_5,280);} private void soundWin(){tone(ToneGenerator.TONE_DTMF_9,550);handler.postDelayed(()->tone(ToneGenerator.TONE_PROP_ACK,350),250);}
    private void tone(int type,int ms){try{if(tone!=null)tone.startTone(type,ms);}catch(Throwable ignored){}}

    private void confirmQuitApp(){new AlertDialog.Builder(this).setTitle("Quitter Aventure CE1 ?").setMessage("À bientôt pour une nouvelle aventure !").setNegativeButton("Annuler",null).setPositiveButton("Quitter",(d,w)->{stopTimer();finishAffinity();}).show();}
    private void confirmLeaveGame(){boolean wasPaused=paused;if(difficulty.equals("Chrono")&&!paused)holdTimer();new AlertDialog.Builder(this).setTitle("Quitter la partie ?").setMessage("La partie en cours sera perdue.").setNegativeButton("Continuer",(d,w)->{if(difficulty.equals("Chrono")&&!wasPaused)resumeTimer();immersive();}).setPositiveButton("Quitter la partie",(d,w)->showHome()).setOnCancelListener(d->{if(difficulty.equals("Chrono")&&!wasPaused)resumeTimer();immersive();}).show();}

    @Override public void onBackPressed(){if(!gameOver)confirmLeaveGame();else showHome();}
    @Override protected void onDestroy(){stopTimer();try{if(tone!=null)tone.release();}catch(Throwable ignored){}super.onDestroy();}

    public static class MonsterView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final int stage,color;
        MonsterView(Context c,int s,int co){super(c);stage=s;color=co;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas x){super.onDraw(x);float w=getWidth(),h=getHeight();
            p.setShadowLayer(10,0,6,0x55000000);p.setColor(color);x.drawOval(w*.10f,h*.22f,w*.90f,h*.92f,p);p.clearShadowLayer();
            if(stage==1){p.setColor(0xFF6B3518);x.drawOval(w*.34f,h*.04f,w*.66f,h*.36f,p);}
            if(stage==2){p.setColor(0xFFB8F240);x.drawCircle(w*.08f,h*.43f,w*.08f,p);x.drawCircle(w*.92f,h*.35f,w*.10f,p);p.setColor(0xFF7ED321);x.drawCircle(w*.04f,h*.35f,w*.04f,p);x.drawCircle(w*.96f,h*.48f,w*.05f,p);}
            if(stage==3){p.setColor(0xFF344C2E);x.drawRect(w*.33f,h*.03f,w*.67f,h*.22f,p);p.setColor(0xFF814C72);x.drawRect(w*.45f,h*.02f,w*.55f,h*.16f,p);}
            if(stage==4){p.setColor(0xFFFF8C21);x.drawCircle(w*.18f,h*.20f,w*.12f,p);x.drawCircle(w*.82f,h*.18f,w*.12f,p);p.setColor(0xFFFFD43B);x.drawCircle(w*.18f,h*.20f,w*.05f,p);x.drawCircle(w*.82f,h*.18f,w*.05f,p);}
            p.setColor(Color.WHITE);x.drawCircle(w*.38f,h*.40f,w*.11f,p);x.drawCircle(w*.64f,h*.40f,w*.11f,p);p.setColor(0xFF1B1B1B);x.drawCircle(w*.40f,h*.41f,w*.045f,p);x.drawCircle(w*.62f,h*.41f,w*.045f,p);
            p.setColor(0xFF7B1515);x.drawOval(w*.27f,h*.56f,w*.73f,h*.80f,p);p.setColor(0xFFFF6D9B);x.drawOval(w*.40f,h*.68f,w*.70f,h*.84f,p);
            p.setColor(0xFFFFF3C4);for(int i=0;i<4;i++){float cx=w*(.34f+i*.105f);x.drawRect(cx,h*.57f,cx+w*.055f,h*.65f,p);} }
    }
}
