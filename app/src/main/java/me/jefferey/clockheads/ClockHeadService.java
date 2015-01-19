package me.jefferey.clockheads;

import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

public class ClockHeadService extends Service {


    private final ClockOnTouchListener mClockTouchListener =  new ClockOnTouchListener();
    // Initialize the layout params for the clock view
    private final WindowManager.LayoutParams mClockLayoutParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);

    // Initialize the layout params for the trash can view
    private final WindowManager.LayoutParams mRemoveClockLayoutParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);


    private WindowManager mWindowManager;
    private TextView mClockView;
    private ImageView mRemoveClockView;
    private CountDownTimer mCurrentCountDownTimer;
    private long mCurrentTimeRemaining;

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Resources res = getResources();
        LayoutInflater inflater = LayoutInflater.from(this);
        int viewOffset = res.getDimensionPixelOffset(R.dimen.init_clock_top_padding);
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mClockView = (TextView) inflater.inflate(R.layout.service_clock_head, null);
        mClockView.setOnTouchListener(mClockTouchListener);
        mClockLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        mClockLayoutParams.y = viewOffset;
        mWindowManager.addView(mClockView, mClockLayoutParams);

        mRemoveClockView = new ImageView(this);
        mRemoveClockView.setImageResource(R.mipmap.ic_trash);
        mRemoveClockLayoutParams.gravity = Gravity.CENTER | Gravity.CENTER;
        mRemoveClockLayoutParams.y = viewOffset;

        setTimerFor(100000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mClockView != null) {
            mWindowManager.removeView(mClockView);
        }
    }

    private void setTimerFor(long timeInMiliseconds) {

        mCurrentCountDownTimer = new CountDownTimer(timeInMiliseconds, 1000) {

            public void onTick(long millisUntilFinished) {
                mCurrentTimeRemaining = millisUntilFinished;
                long secondsUntilFinished = millisUntilFinished / 1000;
                StringBuilder builder = new StringBuilder();
                long hours = secondsUntilFinished / 36000;
                long minutes = (secondsUntilFinished % 3600) / 60;
                long seconds = secondsUntilFinished % 60;
                if (hours > 0) {
                    builder.append(String.format(Locale.US, "%02d:", hours));
                }
                builder.append(String.format(Locale.US, "%02d:", minutes));
                builder.append(String.format(Locale.US, "%02d", seconds));
                mClockView.setText(builder.toString());
            }

            public void onFinish() {
            }
        };
        mCurrentCountDownTimer.start();
    }


    private class ClockOnTouchListener implements View.OnTouchListener {

        private float initialTouchX;
        private float initialTouchY;

        private int initialX;
        private int initialY;

        private boolean showingRemoveView;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = mClockLayoutParams.x;
                    initialY = mClockLayoutParams.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return false;
                case MotionEvent.ACTION_UP:
                    mWindowManager.removeView(mRemoveClockView);
                    showingRemoveView = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    mClockLayoutParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                    mClockLayoutParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                    mWindowManager.updateViewLayout(mClockView, mClockLayoutParams);
                    if (!showingRemoveView) {
                        mWindowManager.addView(mRemoveClockView, mRemoveClockLayoutParams);
                        showingRemoveView = true;
                    }

                    Point point = new Point();
                    Display display =  mWindowManager.getDefaultDisplay();
                    display.getSize(point);
                    int bottomOffset = point.y - mClockLayoutParams.y - 300;

                    if ((mClockLayoutParams.x) > -20 && (mClockLayoutParams.x) < 20 && bottomOffset > -320 && bottomOffset < 320) {
                        mClockView.setBackgroundResource(R.color.red);
                    } else {
                        mClockView.setBackgroundResource(R.color.transparent_grey);
                    }
                    return true;
            }
            return false;
        }
    }
}
