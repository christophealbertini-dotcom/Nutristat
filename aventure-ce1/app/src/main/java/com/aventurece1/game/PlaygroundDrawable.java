package com.aventurece1.game;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.Random;

/** Decorative game-world background inspired by the Aventure CE1 visual charter. */
public class PlaygroundDrawable extends Drawable {
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Random r=new Random(7);
  private final boolean battle;
  public PlaygroundDrawable(boolean battle){this.battle=battle;}
  private void color(int c){p.setColor(c);p.setStyle(Paint.Style.FILL);p.setShader(null);}
  @Override public void draw(Canvas c){
    Rect b=getBounds(); float w=b.width(),h=b.height();
    LinearGradient sky=new LinearGradient(0,0,0,h*.62f,Color.rgb(48,190,245),Color.rgb(177,239,255),Shader.TileMode.CLAMP);p.setShader(sky);c.drawRect(0,0,w,h,p);p.setShader(null);
    color(Color.argb(220,255,255,255));cloud(c,w*.12f,h*.11f,w*.07f);cloud(c,w*.76f,h*.16f,w*.08f);cloud(c,w*.50f,h*.07f,w*.055f);
    color(Color.rgb(211,176,244));float cx=w*.79f,cy=h*.31f,s=w*.055f;c.drawRect(cx-s*1.2f,cy-s*.4f,cx+s*1.2f,cy+s*2.7f,p);c.drawRect(cx-s*2.3f,cy+s*.6f,cx-s*1.2f,cy+s*2.7f,p);c.drawRect(cx+s*1.2f,cy+s*.5f,cx+s*2.3f,cy+s*2.7f,p);
    color(Color.rgb(236,105,158));tri(c,cx-s*1.2f,cy-s*.4f,cx,cy-s*2.0f,cx+s*1.2f,cy-s*.4f);tri(c,cx-s*2.3f,cy+s*.6f,cx-s*1.75f,cy-s*.7f,cx-s*1.2f,cy+s*.6f);tri(c,cx+s*1.2f,cy+s*.5f,cx+s*1.75f,cy-s*.8f,cx+s*2.3f,cy+s*.5f);color(Color.rgb(255,235,91));c.drawCircle(cx,cy+s*.45f,s*.22f,p);
    color(Color.rgb(103,205,91));c.drawOval(-w*.16f,h*.43f,w*.62f,h*.85f,p);color(Color.rgb(75,186,70));c.drawOval(w*.30f,h*.42f,w*1.15f,h*.87f,p);
    LinearGradient grass=new LinearGradient(0,h*.60f,0,h,Color.rgb(101,215,83),Color.rgb(42,154,63),Shader.TileMode.CLAMP);p.setShader(grass);c.drawRect(0,h*.60f,w,h,p);p.setShader(null);
    color(Color.rgb(249,221,158));Path path=new Path();path.moveTo(w*.46f,h);path.lineTo(w*.57f,h);path.lineTo(w*.72f,h*.59f);path.lineTo(w*.62f,h*.59f);path.close();c.drawPath(path,p);
    for(int i=0;i<14;i++){float x=(i+1)*w/15f;float y=h*(.59f+.025f*(i%3));color(i%2==0?Color.rgb(45,161,67):Color.rgb(67,180,75));c.drawCircle(x,y,w*.045f,p);}
    int[] flower={Color.rgb(255,83,140),Color.rgb(255,208,50),Color.rgb(135,81,225),Color.WHITE};for(int i=0;i<24;i++){float x=(float)(r.nextDouble()*w),y=h*(.66f+(float)r.nextDouble()*.28f);float rr=w*(.006f+(float)r.nextDouble()*.006f);color(flower[i%flower.length]);c.drawCircle(x,y,rr,p);c.drawCircle(x+rr*1.7f,y,rr,p);c.drawCircle(x-rr*1.7f,y,rr,p);c.drawCircle(x,y+rr*1.7f,rr,p);color(Color.rgb(255,220,45));c.drawCircle(x,y,rr*.65f,p);}
    if(battle){LinearGradient vg=new LinearGradient(0,h*.45f,0,h,Color.TRANSPARENT,Color.argb(60,0,70,20),Shader.TileMode.CLAMP);p.setShader(vg);c.drawRect(0,h*.45f,w,h,p);p.setShader(null);for(int i=0;i<8;i++){color(Color.argb(190,255,244,90));float x=w*(.08f+i*.12f),y=h*(.30f+(i%3)*.055f);c.drawCircle(x,y,w*.006f,p);}}
  }
  private void cloud(Canvas c,float x,float y,float s){c.drawCircle(x,y,s*.55f,p);c.drawCircle(x+s*.5f,y+s*.03f,s*.42f,p);c.drawCircle(x-s*.45f,y+s*.08f,s*.37f,p);c.drawOval(x-s*.72f,y+s*.05f,x+s*.82f,y+s*.48f,p);}
  private void tri(Canvas c,float x1,float y1,float x2,float y2,float x3,float y3){Path q=new Path();q.moveTo(x1,y1);q.lineTo(x2,y2);q.lineTo(x3,y3);q.close();c.drawPath(q,p);}
  @Override public void setAlpha(int a){p.setAlpha(a);} @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);} @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}
