package me.jefferey.clockheads;

import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.Vibrator;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
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

    private final Point mDeletePoint = new Point();
    private final Point mEditPoint = new Point();
    private Point mDisplaySize = new Point();

    private WindowManager mWindowManager;
    private TextView mClockView;
    private CountDownTimer mCurrentCountDownTimer;
    private Vibrator mVibrator;

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LayoutInflater inflater = LayoutInflater.from(this);
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        Display display =  mWindowManager.getDefaultDisplay();
        display.getSize(mDisplaySize);
        resetEditPoints();
        mClockView = (TextView) inflater.inflate(R.layout.service_clock_head, null);
        mClockView.setOnTouchListener(mClockTouchListener);
        mClockLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        mWindowManager.addView(mClockView, mClockLayoutParams);

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

    private void centerClockOn(Point point) {
        mClockLayoutParams.x = point.x - (mClockView.getWidth() / 2);
        mClockLayoutParams.y = point.y - (mClockView.getHeight() / 2);
        mWindowManager.updateViewLayout(mClockView, mClockLayoutParams);
    }

    private void resetEditPoints() {
        int displayCenter = mDisplaySize.x / 2;
        Resources res = getResources();
        int editEdigeOffset = res.getDimensionPixelSize(R.dimen.edit_edge_offset);
        mDeletePoint.set(displayCenter, mDisplaySize.y - editEdigeOffset);
        mEditPoint.set(displayCenter, editEdigeOffset);
    }

    private class ClockOnTouchListener implements View.OnTouchListener {

        private float initialTouchX;
        private float initialTouchY;

        private int initialX;
        private int initialY;

        private boolean inDelete;
        private boolean inEdit;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startTracking(event);
                    return false;
                case MotionEvent.ACTION_UP:
                    resetEditPoints();
                    if (inDelete) {
                        deleteClock();
                    }
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (hasMovedToDelete(event)) {
                        onTouchInDelete();
                        inDelete = true;
                        inEdit = false;
                    } else if (hasMovedToEdit(event)){
                        onTouchInEdit();
                        inDelete = false;
                        inEdit = true;
                    } else {
                        updateClockPosition(event);
                        inDelete = false;
                        inEdit = false;
                    }
                    return true;
            }
            return false;
        }

        private void deleteClock() {
            stopSelf();
        }

        private void startTracking(MotionEvent event) {
            initialX = mClockLayoutParams.x;
            initialY = mClockLayoutParams.y;
            initialTouchX = event.getRawX();
            initialTouchY = event.getRawY();
        }

        private void updateClockPosition(MotionEvent event) {
            mClockLayoutParams.x = initialX + (int) (event.getRawX() - initialTouchX);
            mClockLayoutParams.y = initialY + (int) (event.getRawY() - initialTouchY);
            mWindowManager.updateViewLayout(mClockView, mClockLayoutParams);
        }

        private void onTouchInDelete() {
            if (!inDelete) {
                mVibrator.vibrate(50);
            }
            centerClockOn(mDeletePoint);
        }

        private void onTouchInEdit() {
            if (!inEdit) {
                mVibrator.vibrate(50);
            }
            centerClockOn(mEditPoint);
        }

        private boolean hasMovedToDelete(MotionEvent event) {
            return inCircle((int) event.getRawX(), (int) event.getRawY(), 200, mDeletePoint);
        }

        private boolean hasMovedToEdit(MotionEvent event) {
            return inCircle((int) event.getRawX(), (int) event.getRawY(), 200, mEditPoint);
        }

        private boolean inCircle(int x, int y, int radius, Point center) {
            double xComp = Math.pow((center.x - x), 2);
            double yComp = Math.pow((center.y - y), 2);
            double distance = Math.sqrt(xComp + yComp);
            return distance <= radius;
        }
    }
}
