package com.roadwaffle;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.util.Random;

public class XsnowWallpaper extends WallpaperService {

  private final Handler handler = new Handler();

  @Override
  public Engine onCreateEngine() {
    //Log.d("Xsnow", "onCreateEngine");
    return new XsnowEngine();
  }

  class XsnowEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {

    // Time to sleep/post between frames
    private static final int SLEEP_TIME = 15;

    // chance that the wind will change this frame 1 in WIND_CHANGE_CHANCE chance
    private static final int WIND_CHANGE_CHANCE = 100;

    // change that the wind will die down this frame 1 in WIND_RESET_CHANCE chance
    private static final int WIND_RESET_CHANCE = 25;

    // MAX horizontal windspeed in either direction
    private static final float WIND_MAXSPEED = 20.0f;

    // Should we be drawing?
    private boolean visible;

    // Cache the paint object
    private Paint paint = new Paint();

    // current screen height/width
    private int height;
    private int width;

    // current screen offset
    private int xoffset = 0;

    private float currentWind = 0.0f;
    private float targetWind = 0.0f;

    // Configuration/prefs values
    private int numTrees = 10;
    private int numSnowflakes = 100;
    private float vSpeed = 12;
    private boolean wind = true;

    // Images loaded from apk
    private Drawable tannenbaumImage;
    private Drawable[] snowflakeImages;

    // Holders for the tree and snowflake objects
    private Tree[] trees;
    private Snowflake[] snowflakes;

    private Random rand = new Random();

    private class Tree {
      Tree(float top, float left, Drawable model) {
        this.top = top;
        this.left = left;
        this.model = model;
      }

      Drawable model;
      float top;
      float left;
    }

    private class Snowflake {
      Snowflake(float top, float left, float vspeed, float hspeed, Drawable model) {
        this.top = top;
        this.left = left;
        this.model = model;
        this.vspeed = vspeed;
        this.hspeed = hspeed;
      }

      Drawable model;
      float top;
      float left;
      float vspeed;
      float hspeed;
    }

    XsnowEngine() {
      //Log.d("Xsnow", "_ctor");
      Context context = getApplicationContext();

      tannenbaumImage = context.getResources().getDrawable(R.drawable.tannenbaum);
      snowflakeImages = new Drawable[7];
      snowflakeImages[0] = context.getResources().getDrawable(R.drawable.snow00);
      snowflakeImages[1] = context.getResources().getDrawable(R.drawable.snow01);
      snowflakeImages[2] = context.getResources().getDrawable(R.drawable.snow02);
      snowflakeImages[3] = context.getResources().getDrawable(R.drawable.snow03);
      snowflakeImages[4] = context.getResources().getDrawable(R.drawable.snow04);
      snowflakeImages[5] = context.getResources().getDrawable(R.drawable.snow05);
      snowflakeImages[6] = context.getResources().getDrawable(R.drawable.snow06);
    }

    private void reinit() {
      //Log.d("Xsnow", "reinit");

      reinitPrefs();

      trees = new Tree[numTrees];
      snowflakes = new Snowflake[numSnowflakes];

      // Note to self:  top/left of the screen is (0,0)... go figure

      for (int i = 0; i < numTrees; i++) {
        float top = rand.nextInt(height - 20) + 10;
        float left = rand.nextInt(width - 100) + 50;
        trees[i] = new Tree(top, left, tannenbaumImage);
      }

      for (int i = 0; i < numSnowflakes; i++) {
        float left = rand.nextFloat() * width;
        float top = rand.nextFloat() * height;
        float vspeed = (rand.nextFloat() * (0.75f * vSpeed)) + (0.75f * vSpeed);
        float hspeed = (rand.nextFloat() * (0.5f * vSpeed)) - (0.25f * vSpeed);

        snowflakes[i] = new Snowflake(top, left, vspeed, hspeed, snowflakeImages[rand.nextInt(7)]);
      }

      currentWind = 0.0f;
      targetWind = 0.0f;
    }

    private void reinitPrefs() {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
      prefs.registerOnSharedPreferenceChangeListener(this);
      try {
        numSnowflakes = Integer.valueOf(prefs.getString("numflakes", "100"));
        numTrees = Integer.valueOf(prefs.getString("numtrees", "20"));
        vSpeed = Float.valueOf(prefs.getString("vspeed", "5"));
        wind = prefs.getBoolean("wind", true);
      } catch (NumberFormatException nfe) {
        Toast.makeText(getApplicationContext(), "Invalid number specified " + nfe.getMessage(), 1000);
        Log.d("Xsnow", "reinitPrefs " + nfe);
        numSnowflakes = 100;
        numTrees = 20;
        wind = true;
        vSpeed = 5;
      }
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
      Log.d("Xsnow", "onSharedPreferenceChanged");
      reinit();
    }

    private final Runnable drawFrameRunnable = new Runnable() {
      public void run() {
        drawFrame();
      }
    };

    void drawFrame() {
      final SurfaceHolder holder = getSurfaceHolder();

      Canvas c = null;
      try {
        c = holder.lockCanvas();
        if (c != null) {
          drawFlakes(c);
          moveFlakes();
        }
      } finally {
        if (c != null)
          holder.unlockCanvasAndPost(c);
      }

      // remove and reschedule our callback
      handler.removeCallbacks(drawFrameRunnable);
      if (visible)
        handler.postDelayed(drawFrameRunnable, SLEEP_TIME);
    }

    private void drawFlakes(Canvas c) {
      c.save();
      c.drawColor(Color.BLACK);
      //c.translate(xoffset, 0);
      for (int i = 0; i < numTrees; i++) {
        Tree t = trees[i];
        float offset = (xoffset * 0.25f);
        c.drawBitmap(((BitmapDrawable) t.model).getBitmap(), t.left + offset, t.top, paint);
      }

      for (int i = 0; i < numSnowflakes; i++) {
        Snowflake s = snowflakes[i];
        c.drawBitmap(((BitmapDrawable) s.model).getBitmap(), s.left, s.top, paint);
      }
      c.restore();
    }

    private void moveFlakes() {
      if (wind) {
        if(targetWind == 0.0f) {
          if((rand.nextInt() % WIND_CHANGE_CHANCE)==0)
            targetWind= (rand.nextFloat() * (WIND_MAXSPEED * 2)) - WIND_MAXSPEED;
        } else {
          if((rand.nextInt() % WIND_RESET_CHANCE)==0)
            targetWind=0.0f;
        }

        float difference = (currentWind - targetWind);
        float step=Math.abs(targetWind) / 50;
        if (difference > 1) {
          // target is less than current by at least one ... subtract from current
          //currentWind -= 0.025f;
          currentWind -= step;
        } else if (difference < -1) {
          // target is greater than current by at least one ... add to current
          //currentWind += 0.025f;
          currentWind += step;
        }

      }

      for (int i = 0; i < numSnowflakes; i++) {
        Snowflake s = snowflakes[i];
        s.top = s.top + s.vspeed;
        s.left = s.left + s.hspeed + currentWind;

        if (s.top > height) {
          s.top = 0;
          s.left = rand.nextFloat() * width;
        }

        if (s.left < -5)
          s.left = width + 5;
        if (s.left > width + 5)
          s.left = -5;

        //if (i == 0)
        //  Log.d("Xsnow", "moving top=" + s.top + " left=" + s.left);
      }
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
      super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
      Log.d("Xsnow", "onOffsetsChanged xOffset=" + xOffset + " yOffset=" + yOffset + " xOffsetStep=" + xOffsetStep + " yOffsetStep=" + yOffsetStep + " xPixelOffset=" + xPixelOffset + " yPixelOffset=" + yPixelOffset);

      this.xoffset = xPixelOffset;
    }

    @Override
    public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      super.onSurfaceChanged(holder, format, width, height);

      //Log.d("Xsnow", "onSurfaceChanged format=" + format + " width=" + width + " height=" + height);

      this.width = width;
      this.height = height;

      reinit();
    }

    @Override
    public void onSurfaceDestroyed(SurfaceHolder holder) {
      //Log.d("Xsnow", "onSurfaceDestroyed");

      super.onSurfaceDestroyed(holder);

      visible = false;
      handler.removeCallbacks(drawFrameRunnable);
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
      //Log.d("Xsnow", "onVisibilityChanged " + visible);
      super.onVisibilityChanged(visible);
      this.visible = visible;
      if (!visible)
        handler.removeCallbacks(drawFrameRunnable);
      else
        handler.post(drawFrameRunnable);
    }

  }

}
