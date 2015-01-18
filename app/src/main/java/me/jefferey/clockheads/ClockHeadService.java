package me.jefferey.clockheads;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Locale;

public class ClockHeadService extends Service {

    private WindowManager mWindowManager;
    private TextView mClockView;
    private WindowManager.LayoutParams mClockLayoutParams;
    private ClockOnTouchListener mClockTouchListener =  new ClockOnTouchListener();
    private CountDownTimer mTimer;

    public ClockHeadService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater inflater = LayoutInflater.from(this);
        mClockView = (TextView) inflater.inflate(R.layout.service_clock_head, null);
        mClockView.setOnTouchListener(mClockTouchListener);
        mClockLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        mClockLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        mClockLayoutParams.x = 0;
        mClockLayoutParams.y = 500;

        mWindowManager.addView(mClockView, mClockLayoutParams);
        mTimer = new CountDownTimer(3000000, 1000) {

            public void onTick(long millisUntilFinished) {
                long secondsUntilFinished = millisUntilFinished / 1000;
                StringBuilder builder = new StringBuilder();
                long hours = secondsUntilFinished / 36000;
                long minutes = (secondsUntilFinished % 3600) / 60;
                long seconds = secondsUntilFinished % 60;
                if (hours > 0) {
                    builder.append(String.format(Locale.US, "%02d:", hours));
                }
                if (minutes > 0) {
                    builder.append(String.format(Locale.US, "%02d:", minutes));
                }
                builder.append(String.format(Locale.US, "%02d", seconds));
                mClockView.setText(builder.toString());
            }

            public void onFinish() {
                mClockView.setText("00");
            }
        }.start();
        mTimer.
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mClockView != null) {
            mWindowManager.removeView(mClockView);
        }
    }

    private class ClockOnTouchListener implements View.OnTouchListener {

        private float initialTouchX;
        private float initialTouchY;

        private int initialX;
        private int initialY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = mClockLayoutParams.x;
                    initialY = mClockLayoutParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_UP:
                    break;
                case MotionEvent.ACTION_MOVE:
                    mClockLayoutParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                    mClockLayoutParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                    mWindowManager.updateViewLayout(mClockView, mClockLayoutParams);
                    return true;
            }
            return false;
        }
    }
}
