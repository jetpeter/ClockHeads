package me.jefferey.clockheads;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.util.Log;
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

    private static final int OFFSCREEN_OFFSET = 250;
    private static final int IN_CIRCLE_SLOP = 200;

    private final ClockOnTouchListener mClockTouchListener =  new ClockOnTouchListener();
    // Initialize the layout params for the clock view
    private final WindowManager.LayoutParams mClockLayoutParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);

    // Initialize the layout params for the delete view
    private final WindowManager.LayoutParams mDeleteLayoutParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);

    // Initialize the layout params for the edit view
    private final WindowManager.LayoutParams mEditLayoutParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);

    // Initialize the layout params for the throttle view
    private final WindowManager.LayoutParams mThrottleLayoutParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);

    private final Point mDeletePoint = new Point();
    private final Point mEditPoint = new Point();
    private final Point mThrottleCenter = new Point();

    private CountDownTimer mCurrentCountDownTimer;
    private long mTimeRemainingMiliseconds;
    private Vibrator mVibrator;

    private WindowManager mWindowManager;
    private TextView mClockView;
    private ImageView mDeleteView;
    private ImageView mEditView;
    private View mThrottleView;

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
        mClockView = (TextView) inflater.inflate(R.layout.service_clock_head, null);
        mClockLayoutParams.gravity = Gravity.TOP | Gravity.START;
        mClockView.setOnTouchListener(mClockTouchListener);
        mWindowManager.addView(mClockView, mClockLayoutParams);

        mDeleteView = new ImageView(this);
        mDeleteView.setImageResource(R.drawable.delete_square);
        mDeleteLayoutParams.gravity = Gravity.TOP | Gravity.START;

        mEditView = new ImageView(this);
        mEditView.setImageResource(R.drawable.edit_square);
        mEditLayoutParams.gravity = Gravity.TOP | Gravity.START;

        mThrottleView = inflater.inflate(R.layout.throttle, null);
        mThrottleView.setOnTouchListener(new ThrottleOnTouchListener());
        mThrottleLayoutParams.gravity = Gravity.TOP | Gravity.START;
        mThrottleLayoutParams.width = getResources().getDimensionPixelOffset(R.dimen.throttle_size);
        mThrottleLayoutParams.height = getResources().getDimensionPixelOffset(R.dimen.throttle_size);
        setClockText(0);
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
                setClockText(millisUntilFinished);
                mTimeRemainingMiliseconds = millisUntilFinished;
            }
            public void onFinish() {
            }
        };
        mCurrentCountDownTimer.start();
    }

    private void setClockText(long timeInMiliseconds) {
        long secondsUntilFinished = timeInMiliseconds / 1000;
        StringBuilder builder = new StringBuilder();
        long hours = secondsUntilFinished / 3600;
        long minutes = (secondsUntilFinished % 3600) / 60;
        long seconds = secondsUntilFinished % 60;
        Log.v("Hours", "" + hours);
        if (hours > 0) {
            builder.append(String.format(Locale.US, "%02d:", hours));
        }
        builder.append(String.format(Locale.US, "%02d:", minutes));
        builder.append(String.format(Locale.US, "%02d", seconds));
        mClockView.setText(builder.toString());
    }

    private void centerViewOn(Point point, View view, WindowManager.LayoutParams layoutParams) {
        layoutParams.x = point.x - (view.getWidth() / 2);
        layoutParams.y = point.y - (view.getHeight() / 2);
        mWindowManager.updateViewLayout(view, layoutParams);
    }

    private class ClockOnTouchListener implements View.OnTouchListener {

        private float initialTouchX;
        private float initialTouchY;

        private int initialX;
        private int initialY;

        private boolean currentTouchInDelete;
        private boolean currentTouchInEdit;
        private boolean inEditMode;


        private final Point adjustedDeletePoint = new Point();
        private final Point adjustedEditPoint = new Point();

        private ValueAnimator mDeleteAnimator;
        private ValueAnimator mEditAnimator;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startTracking(event);
                    return false;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    stopTracking(event);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    updateDeletePointPosition(event);
                    updateEditPointPosition(event);
                    if (hasMovedToDelete(event)) {
                        onTouchInDelete();
                        currentTouchInDelete = true;
                        currentTouchInEdit = false;
                    } else if (hasMovedToEdit(event)){
                        onTouchInEdit();
                        currentTouchInDelete = false;
                        currentTouchInEdit = true;
                    } else {
                        updateClockPosition(event);
                        currentTouchInDelete = false;
                        currentTouchInEdit = false;
                    }
                    return true;
            }
            return false;
        }

        private void startTracking(MotionEvent event) {
            if (inEditMode) {
                mWindowManager.removeView(mThrottleView);
                inEditMode = false;
            }
            initialX = mClockLayoutParams.x;
            initialY = mClockLayoutParams.y;
            initialTouchX = event.getRawX();
            initialTouchY = event.getRawY();
            Point displaySize = new Point();
            Display display =  mWindowManager.getDefaultDisplay();
            display.getSize(displaySize);
            int displayCenterX = displaySize.x / 2;
            if (mEditAnimator != null && mEditAnimator.isRunning()) {
                mEditAnimator.cancel();
            } else {
                mEditPoint.y = -OFFSCREEN_OFFSET;
                mWindowManager.addView(mEditView, mEditLayoutParams);
            }
            if (mDeleteAnimator != null && mDeleteAnimator.isRunning()) {
                mDeleteAnimator.cancel();
            } else {
                mDeletePoint.y = displaySize.y + OFFSCREEN_OFFSET;
                mWindowManager.addView(mDeleteView, mDeleteLayoutParams);
            }
            initDeletePoint(displayCenterX, displaySize.y, event);
            initEditPoint(displayCenterX, event);
        }

        private void initDeletePoint(int displayCenterX, int displayHeight, final MotionEvent event) {
            Resources res = getResources();
            int editEdgeOffset = res.getDimensionPixelSize(R.dimen.edit_edge_offset);
            mDeletePoint.x = displayCenterX;
            ValueAnimator animator = ValueAnimator.ofInt(mDeletePoint.y, displayHeight - editEdgeOffset);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mDeletePoint.y = (int) animation.getAnimatedValue();
                    updateDeletePointPosition(event);
                }
            });
            animator.start();
        }

        private void initEditPoint(int displayCenterX, final MotionEvent event) {
            Resources res = getResources();
            int editEdgeOffset = res.getDimensionPixelSize(R.dimen.edit_edge_offset);
            mEditPoint.x = displayCenterX;
            ValueAnimator animator = ValueAnimator.ofInt(mEditPoint.y, editEdgeOffset);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mEditPoint.y = (int) animation.getAnimatedValue();
                    updateEditPointPosition(event);
                }
            });
            animator.start();
        }


        private void updateDeletePointPosition(MotionEvent event) {
            int adjustX = (mDeletePoint.x - (int) event.getRawX()) / 20;
            int adjustY = (mDeletePoint.y - (int) event.getRawY()) / 20;

            adjustedDeletePoint.set(mDeletePoint.x - adjustX, mDeletePoint.y - adjustY);
            centerViewOn(adjustedDeletePoint, mDeleteView, mDeleteLayoutParams);
        }

        private void updateEditPointPosition(MotionEvent event) {
            int adjustX = (mEditPoint.x - (int) event.getRawX()) / 20;
            int adjustY = (mEditPoint.y - (int) event.getRawY()) / 20;

            adjustedEditPoint.set(mEditPoint.x - adjustX, mEditPoint.y - adjustY);
            centerViewOn(adjustedEditPoint, mEditView, mEditLayoutParams);
        }

        private void stopTracking(MotionEvent event) {
            if (currentTouchInDelete) {
                stopSelf();
            }
            if (currentTouchInEdit) {
                inEditMode = true;
                if (mCurrentCountDownTimer != null) {
                    mCurrentCountDownTimer.cancel();
                }
                addThrottle();
            }
            currentTouchInDelete = false;
            currentTouchInEdit = false;
            removeDeletePoint(event);
            removeEditPoint(event);
        }

        private void addThrottle() {
            mThrottleLayoutParams.x = mThrottleCenter.x = mClockLayoutParams.x + (mClockView.getWidth());
            mThrottleLayoutParams.y = mThrottleCenter.y = mClockLayoutParams.y + (mClockView.getHeight() / 4);
            mWindowManager.addView(mThrottleView, mThrottleLayoutParams);
        }

        private void removeDeletePoint(final MotionEvent event) {
            Point displaySize = new Point();
            Display display =  mWindowManager.getDefaultDisplay();
            display.getSize(displaySize);
            mDeleteAnimator = ValueAnimator.ofInt(mDeletePoint.y, displaySize.y + OFFSCREEN_OFFSET);
            mDeleteAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mDeletePoint.y = (int) animation.getAnimatedValue();
                    updateDeletePointPosition(event);
                }
            });
            mDeleteAnimator.addListener(new Animator.AnimatorListener() {
                boolean canceled = false;
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!canceled) {
                        mWindowManager.removeView(mDeleteView);
                    }
                    mDeleteAnimator = null;
                }
                @Override
                public void onAnimationCancel(Animator animation) {
                    canceled = true;
                }
                @Override
                public void onAnimationStart(Animator animation) { }

                @Override
                public void onAnimationRepeat(Animator animation) { }
            });
            mDeleteAnimator.start();
        }

        private void removeEditPoint(final MotionEvent event) {
            mEditAnimator = ValueAnimator.ofInt(mEditPoint.y, -OFFSCREEN_OFFSET);
            mEditAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mEditPoint.y = (int) animation.getAnimatedValue();
                    updateEditPointPosition(event);
                }
            });
            mEditAnimator.addListener(new Animator.AnimatorListener() {
                boolean canceled = false;
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!canceled) {
                        mWindowManager.removeView(mEditView);
                    }
                    mEditAnimator = null;
                }
                @Override
                public void onAnimationCancel(Animator animation) {
                    canceled = true;
                }
                @Override
                public void onAnimationStart(Animator animation) { }
                @Override
                public void onAnimationRepeat(Animator animation) { }
            });
            mEditAnimator.start();
        }

        private void updateClockPosition(MotionEvent event) {
            mClockLayoutParams.x = initialX + (int) (event.getRawX() - initialTouchX);
            mClockLayoutParams.y = initialY + (int) (event.getRawY() - initialTouchY);
            mWindowManager.updateViewLayout(mClockView, mClockLayoutParams);
        }

        private void onTouchInDelete() {
            if (!currentTouchInDelete) {
                mVibrator.vibrate(20);
            }
            centerViewOn(adjustedDeletePoint, mClockView, mClockLayoutParams);
        }

        private void onTouchInEdit() {
            if (!currentTouchInEdit) {
                mVibrator.vibrate(20);
            }
            centerViewOn(adjustedEditPoint, mClockView, mClockLayoutParams);
        }

        private boolean hasMovedToDelete(MotionEvent event) {
            return inCircle((int) event.getRawX(), (int) event.getRawY(), IN_CIRCLE_SLOP, mDeletePoint);
        }

        private boolean hasMovedToEdit(MotionEvent event) {
            return inCircle((int) event.getRawX(), (int) event.getRawY(), IN_CIRCLE_SLOP, mEditPoint);
        }

        private boolean inCircle(int x, int y, int radius, Point center) {
            double xComp = Math.pow((center.x - x), 2);
            double yComp = Math.pow((center.y - y), 2);
            double distance = Math.sqrt(xComp + yComp);
            return distance <= radius;
        }
    }

    private class ThrottleOnTouchListener implements View.OnTouchListener {

        private float initialTouchY;

        private int rateOfChange = 0;

        private Handler handler = new Handler();

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initialTouchY = event.getRawY();
                    rateOfChange = 0;
                    handler.post(timeChangingRunnable);
                    return false;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    handler.removeCallbacks(timeChangingRunnable);
                    mThrottleLayoutParams.x = mThrottleCenter.x;
                    mThrottleLayoutParams.y = mThrottleCenter.y;
                    mWindowManager.updateViewLayout(mThrottleView, mThrottleLayoutParams);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    mThrottleLayoutParams.y = mThrottleCenter.y + (int) (event.getRawY() - initialTouchY);
                    mWindowManager.updateViewLayout(mThrottleView, mThrottleLayoutParams);
                    float offset = initialTouchY - event.getRawY();
                    rateOfChange = (offset > 0 ? 1 : -1) * (int) (Math.pow(2, Math.abs(offset) / 20) + 1000);
                    Log.v("", "Rate of change: " + rateOfChange + "    Offset: " + offset);
                    return true;
            }
            return false;
        }

        private Runnable timeChangingRunnable = new Runnable() {
            @Override
            public void run() {
                if (mTimeRemainingMiliseconds + rateOfChange > 0) {
                    mTimeRemainingMiliseconds += rateOfChange;
                } else {
                    mTimeRemainingMiliseconds = 0;
                }
                setClockText(mTimeRemainingMiliseconds);
                handler.postDelayed(timeChangingRunnable, 100);
            }
        };

    }
}
