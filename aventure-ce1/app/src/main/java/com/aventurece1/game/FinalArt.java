package com.aventurece1.game;

import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.content.res.Resources;
import java.io.*;

public class FinalArt {
  public static Bitmap crop(Bitmap all,int x,int y,int w,int h){return Bitmap.createBitmap(all,x,y,w,h);}
  public static Bitmap load(Resources r){return BitmapFactory.decodeResource(r,R.drawable.final_charter);}
}
