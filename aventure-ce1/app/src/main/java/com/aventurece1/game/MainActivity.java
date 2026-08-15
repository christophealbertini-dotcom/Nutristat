package com.aventurece1.game;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
  private final Random random=new Random();
  private final String[] LEVELS={"CP","CE1","CHAMPION"};
  private final String[] DIFFS={"Normal","Difficile","Légendaire","Chrono"};
  private SharedPreferences prefs;
  private String selectedLevel="CP", selectedDiff="Normal";
  private int playerLives,score,good,bad,stage,monsterHp,correct,maxForGame;
  private boolean paused=false,gameOver=false;
  private long startMs,pausedAt,pausedTotal;
  private TextView scoreView,livesView,monsterView,operationView,messageView,timerView,inputView;
  private final Button[] answerButtons=new Button[3];
  private final Handler timerHandler=new Handler();
  private Runnable timerTick;

  @Override public void onCreate(Bundle b){ super.onCreate(b); prefs=getSharedPreferences("avent_ce1",MODE_PRIVATE); showHome(); }
  private int c(String h){return Color.parseColor(h);}  
  private LinearLayout root(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setGravity(Gravity.CENTER_HORIZONTAL); l.setPadding(24,28,24,28); l.setBackgroundColor(c("#F7FBFF")); return l; }
  private ScrollView scroll(LinearLayout l){ ScrollView s=new ScrollView(this); s.setFillViewport(true); s.addView(l); return s; }
  private TextView text(String s,float sp){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(c("#263238")); v.setGravity(Gravity.CENTER); v.setPadding(12,12,12,12); return v; }
  private Button button(String s){ Button b=new Button(this); b.setText(s); b.setTextSize(19); b.setAllCaps(false); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(12,7,12,7); b.setLayoutParams(p); return b; }
  private EditText numEdit(int value){ EditText e=new EditText(this); e.setInputType(2); e.setText(String.valueOf(value)); e.setGravity(Gravity.CENTER); e.setTextSize(18); e.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); return e; }
  private int prefInt(String k,int d){return prefs.getInt(k,d);}  
  private int levelMin(String l){return prefInt(l+"_min",0);}  
  private int levelMax(String l){return prefInt(l+"_max",l.equals("CP")?100:l.equals("CE1")?200:300);}  
  private int levelMult(String l){return prefInt(l+"_mult",l.equals("CE1")?5:10);}  

  private void showHome(){ stopTimer(); LinearLayout l=root(); LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); TextView t=text("👧  🐶   Aventure CE1",30); top.addView(t,new LinearLayout.LayoutParams(0,-2,1)); Button st=new Button(this); st.setText("⚙"); st.setTextSize(24); st.setOnClickListener(v->showSettings()); top.addView(st); l.addView(top); l.addView(text("Bataille des monstres",22)); l.addView(text("Choisis ton niveau",17)); for(String level:LEVELS){ Button b=button(level); b.setTextSize(24); b.setOnClickListener(v->{selectedLevel=level;showDifficulty();}); l.addView(b);} l.addView(text("Chaque partie contient 4 monstres à vaincre.",16)); setContentView(scroll(l)); }

  private void showDifficulty(){ LinearLayout l=root(); l.addView(text(selectedLevel,32)); l.addView(text("Choisis la difficulté",20)); for(String d:DIFFS){ int record=prefs.getInt("record_"+selectedLevel+"_"+d,0); Button b=button(d+"     🏆 "+record); b.setOnClickListener(v->{selectedDiff=d;showRules();}); l.addView(b);} Button back=button("← Retour"); back.setOnClickListener(v->showHome()); l.addView(back); setContentView(scroll(l)); }

  private void showSettings(){ LinearLayout l=root(); l.addView(text("⚙ Paramètres",30)); final Map<String,EditText[]> fields=new LinkedHashMap<>(); for(String level:LEVELS){ l.addView(text(level,23)); LinearLayout r1=new LinearLayout(this); r1.setGravity(Gravity.CENTER_VERTICAL); r1.addView(text("Mini",17),new LinearLayout.LayoutParams(0,-2,1)); EditText mn=numEdit(levelMin(level)); r1.addView(mn); l.addView(r1); LinearLayout r2=new LinearLayout(this); r2.setGravity(Gravity.CENTER_VERTICAL); r2.addView(text("Maxi",17),new LinearLayout.LayoutParams(0,-2,1)); EditText mx=numEdit(levelMax(level)); r2.addView(mx); l.addView(r2); EditText mm=null; if(!level.equals("CP")){ LinearLayout r3=new LinearLayout(this); r3.setGravity(Gravity.CENTER_VERTICAL); r3.addView(text("Maxi multiplication",17),new LinearLayout.LayoutParams(0,-2,1)); mm=numEdit(levelMult(level)); r3.addView(mm); l.addView(r3);} fields.put(level,new EditText[]{mn,mx,mm}); }
    Button save=button("Enregistrer"); save.setOnClickListener(v->{ SharedPreferences.Editor ed=prefs.edit(); for(String level:LEVELS){ EditText[] a=fields.get(level); int mn=parse(a[0],0),mx=Math.max(mn+1,parse(a[1],levelMax(level))); ed.putInt(level+"_min",mn); ed.putInt(level+"_max",mx); if(a[2]!=null) ed.putInt(level+"_mult",Math.max(3,Math.min(10,parse(a[2],levelMult(level)))));} ed.apply(); Toast.makeText(this,"Paramètres enregistrés",Toast.LENGTH_SHORT).show(); showHome(); }); l.addView(save); Button cancel=button("Annuler"); cancel.setOnClickListener(v->showHome()); l.addView(cancel); setContentView(scroll(l)); }
  private int parse(EditText e,int d){try{return Integer.parseInt(e.getText().toString().trim());}catch(Exception x){return d;}}

  private void showRules(){ LinearLayout l=root(); l.addView(text("📜 Règles",32)); l.addView(text("Chaque bonne réponse fait gagner 10 points et fait perdre une vie au monstre.\n\nChaque mauvaise réponse fait perdre 5 points et une vie.\n\nSi tu n’as plus de vie, le monstre a gagné.\n\nLa partie comporte 4 monstres. Chaque monstre a 5 vies.",19)); Button go=button("J’ai compris !"); go.setOnClickListener(v->startGame()); l.addView(go); Button back=button("← Retour"); back.setOnClickListener(v->showDifficulty()); l.addView(back); setContentView(scroll(l)); }

  private void startGame(){ score=0;good=0;bad=0;stage=1;gameOver=false;paused=false;pausedTotal=0; int base=levelMax(selectedLevel); maxForGame=selectedDiff.equals("Normal")?Math.max(levelMin(selectedLevel)+1,(int)Math.floor(base*.70)):base; if(selectedDiff.equals("Normal"))playerLives=10; else if(selectedDiff.equals("Difficile")||selectedDiff.equals("Légendaire"))playerLives=5; else playerLives=999999; startMs=System.currentTimeMillis(); showStageGame(); if(selectedDiff.equals("Chrono"))startTimer(); }

  private String monsterName(){return stage==1?"💩 Monstre Caca":stage==2?"💨 Monstre qui pète":stage==3?"🧟 Monstre Zombie":"🤪 Monstre Rigolo";}
  private String monsterEmoji(){return stage==1?"💩":stage==2?"👹💨":stage==3?"🧟":"🤪";}

  private void showStageGame(){ monsterHp=5; LinearLayout l=root(); LinearLayout arena=new LinearLayout(this); arena.setGravity(Gravity.CENTER); LinearLayout heroes=new LinearLayout(this); heroes.setOrientation(LinearLayout.VERTICAL); heroes.setGravity(Gravity.CENTER); heroes.addView(text("👧 🐶",38)); livesView=text("",18); heroes.addView(livesView); arena.addView(heroes,new LinearLayout.LayoutParams(0,-2,1)); LinearLayout monster=new LinearLayout(this); monster.setOrientation(LinearLayout.VERTICAL); monster.setGravity(Gravity.CENTER); monster.addView(text(monsterEmoji(),48)); monster.addView(text(monsterName(),16)); monsterView=text("",18); monster.addView(monsterView); arena.addView(monster,new LinearLayout.LayoutParams(0,-2,1)); l.addView(arena,new LinearLayout.LayoutParams(-1,-2)); LinearLayout stats=new LinearLayout(this); scoreView=text("Score : "+score,19); stats.addView(scoreView,new LinearLayout.LayoutParams(0,-2,1)); if(selectedDiff.equals("Chrono")){timerView=text("00:00.0",19);stats.addView(timerView,new LinearLayout.LayoutParams(0,-2,1));} l.addView(stats); operationView=text("",40); l.addView(operationView); messageView=text("À toi de jouer !",18); l.addView(messageView); if(selectedDiff.equals("Légendaire"))addKeypad(l); else addChoices(l); if(selectedDiff.equals("Chrono")){Button pause=button("⏸ Pause"); pause.setOnClickListener(v->togglePause(pause)); l.addView(pause);} Button quit=button("← Abandonner"); quit.setOnClickListener(v->showHome()); l.addView(quit); setContentView(scroll(l)); refreshHud(); nextQuestion(); }

  private void addChoices(LinearLayout l){for(int i=0;i<3;i++){answerButtons[i]=button("");final int ix=i;answerButtons[i].setOnClickListener(v->submitChoice(ix));l.addView(answerButtons[i]);}}
  private void addKeypad(LinearLayout l){ inputView=text("—",34); l.addView(inputView); int n=1; for(int row=0;row<3;row++){LinearLayout r=new LinearLayout(this);for(int col=0;col<3;col++){final int d=n++;Button b=button(String.valueOf(d));b.setOnClickListener(v->appendDigit(d));r.addView(b,new LinearLayout.LayoutParams(0,-2,1));}l.addView(r);} LinearLayout bottom=new LinearLayout(this); Button z=button("0");z.setOnClickListener(v->appendDigit(0));bottom.addView(z,new LinearLayout.LayoutParams(0,-2,1));Button er=button("⌫ Effacer");er.setOnClickListener(v->{inputView.setTag("");inputView.setText("—");});bottom.addView(er,new LinearLayout.LayoutParams(0,-2,1));l.addView(bottom);Button ok=button("VALIDER");ok.setTextSize(26);ok.setOnClickListener(v->submitTyped());l.addView(ok); }
  private void appendDigit(int d){if(paused)return;String cur=inputView.getTag()==null?"":String.valueOf(inputView.getTag());if(cur.length()<4){cur+=d;inputView.setTag(cur);inputView.setText(cur);}}
  private void refreshHud(){livesView.setText(selectedDiff.equals("Chrono")?"❤️ ∞":"❤️ "+playerLives);monsterView.setText("❤️ "+monsterHp+"/5");scoreView.setText("Score : "+score);}
  private int stageMax(){int mn=levelMin(selectedLevel),spread=Math.max(1,maxForGame-mn);double f=stage==1?.40:stage==2?.70:1.0;return Math.max(mn+2,mn+(int)Math.floor(spread*f));}

  private void nextQuestion(){ if(gameOver||paused)return; int max=stageMax(),min=levelMin(selectedLevel); int op=random.nextInt(selectedLevel.equals("CP")?2:3); int a,b; if(op==2){int mm=Math.max(3,Math.min(10,levelMult(selectedLevel)));a=3+random.nextInt(Math.max(1,mm-2));int maxB=Math.max(1,max/a);b=1+random.nextInt(maxB);correct=a*b;operationView.setText(a+" × "+b+" = ?");} else if(op==1){a=min+random.nextInt(Math.max(1,max-min+1));b=min+random.nextInt(Math.max(1,max-min+1));if(b>a){int t=a;a=b;b=t;}correct=a-b;operationView.setText(a+" − "+b+" = ?");} else {correct=min+random.nextInt(Math.max(1,max-min+1));a=min+random.nextInt(Math.max(1,correct-min+1));b=correct-a;operationView.setText(a+" + "+b+" = ?");} if(selectedDiff.equals("Légendaire")){inputView.setTag("");inputView.setText("—");} else {int gi=random.nextInt(3);int[] vals=new int[3];vals[gi]=correct;for(int i=0;i<3;i++)if(i!=gi){int v,tries=0;do{int delta=1+random.nextInt(Math.max(3,Math.min(15,max/10+2)));if(random.nextBoolean())delta=-delta;v=Math.max(0,correct+delta);tries++;}while((v==correct||contains(vals,i,v))&&tries<30);vals[i]=v;}for(int i=0;i<3;i++){answerButtons[i].setText(String.valueOf(vals[i]));answerButtons[i].setTag(vals[i]);answerButtons[i].setEnabled(true);}} }
  private boolean contains(int[] a,int upto,int v){for(int i=0;i<upto;i++)if(a[i]==v)return true;return false;}
  private void submitChoice(int ix){if(paused||gameOver)return;processAnswer((Integer)answerButtons[ix].getTag());}
  private void submitTyped(){if(paused||gameOver)return;String s=inputView.getTag()==null?"":String.valueOf(inputView.getTag());if(s.length()==0){Toast.makeText(this,"Tape une réponse",Toast.LENGTH_SHORT).show();return;}processAnswer(Integer.parseInt(s));}
  private void processAnswer(int value){boolean ok=value==correct;if(ok){score+=10;good++;monsterHp--;messageView.setText("🐶 ATTAQUE !   👾 « Aïe ! »   +10");}else{score=Math.max(0,score-5);bad++;if(!selectedDiff.equals("Chrono"))playerLives--;messageView.setText("👾 ATTAQUE !   👧🐶 « Ouille ! »   −5");}refreshHud();if(!selectedDiff.equals("Légendaire"))for(Button b:answerButtons)b.setEnabled(false);if(!selectedDiff.equals("Chrono")&&playerLives<=0){gameOver=true;operationView.postDelayed(()->showFinal(false),600);return;}if(monsterHp<=0){operationView.postDelayed(this::showStageSummary,600);return;}operationView.postDelayed(this::nextQuestion,600);}

  private void showStageSummary(){stopTimerTemporarily();LinearLayout l=root();l.addView(text("🎉 BRAVO !",38));l.addView(text("Tu as vaincu le monstre",25));l.addView(text(monsterName(),22));l.addView(text("✅ Bonnes réponses : "+good+"\n❌ Mauvaises réponses : "+bad+"\n🏆 Score total : "+score,20));Button next=button(stage<4?"Aller vaincre le monstre suivant":"Voir mon score final");next.setOnClickListener(v->{if(stage>=4)showFinal(true);else{stage++;showStageGame();if(selectedDiff.equals("Chrono"))resumeTimerAfterScreen();}});l.addView(next);setContentView(scroll(l));}
  private int starsFor(int s){if(s>190)return 5;if(s>=171)return 4;if(s>=141)return 3;if(s>100)return 2;return 1;}
  private void showFinal(boolean completed){gameOver=true;stopTimer();int old=prefs.getInt("record_"+selectedLevel+"_"+selectedDiff,0);boolean record=score>old;if(record)prefs.edit().putInt("record_"+selectedLevel+"_"+selectedDiff,score).apply();LinearLayout l=root();l.addView(text(completed?"🏆 VICTOIRE !":"💥 Le monstre a gagné",34));l.addView(text("Score final : "+score+" / 200",28));int st=starsFor(score);StringBuilder sb=new StringBuilder();for(int i=0;i<st;i++)sb.append("⭐");l.addView(text(sb.toString(),38));if(record)l.addView(text("🎉 RECORD ! 🎉",34));l.addView(text("✅ Bonnes réponses : "+good+"\n❌ Mauvaises réponses : "+bad,20));if(selectedDiff.equals("Chrono"))l.addView(text("⏱ Temps : "+formatTime(elapsedMs()),20));Button menu=button("Menu principal");menu.setOnClickListener(v->showHome());l.addView(menu);setContentView(scroll(l));}

  private void startTimer(){timerTick=new Runnable(){public void run(){if(timerView!=null&&!paused)timerView.setText(formatTime(elapsedMs()));if(!gameOver)timerHandler.postDelayed(this,100);}};timerHandler.post(timerTick);}  
  private long elapsedMs(){long now=paused?pausedAt:System.currentTimeMillis();return Math.max(0,now-startMs-pausedTotal);}  
  private String formatTime(long ms){long sec=ms/1000,min=sec/60,s=sec%60,t=(ms%1000)/100;return String.format(Locale.FRANCE,"%02d:%02d.%d",min,s,t);}  
  private void togglePause(Button b){if(!paused){paused=true;pausedAt=System.currentTimeMillis();b.setText("▶ Reprendre");messageView.setText("⏸ PAUSE");}else{paused=false;pausedTotal+=System.currentTimeMillis()-pausedAt;b.setText("⏸ Pause");messageView.setText("C’est reparti !");nextQuestion();}}
  private void stopTimer(){if(timerTick!=null)timerHandler.removeCallbacks(timerTick);}  
  private void stopTimerTemporarily(){if(selectedDiff.equals("Chrono")&&!paused){paused=true;pausedAt=System.currentTimeMillis();}}
  private void resumeTimerAfterScreen(){if(paused){paused=false;pausedTotal+=System.currentTimeMillis()-pausedAt;}}
  @Override public void onBackPressed(){showHome();}
}
