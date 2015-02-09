package me.jefferey.clockheads;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.CountDownTimer;
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

    private static final int OFFSCREEN_OFFSET = 200;

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

    // Initialize the layout params for the delete view
    private final WindowManager.LayoutParams mEditLayoutParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);

    private final Point mDeletePoint = new Point();
    private final Point mEditPoint = new Point();

    private WindowManager mWindowManager;
    private TextView mClockView;
    private ImageView mDeleteView;
    private ImageView mEditView;
    private ImageView mThrottle;
    private CountDownTimer mCurrentCountDownTimer;
    private Vibrator mVibrator;

    private ValueAnimator mDeleteAnimator;
    private ValueAnimator mEditAnimator;

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

    private class ClockOnTouchListener implements View.OnTouchListener {

        private float initialTouchX;
        private float initialTouchY;

        private int initialX;
        private int initialY;

        private boolean inDelete;
        private boolean inEdit;

        private final Point adjustedDeletePoint = new Point();
        private final Point adjustedEditPoint = new Point();

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Log.v("Event", event.toString());
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

        private void startTracking(MotionEvent event) {
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
            if (inDelete) {
                stopSelf();
            }
            removeDeletePoint(event);
            removeEditPoint(event);
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
            if (!inDelete) {
                mVibrator.vibrate(20);
            }
            centerViewOn(adjustedDeletePoint, mClockView, mClockLayoutParams);
        }

        private void onTouchInEdit() {
            if (!inEdit) {
                mVibrator.vibrate(20);
            }
            centerViewOn(adjustedEditPoint, mClockView, mClockLayoutParams);
        }

        private boolean hasMovedToDelete(MotionEvent event) {
            return inCircle((int) event.getRawX(), (int) event.getRawY(), OFFSCREEN_OFFSET, mDeletePoint);
        }

        private boolean hasMovedToEdit(MotionEvent event) {
            return inCircle((int) event.getRawX(), (int) event.getRawY(), OFFSCREEN_OFFSET, mEditPoint);
        }

        private boolean inCircle(int x, int y, int radius, Point center) {
            double xComp = Math.pow((center.x - x), 2);
            double yComp = Math.pow((center.y - y), 2);
            double distance = Math.sqrt(xComp + yComp);
            return distance <= radius;
        }
    }
}
