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

    // Initialize the layout params for the clock view
    private final WindowManager.LayoutParams mDeleteLayoutParams = new WindowManager.LayoutParams(
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
    private ImageView mDeleteView;
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
        mClockView = (TextView) inflater.inflate(R.layout.service_clock_head, null);
        mClockLayoutParams.gravity = Gravity.TOP | Gravity.START;
        mClockView.setOnTouchListener(mClockTouchListener);
        mWindowManager.addView(mClockView, mClockLayoutParams);

        mDeleteView = new ImageView(this);
        mDeleteView.setImageResource(R.drawable.delete_square);
        mDeleteLayoutParams.gravity = Gravity.TOP | Gravity.START;
        initEditPoints();
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

    private void centerViewOn(Point point, View view, WindowManager.LayoutParams layoutParams) {
        layoutParams.x = point.x - (view.getWidth() / 2);
        layoutParams.y = point.y - (view.getHeight() / 2);
        mWindowManager.updateViewLayout(view, layoutParams);
    }

    private void initEditPoints() {
        int displayCenter = mDisplaySize.x / 2;
        Resources res = getResources();
        int editEdgeOffset = res.getDimensionPixelSize(R.dimen.edit_edge_offset);
        mDeletePoint.set(displayCenter, mDisplaySize.y - editEdgeOffset);
        mEditPoint.set(displayCenter, editEdgeOffset);
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
                    if (inDelete) {
                        deleteClock();
                    }
                    mWindowManager.removeView(mDeleteView);
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
                    centerViewOn(mDeletePoint, mDeleteView, mDeleteLayoutParams);
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
            mWindowManager.addView(mDeleteView, mDeleteLayoutParams);
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
            centerViewOn(mDeletePoint, mClockView, mClockLayoutParams);
        }

        private void onTouchInEdit() {
            if (!inEdit) {
                mVibrator.vibrate(50);
            }
            centerViewOn(mEditPoint, mClockView, mClockLayoutParams);
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
