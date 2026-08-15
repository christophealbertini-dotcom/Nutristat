package com.aventurece1.game;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
  final Random rnd=new Random();
  final Handler h=new Handler(Looper.getMainLooper());
  SharedPreferences prefs;
  ToneGenerator tone;
  String level="CP", diff="Normal";
  final String[] levels={"CP","CE1","CHAMPION"};
  final String[] diffs={"Normal","Difficile","Légendaire","Chrono"};
  int score,good,bad,stage,monsterHp,lives,correct,maxGame;
  boolean paused=false,over=true,inGame=false;
  long startMs,pauseMs,pausedTotal;
  Runnable tick;
  TextView scoreV,livesV,monsterHpV,opV,msgV,timerV,inputV;
  ImageView heroV,monsterV;
  final Button[] choices=new Button[3];

  @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("avent_ce1",MODE_PRIVATE);immersive();home();}
  @Override protected void onResume(){super.onResume();immersive();}
  @Override public void onWindowFocusChanged(boolean f){super.onWindowFocusChanged(f);if(f)immersive();}

  void immersive(){
    try{
      getWindow().setStatusBarColor(Color.TRANSPARENT);
      getWindow().setNavigationBarColor(Color.TRANSPARENT);
      if(Build.VERSION.SDK_INT>=30){
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController ctl=getWindow().getInsetsController();
        if(ctl!=null){ctl.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());ctl.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}
      }else{
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|
          View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
      }
    }catch(Throwable ignored){}
  }
  void safe(Runnable r){try{r.run();}catch(Throwable e){showError(e);}}
  void later(Runnable r,long ms){h.postDelayed(()->safe(r),ms);}
  void showError(Throwable e){stopTimer();over=true;inGame=false;LinearLayout l=root(c("#FFD0D0"),Color.WHITE);l.addView(tx("Oups !",34,c("#A00000")));l.addView(tx("Une erreur est survenue.\n"+e.getClass().getSimpleName()+"\n"+String.valueOf(e.getMessage()),17,c("#333333")));Button b=bt("Retour au menu",c("#269CF0"),c("#0873C3"));b.setOnClickListener(v->home());l.addView(b);set(sc(l));}

  int c(String x){return Color.parseColor(x);} int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}
  GradientDrawable grad(int a,int b,int r){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{a,b});g.setCornerRadius(dp(r));return g;}
  GradientDrawable solid(int a){GradientDrawable g=new GradientDrawable();g.setColor(a);g.setCornerRadius(dp(18));return g;}
  LinearLayout root(int a,int b){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setGravity(Gravity.CENTER_HORIZONTAL);l.setPadding(dp(14),dp(20),dp(14),dp(24));l.setBackground(grad(a,b,0));return l;}
  ScrollView sc(View v){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(v);return s;}
  TextView tx(String s,float z,int co){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(co);t.setGravity(Gravity.CENTER);t.setPadding(dp(7),dp(7),dp(7),dp(7));return t;}
  Button bt(String s,int a,int b){Button x=new Button(this);x.setText(s);x.setTextSize(19);x.setTextColor(Color.WHITE);x.setAllCaps(false);x.setBackground(grad(a,b,18));LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(-1,-2);q.setMargins(dp(8),dp(6),dp(8),dp(6));x.setLayoutParams(q);return x;}
  ImageView img(int res,int height){ImageView x=new ImageView(this);x.setImageResource(res);x.setScaleType(ImageView.ScaleType.CENTER_INSIDE);x.setAdjustViewBounds(true);x.setBackgroundColor(Color.WHITE);x.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(height)));return x;}
  LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setGravity(Gravity.CENTER);l.setPadding(dp(10),dp(10),dp(10),dp(10));l.setBackground(solid(Color.WHITE));LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(-1,-2);q.setMargins(dp(6),dp(6),dp(6),dp(6));l.setLayoutParams(q);return l;}
  void set(View v){setContentView(v);immersive();}
  int pi(String k,int v){return prefs.getInt(k,v);} int min(){return pi(level+"_min",0);} int max(){return pi(level+"_max",level.equals("CP")?100:level.equals("CE1")?200:300);} int mm(){return pi(level+"_mult",level.equals("CE1")?5:10);}

  void home(){stopTimer();over=true;inGame=false;paused=false;LinearLayout l=root(c("#46C8FF"),c("#E8FAFF"));LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView title=tx("🌟 Aventure CE1",30,c("#073B66"));title.setGravity(Gravity.CENTER_VERTICAL);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button settings=bt("⚙",c("#FFB300"),c("#F57C00"));settings.setOnClickListener(v->safe(this::settings));top.addView(settings,new LinearLayout.LayoutParams(dp(62),dp(55)));l.addView(top);l.addView(img(R.drawable.rules_hero,195));l.addView(tx("Choisis ton niveau",23,c("#073B66")));int[][] cs={{0xFF098BFF,0xFF0062C8},{0xFF40CC4B,0xFF168A28},{0xFFA64CEB,0xFF7130B8}};for(int i=0;i<3;i++){final String q=levels[i];Button b=bt((i==2?"👑 ":"⭐ ")+q,cs[i][0],cs[i][1]);b.setOnClickListener(v->safe(()->{level=q;difficulty();}));l.addView(b);}Button quit=bt("🚪 Quitter",c("#EF5350"),c("#C62828"));quit.setOnClickListener(v->{stopTimer();finishAndRemoveTask();});l.addView(quit);set(sc(l));}

  void difficulty(){over=true;inGame=false;LinearLayout l=root(c("#FFE477"),c("#FFF9DD"));l.addView(tx(level,34,c("#6A4500")));l.addView(tx("Choisis la difficulté",21,c("#6A4500")));int[][] cs={{0xFF42CB5D,0xFF168C39},{0xFFFFA726,0xFFE66A00},{0xFFB95BDC,0xFF7B2CBF},{0xFF2AA7F2,0xFF096FBE}};for(int i=0;i<4;i++){final String q=diffs[i];String rec;if(q.equals("Chrono")){long z=prefs.getLong("time_"+level,0);rec="⏱ Meilleur temps : "+(z==0?"0 s":seconds(z));}else rec="🏆 Meilleur score : "+prefs.getInt("record_"+level+"_"+q,0);Button b=bt(q+"\n"+rec,cs[i][0],cs[i][1]);b.setOnClickListener(v->safe(()->{diff=q;rules();}));l.addView(b);}Button back=bt("← Retour",c("#78909C"),c("#546E7A"));back.setOnClickListener(v->home());l.addView(back);set(sc(l));}

  LinearLayout settingRow(String label,int value,int step,int minV,int maxV,TextView[] holder){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(tx(label,16,c("#333333")),new LinearLayout.LayoutParams(0,-2,1));Button minus=bt("−",c("#FF9F43"),c("#E67E22"));TextView val=tx(String.valueOf(value),20,c("#17324D"));val.setTag(value);holder[0]=val;Button plus=bt("+",c("#42A5F5"),c("#1976D2"));minus.setOnClickListener(v->{int n=(Integer)val.getTag();n=Math.max(minV,n-step);val.setTag(n);val.setText(String.valueOf(n));});plus.setOnClickListener(v->{int n=(Integer)val.getTag();n=Math.min(maxV,n+step);val.setTag(n);val.setText(String.valueOf(n));});row.addView(minus,new LinearLayout.LayoutParams(dp(58),dp(48)));row.addView(val,new LinearLayout.LayoutParams(dp(78),-2));row.addView(plus,new LinearLayout.LayoutParams(dp(58),dp(48)));return row;}
  void settings(){stopTimer();over=true;inGame=false;LinearLayout l=root(c("#9CF1BE"),c("#E9FFF1"));l.addView(tx("⚙ Paramètres",30,c("#145A32")));final Map<String,TextView[]> map=new LinkedHashMap<>();for(String q:levels){LinearLayout box=card();box.addView(tx(q,24,c("#0B7A3E")));TextView[] mn={null},mx={null},mul={null};box.addView(settingRow("Mini",pi(q+"_min",0),5,0,500,mn));box.addView(settingRow("Maxi",pi(q+"_max",q.equals("CP")?100:q.equals("CE1")?200:300),10,10,500,mx));if(!q.equals("CP"))box.addView(settingRow("Maxi multiplication",pi(q+"_mult",q.equals("CE1")?5:10),1,3,10,mul));map.put(q,new TextView[]{mn[0],mx[0],mul[0]});l.addView(box);}Button save=bt("💾 Enregistrer",c("#2ECC71"),c("#159447"));save.setOnClickListener(v->safe(()->{SharedPreferences.Editor ed=prefs.edit();for(String q:levels){TextView[] a=map.get(q);int mi=(Integer)a[0].getTag(),ma=Math.max(mi+1,(Integer)a[1].getTag());ed.putInt(q+"_min",mi);ed.putInt(q+"_max",ma);if(a[2]!=null)ed.putInt(q+"_mult",(Integer)a[2].getTag());}ed.apply();Toast.makeText(this,"Paramètres enregistrés",Toast.LENGTH_SHORT).show();home();}));l.addView(save);Button back=bt("← Retour",c("#78909C"),c("#546E7A"));back.setOnClickListener(v->home());l.addView(back);set(sc(l));}

  void rules(){over=true;inGame=false;LinearLayout l=root(c("#FFD45F"),c("#FFF6D0"));l.addView(tx(diff.equals("Chrono")?"⏱ MODE CHRONO":"⚔ RÈGLES DU JEU",29,c("#7A3E00")));l.addView(img(R.drawable.rules_hero,195));LinearLayout box=card();box.addView(tx(diff.equals("Chrono")?"Bats les 4 monstres le plus vite possible !\n\n⚠ Attention, sois prudent, tu n'as qu'une seule vie.\n\n⏸ Le bouton Pause arrête le chrono.":"Prêt à affronter le monstre ?\n\n✅ Bonne réponse : +10 points et -1 vie au monstre.\n\n❌ Mauvaise réponse : -5 points et -1 vie.\n\nSi tu n'as plus de vie, le monstre a gagné.",19,c("#4E342E")));l.addView(box);Button go=bt("🚀 C'est parti !",c("#39D653"),c("#159832"));go.setOnClickListener(v->safe(this::startGame));l.addView(go);Button back=bt("← Retour",c("#78909C"),c("#546E7A"));back.setOnClickListener(v->difficulty());l.addView(back);set(sc(l));}

  void startGame(){score=good=bad=0;stage=1;over=false;inGame=true;paused=false;pausedTotal=0;maxGame=diff.equals("Normal")?Math.max(min()+1,(int)Math.floor(max()*.70)):max();lives=diff.equals("Normal")?10:diff.equals("Chrono")?1:5;startMs=System.currentTimeMillis();showGame();if(diff.equals("Chrono"))startTimer();}
  int monsterRes(){return stage==1?R.drawable.monster_poop:stage==2?R.drawable.monster_fart:stage==3?R.drawable.monster_zombie:R.drawable.monster_funny;}
  String monsterName(){return stage==1?"Monstre Caca":stage==2?"Monstre qui pète":stage==3?"Monstre Zombie":"Monstre Rigolo";}

  void showGame(){inGame=true;over=false;monsterHp=5;LinearLayout l=root(c("#69D1FF"),c("#E8FFD9"));LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(tx("Monstre "+stage+" / 4",17,c("#073B66")),new LinearLayout.LayoutParams(0,-2,1));Button quit=bt("✕",c("#FF6B6B"),c("#D93636"));quit.setTextSize(17);quit.setContentDescription("Quitter la partie");quit.setOnClickListener(v->leaveGame());header.addView(quit,new LinearLayout.LayoutParams(dp(52),dp(46)));l.addView(header);LinearLayout arena=new LinearLayout(this);LinearLayout a=card();a.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));heroV=img(R.drawable.heroes,120);a.addView(heroV);livesV=tx("",14,c("#C62828"));a.addView(livesV);arena.addView(a);LinearLayout b=card();b.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));monsterV=img(monsterRes(),120);b.addView(monsterV);b.addView(tx(monsterName(),14,c("#6D3D18")));monsterHpV=tx("",14,c("#C62828"));b.addView(monsterHpV);arena.addView(b);l.addView(arena);scoreV=tx("",18,c("#073B66"));l.addView(scoreV);timerV=null;if(diff.equals("Chrono")){timerV=tx(formatTime(elapsed()),21,c("#075FA8"));l.addView(timerV);}opV=tx("",39,c("#15232D"));opV.setBackground(solid(Color.WHITE));l.addView(opV);msgV=tx("À toi de jouer !",17,c("#7A1B91"));l.addView(msgV);if(diff.equals("Légendaire"))keypad(l);else choiceButtons(l);if(diff.equals("Chrono")){Button pz=bt(paused?"▶ Reprendre":"⏸ Pause",c("#2AA7F2"),c("#096FBE"));pz.setOnClickListener(v->togglePause(pz));l.addView(pz);}set(sc(l));hud();nextQuestion();}

  void choiceButtons(LinearLayout l){LinearLayout row=new LinearLayout(this);int[] cs={c("#1199FF"),c("#FFB300"),c("#EC4FA3")};for(int i=0;i<3;i++){final int n=i;choices[i]=bt("",cs[i],dark(cs[i]));choices[i].setTextSize(23);choices[i].setOnClickListener(v->safe(()->{Object o=choices[n].getTag();if(o instanceof Integer)submit((Integer)o);}));row.addView(choices[i],new LinearLayout.LayoutParams(0,-2,1));}l.addView(row);}
  int dark(int x){return Color.rgb((int)(Color.red(x)*.75),(int)(Color.green(x)*.75),(int)(Color.blue(x)*.75));}
  void keypad(LinearLayout l){inputV=tx("—",32,c("#222222"));inputV.setBackground(solid(Color.WHITE));l.addView(inputV);int n=1;for(int y=0;y<3;y++){LinearLayout row=new LinearLayout(this);for(int x=0;x<3;x++){final int k=n++;Button b=bt(String.valueOf(k),c("#269CF0"),c("#0873C3"));b.setOnClickListener(v->digit(k));row.addView(b,new LinearLayout.LayoutParams(0,-2,1));}l.addView(row);}LinearLayout row=new LinearLayout(this);Button z=bt("0",c("#269CF0"),c("#0873C3"));z.setOnClickListener(v->digit(0));row.addView(z,new LinearLayout.LayoutParams(0,-2,1));Button e=bt("⌫ Effacer",c("#F39C12"),c("#CE7300"));e.setOnClickListener(v->{inputV.setTag("");inputV.setText("—");});row.addView(e,new LinearLayout.LayoutParams(0,-2,1));l.addView(row);Button val=bt("✅ VALIDER",c("#39C95A"),c("#168B38"));val.setTextSize(24);val.setOnClickListener(v->safe(()->{String s=inputV.getTag()==null?"":String.valueOf(inputV.getTag());if(!s.isEmpty())submit(Integer.parseInt(s));}));l.addView(val);}
  void digit(int k){if(paused||over)return;String s=inputV.getTag()==null?"":String.valueOf(inputV.getTag());if(s.length()<4){s+=k;inputV.setTag(s);inputV.setText(s);}}
  void hud(){StringBuilder a=new StringBuilder(),b=new StringBuilder();for(int i=0;i<lives;i++)a.append("❤️");for(int i=0;i<monsterHp;i++)b.append("❤️");livesV.setText("Vies : "+(a.length()==0?"💔":a));monsterHpV.setText(b.length()==0?"💥":b.toString());scoreV.setText(diff.equals("Chrono")?"⚡ Une seule vie !":"🏆 Score : "+score);}
  int stageMax(){int n=min(),span=Math.max(1,maxGame-n);double f=stage==1?.40:stage==2?.70:1.0;return Math.max(n+2,n+(int)(span*f));}

  void nextQuestion(){if(over||paused||!inGame)return;int M=stageMax(),N=Math.min(min(),Math.max(0,M-1)),type=rnd.nextInt(level.equals("CP")?2:3);if(type==2&&!multiplication(M))addition(N,M);else if(type==1)soustraction(N,M);else addition(N,M);if(diff.equals("Légendaire")){inputV.setTag("");inputV.setText("—");}else fillChoices(M);}
  void addition(int N,int M){correct=N+rnd.nextInt(Math.max(1,M-N+1));int a=N+rnd.nextInt(Math.max(1,correct-N+1));int b=correct-a;opV.setText(a+" + "+b+" = ?");}
  void soustraction(int N,int M){int cap=Math.min(M,100);int a,b,tries=0;do{a=N+rnd.nextInt(Math.max(1,cap-N+1));b=N+rnd.nextInt(Math.max(1,cap-N+1));if(b>a){int t=a;a=b;b=t;}tries++;}while(a+b>100&&tries<1000);if(a+b>100){a=Math.min(cap,100);b=0;}correct=a-b;opV.setText(a+" − "+b+" = ?");}
  boolean multiplication(int M){ArrayList<int[]> list=new ArrayList<>();int maxMult=Math.max(3,Math.min(10,mm()));for(int a=2;a<=maxMult;a++)for(int b=3;b<=10;b++)if(a*b<=M)list.add(new int[]{a,b});if(list.isEmpty())return false;int[] x=list.get(rnd.nextInt(list.size()));correct=x[0]*x[1];opV.setText(x[0]+" × "+x[1]+" = ?");return true;}
  void fillChoices(int M){int goodIndex=rnd.nextInt(3);int[] vals={-1,-1,-1};vals[goodIndex]=correct;for(int i=0;i<3;i++)if(i!=goodIndex){int z,tries=0;do{int delta=1+rnd.nextInt(Math.max(3,Math.min(15,M/10+2)));z=Math.max(0,correct+(rnd.nextBoolean()?delta:-delta));tries++;}while((z==correct||z==vals[0]||z==vals[1]||z==vals[2])&&tries<100);if(z==correct)z=correct+i+1;vals[i]=z;}for(int i=0;i<3;i++){choices[i].setText(String.valueOf(vals[i]));choices[i].setTag(vals[i]);choices[i].setEnabled(true);}}

  void submit(int v){if(over||paused||!inGame)return;boolean ok=v==correct;if(ok){good++;monsterHp--;if(!diff.equals("Chrono"))score+=10;msgV.setText("🐶 ATTAQUE !  👾 Aïe !");sound(true);animate(true);}else{bad++;lives--;if(!diff.equals("Chrono"))score=Math.max(0,score-5);msgV.setText("👾 ATTAQUE !  👧🐶 Ouille !");sound(false);animate(false);}hud();if(!diff.equals("Légendaire"))for(Button b:choices)if(b!=null)b.setEnabled(false);if(lives<=0){over=true;inGame=false;later(()->finishGame(false),450);}else if(monsterHp<=0){inGame=false;later(this::summary,450);}else later(this::nextQuestion,450);}
  void sound(boolean yes){try{if(tone==null)tone=new ToneGenerator(AudioManager.STREAM_MUSIC,65);tone.startTone(yes?ToneGenerator.TONE_PROP_ACK:ToneGenerator.TONE_PROP_NACK,160);}catch(Throwable ignored){}}
  void levelSound(){try{if(tone==null)tone=new ToneGenerator(AudioManager.STREAM_MUSIC,70);tone.startTone(ToneGenerator.TONE_PROP_BEEP2,300);}catch(Throwable ignored){}}
  void animate(boolean yes){try{View a=yes?heroV:monsterV,b=yes?monsterV:heroV;a.animate().translationX(yes?dp(16):-dp(16)).setDuration(110).withEndAction(()->a.animate().translationX(0).setDuration(110)).start();b.animate().rotationBy(5).setDuration(90).withEndAction(()->b.animate().rotation(0).setDuration(90)).start();}catch(Throwable ignored){}}

  void summary(){inGame=false;if(diff.equals("Chrono"))hold();levelSound();LinearLayout l=root(c("#FFE978"),c("#FFF9D7"));l.addView(tx("🎉 BRAVO !",38,c("#D35400")));l.addView(img(monsterRes(),165));l.addView(tx("Tu as vaincu le "+monsterName()+" !",22,c("#6D4C41")));l.addView(tx("✅ Bonnes réponses : "+good+"\n❌ Mauvaises réponses : "+bad+(diff.equals("Chrono")?"\n⏱ Temps : "+formatTime(elapsed()):"\n🏆 Score : "+score),18,c("#333333")));Button nx=bt(stage<4?"➡ Monstre suivant":"🏁 Résultat",c("#39C95A"),c("#168B38"));nx.setOnClickListener(v->safe(()->{if(stage>=4){if(diff.equals("Chrono"))resumeClock();finishGame(true);}else{stage++;if(diff.equals("Chrono"))resumeClock();showGame();}}));l.addView(nx);Button quit=bt("✕ Quitter la partie",c("#EF5350"),c("#C62828"));quit.setOnClickListener(v->home());l.addView(quit);set(sc(l));}
  int stars(){return score>190?5:score>=171?4:score>=141?3:score>=100?2:1;}
  void finishGame(boolean win){inGame=false;over=true;long e=elapsed();stopTimer();LinearLayout l=root(win?c("#8BE89B"):c("#FFB7B7"),Color.WHITE);if(win)l.addView(img(R.drawable.victory_family,210));l.addView(tx(win?"🏆 VICTOIRE !":"💥 Le monstre a gagné",32,win?c("#155C2C"):c("#A51414")));boolean rec=false;if(diff.equals("Chrono")){long old=prefs.getLong("time_"+level,0);rec=win&&(old==0||e<old);if(rec)prefs.edit().putLong("time_"+level,e).apply();l.addView(tx("Score chrono : "+seconds(e),28,c("#075FA8")));}else{int old=prefs.getInt("record_"+level+"_"+diff,0);rec=score>old;if(rec)prefs.edit().putInt("record_"+level+"_"+diff,score).apply();l.addView(tx("Score final : "+score+" / 200",27,c("#073B66")));StringBuilder s=new StringBuilder();for(int i=0;i<stars();i++)s.append("⭐");l.addView(tx(s.toString(),38,c("#E6A300")));}if(rec)l.addView(tx("🎉 RECORD ! 🎉",31,c("#D05A00")));Button menu=bt("🏠 Menu principal",c("#269CF0"),c("#0873C3"));menu.setOnClickListener(v->home());l.addView(menu);set(sc(l));}

  void startTimer(){stopTimer();tick=new Runnable(){public void run(){try{if(!over&&!paused&&inGame&&timerV!=null&&timerV.isAttachedToWindow())timerV.setText(formatTime(elapsed()));if(!over)h.postDelayed(this,100);}catch(Throwable ignored){}}};h.post(tick);}
  void stopTimer(){if(tick!=null)h.removeCallbacks(tick);tick=null;}
  long elapsed(){long now=paused?pauseMs:System.currentTimeMillis();return Math.max(0,now-startMs-pausedTotal);}
  String formatTime(long m){long s=m/1000;return String.format(Locale.FRANCE,"%02d:%02d.%d",s/60,s%60,(m%1000)/100);}
  String seconds(long m){return String.format(Locale.FRANCE,"%.1f s",m/1000.0);}
  void hold(){if(!paused){paused=true;pauseMs=System.currentTimeMillis();}}
  void resumeClock(){if(paused){paused=false;pausedTotal+=System.currentTimeMillis()-pauseMs;}}
  void togglePause(Button b){if(over)return;if(!paused){hold();b.setText("▶ Reprendre");if(msgV!=null)msgV.setText("⏸ PAUSE");}else{resumeClock();b.setText("⏸ Pause");if(msgV!=null)msgV.setText("C'est reparti !");}}
  void leaveGame(){boolean wasPaused=paused;if(diff.equals("Chrono")&&!paused)hold();new AlertDialog.Builder(this).setTitle("Quitter la partie ?").setMessage("La partie en cours sera perdue.").setNegativeButton("Continuer",(d,w)->{if(diff.equals("Chrono")&&!wasPaused)resumeClock();immersive();}).setPositiveButton("Quitter",(d,w)->home()).setOnCancelListener(d->{if(diff.equals("Chrono")&&!wasPaused)resumeClock();immersive();}).show();}

  @Override public void onBackPressed(){if(inGame&&!over)leaveGame();else home();}
  @Override protected void onDestroy(){stopTimer();try{if(tone!=null)tone.release();}catch(Throwable ignored){}super.onDestroy();}
}
