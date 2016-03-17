package com.wyb.wechataudiotest.view;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import com.wyb.wechataudiotest.R;


public class AudioRecorderButton extends Button implements AudioManager.AudioStateListener {

    private static final int DISTANCE_Y_CANCEL = 50;
    private static final int STATE_NORMAL = 1;
    private static final int STATE_RECORDING = 2;
    private static final int STATE_WANT_CANCEL = 3;

    private int mCurState = STATE_NORMAL;
    private boolean isRecording = false;

    private DialogManager mDialogManage;

    private AudioManager mAudioManager;

    private float mTime;
    private boolean mReady;

    public AudioRecorderButton(Context context) {
        this(context, null);
    }


    public AudioRecorderButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mDialogManage = new DialogManager(getContext());

        String dir = Environment.getExternalStorageDirectory() + "/wechat_recorder_audios";
        mAudioManager = AudioManager.getInstance(dir);
        mAudioManager.setOnAudioStateListener(this);

        setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mReady = true;
                mAudioManager.prepareAudio();
                return false;
            }
        });
    }

    public interface AudioFinishRecorderListener{
        void onFinish(float seconds, String filePath);
    }

    private AudioFinishRecorderListener mListener;

    public void setAudioFinishRecorderListener(AudioFinishRecorderListener listener) {
        mListener = listener;
    }


    private Runnable mGetVoiceLevelRunnable = new Runnable() {
        @Override
        public void run() {
            while (isRecording) {
                try {
                    Thread.sleep(100);
                    mTime += 0.1f;
                    handler.sendEmptyMessage(MSG_VOICE_CHANGED);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private static final int MSG_AUDIO_PREPARED = 110;
    private static final int MSG_VOICE_CHANGED = 111;
    private static final int MSG_DIALOG_DISMISS = 112;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_AUDIO_PREPARED:
                    mDialogManage.showRecorderDialog();
                    isRecording = true;
                    new Thread(mGetVoiceLevelRunnable).start();
                    break;
                case MSG_VOICE_CHANGED:
                    mDialogManage.updateVoiceLevel(mAudioManager.getVoiceLevel(7));
                    break;
                case MSG_DIALOG_DISMISS:
                    mDialogManage.dismissDialog();
                    break;
            }
        }
    };

    @Override
    public void wellPrepared() {
        handler.sendEmptyMessage(MSG_AUDIO_PREPARED);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int action = event.getAction();
        int x = (int) event.getX();
        int y = (int) event.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                //isRecording = true;  //这句测试
                changeState(STATE_RECORDING);
                break;
            case MotionEvent.ACTION_MOVE:
                if (isRecording) {
                    //根据x, y的坐标，判断是否想要取消
                    if (wantToCancel(x, y)) {
                        changeState(STATE_WANT_CANCEL);
                    } else {
                        changeState(STATE_RECORDING);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (!mReady) {
                    reset();
                    return super.onTouchEvent(event);
                }
                if (!isRecording || mTime < 0.6f) {
                    mDialogManage.tooShort();
                    mAudioManager.cancel();
                    handler.sendEmptyMessageDelayed(MSG_DIALOG_DISMISS, 1300);
                } else if (mCurState == STATE_RECORDING) {
                    mDialogManage.dismissDialog();
                    mAudioManager.release();

                    if (mListener != null) {
                        mListener.onFinish(mTime, mAudioManager.getCurrentFilePath());
                    }
                } else if (mCurState == STATE_WANT_CANCEL) {
                    //cancel
                    mDialogManage.dismissDialog();
                    mAudioManager.cancel();
                }
                reset();
                break;
        }

        return super.onTouchEvent(event);
    }

    //恢复状态及标志位
    private void reset() {
        isRecording = false;
        mReady = false;
        mTime = 0;
        changeState(STATE_NORMAL);
    }

    private boolean wantToCancel(int x, int y) {
        if (x < 0 || x > getWidth()) {
            return true;
        }
        if (y < -DISTANCE_Y_CANCEL || y > getHeight() + DISTANCE_Y_CANCEL) {
            return true;
        }
        return false;
    }


    private void changeState(int state) {
        if (mCurState != state) {
            mCurState = state;
            switch (state) {
                case STATE_NORMAL:
                    setBackgroundResource(R.drawable.btn_recorder_normal);
                    setText(R.string.str_recorder_normal);
                    break;
                case STATE_RECORDING:
                    setBackgroundResource(R.drawable.btn_recording);
                    setText(R.string.str_recorder_recording);
                    if (isRecording) {
                        mDialogManage.recording();
                    }
                    break;
                case STATE_WANT_CANCEL:
                    setBackgroundResource(R.drawable.btn_recording);
                    setText(R.string.str_recorder_want_cancel);
                    mDialogManage.wantCancel();
                    break;
            }
        }
    }


}
