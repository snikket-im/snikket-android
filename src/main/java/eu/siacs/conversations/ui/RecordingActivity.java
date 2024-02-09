package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.databinding.DataBindingUtil;

import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityRecordingBinding;
import eu.siacs.conversations.ui.util.SettingsUtils;
import eu.siacs.conversations.utils.ThemeHelper;
import eu.siacs.conversations.utils.TimeFrameUtils;

public class RecordingActivity extends Activity implements View.OnClickListener {

    private ActivityRecordingBinding binding;

    private MediaRecorder mRecorder;
    private long mStartTime = 0;

    private final CountDownLatch outputFileWrittenLatch = new CountDownLatch(1);

    private final Handler mHandler = new Handler();
    private final Runnable mTickExecutor =
            new Runnable() {
                @Override
                public void run() {
                    tick();
                    mHandler.postDelayed(mTickExecutor, 100);
                }
            };

    private File mOutputFile;

    private FileObserver mFileObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeHelper.findDialog(this));
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_recording);
        this.binding.cancelButton.setOnClickListener(this);
        this.binding.shareButton.setOnClickListener(this);
        this.setFinishOnTouchOutside(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SettingsUtils.applyScreenshotPreventionSetting(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!startRecording()) {
            this.binding.shareButton.setEnabled(false);
            this.binding.timer.setTextAppearance(this, R.style.TextAppearance_Conversations_Title);
            this.binding.timer.setText(R.string.unable_to_start_recording);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mRecorder != null) {
            mHandler.removeCallbacks(mTickExecutor);
            stopRecording(false);
        }
        if (mFileObserver != null) {
            mFileObserver.stopWatching();
        }
    }

    private static final Set<String> AAC_SENSITIVE_DEVICES =
            new ImmutableSet.Builder<String>()
                    .add("FP4")             // Fairphone 4 https://codeberg.org/monocles/monocles_chat/issues/133
                    .add("ONEPLUS A6000")   // OnePlus 6 https://github.com/iNPUTmice/Conversations/issues/4329
                    .add("ONEPLUS A6003")   // OnePlus 6 https://github.com/iNPUTmice/Conversations/issues/4329
                    .add("ONEPLUS A6010")   // OnePlus 6T https://codeberg.org/monocles/monocles_chat/issues/133
                    .add("ONEPLUS A6013")   // OnePlus 6T https://codeberg.org/monocles/monocles_chat/issues/133
                    .add("Pixel 4a")        // Pixel 4a https://github.com/iNPUTmice/Conversations/issues/4223
                    .build();

    private boolean startRecording() {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        final int outputFormat;
        if (Config.USE_OPUS_VOICE_MESSAGES && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            outputFormat = MediaRecorder.OutputFormat.OGG;
            mRecorder.setOutputFormat(outputFormat);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS);
            mRecorder.setAudioEncodingBitRate(32000);
        } else {
            outputFormat = MediaRecorder.OutputFormat.MPEG_4;
            mRecorder.setOutputFormat(outputFormat);
            if (AAC_SENSITIVE_DEVICES.contains(Build.MODEL)) {
                // Changing these three settings for AAC sensitive devices might lead to sporadically truncated (cut-off) voice messages.
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
                mRecorder.setAudioSamplingRate(24000);
                mRecorder.setAudioEncodingBitRate(28000);
            } else {
                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mRecorder.setAudioSamplingRate(22050);
                mRecorder.setAudioEncodingBitRate(96000);
            }
        }
        setupOutputFile(outputFormat);
        mRecorder.setOutputFile(mOutputFile.getAbsolutePath());

        try {
            mRecorder.prepare();
            mRecorder.start();
            mStartTime = SystemClock.elapsedRealtime();
            mHandler.postDelayed(mTickExecutor, 100);
            Log.d(Config.LOGTAG, "started recording to " + mOutputFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "prepare() failed ", e);
            return false;
        }
    }

    protected void stopRecording(final boolean saveFile) {
        try {
            mRecorder.stop();
            mRecorder.release();
        } catch (Exception e) {
            if (saveFile) {
                Toast.makeText(this, R.string.unable_to_save_recording, Toast.LENGTH_SHORT).show();
                return;
            }
        } finally {
            mRecorder = null;
            mStartTime = 0;
        }
        if (!saveFile && mOutputFile != null) {
            if (mOutputFile.delete()) {
                Log.d(Config.LOGTAG, "deleted canceled recording");
            }
        }
        if (saveFile) {
            new Thread(new Finisher(outputFileWrittenLatch, mOutputFile, this)).start();
        }
    }

    private static class Finisher implements Runnable {

        private final CountDownLatch latch;
        private final File outputFile;
        private final WeakReference<Activity> activityReference;

        private Finisher(CountDownLatch latch, File outputFile, Activity activity) {
            this.latch = latch;
            this.outputFile = outputFile;
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            try {
                if (!latch.await(8, TimeUnit.SECONDS)) {
                    Log.d(Config.LOGTAG, "time out waiting for output file to be written");
                }
            } catch (final InterruptedException e) {
                Log.d(Config.LOGTAG, "interrupted while waiting for output file to be written", e);
            }
            final Activity activity = activityReference.get();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(
                    () -> {
                        activity.setResult(
                                Activity.RESULT_OK, new Intent().setData(Uri.fromFile(outputFile)));
                        activity.finish();
                    });
        }
    }

    private File generateOutputFilename(final int outputFormat) {
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US);
        final String extension;
        if (outputFormat == MediaRecorder.OutputFormat.MPEG_4) {
            extension = "m4a";
        } else if (outputFormat == MediaRecorder.OutputFormat.OGG) {
            extension = "oga";
        } else {
            throw new IllegalStateException("Unrecognized output format");
        }
        final String filename =
                String.format("RECORDING_%s.%s", dateFormat.format(new Date()), extension);
        final File parentDirectory;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            parentDirectory =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS);
        } else {
            parentDirectory =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        }
        final File conversationsDirectory = new File(parentDirectory, getString(R.string.app_name));
        return new File(conversationsDirectory, filename);
    }

    private void setupOutputFile(final int outputFormat) {
        mOutputFile = generateOutputFilename(outputFormat);
        final File parentDirectory = mOutputFile.getParentFile();
        if (Objects.requireNonNull(parentDirectory).mkdirs()) {
            Log.d(Config.LOGTAG, "created " + parentDirectory.getAbsolutePath());
        }
        setupFileObserver(parentDirectory);
    }

    private void setupFileObserver(final File directory) {
        mFileObserver =
                new FileObserver(directory.getAbsolutePath()) {
                    @Override
                    public void onEvent(int event, String s) {
                        if (s != null
                                && s.equals(mOutputFile.getName())
                                && event == FileObserver.CLOSE_WRITE) {
                            outputFileWrittenLatch.countDown();
                        }
                    }
                };
        mFileObserver.startWatching();
    }

    private void tick() {
        this.binding.timer.setText(TimeFrameUtils.formatTimePassed(mStartTime, true));
    }

    @Override
    public void onClick(final View view) {
        switch (view.getId()) {
            case R.id.cancel_button:
                mHandler.removeCallbacks(mTickExecutor);
                stopRecording(false);
                setResult(RESULT_CANCELED);
                finish();
                break;
            case R.id.share_button:
                this.binding.shareButton.setEnabled(false);
                this.binding.shareButton.setText(R.string.please_wait);
                mHandler.removeCallbacks(mTickExecutor);
                mHandler.postDelayed(() -> stopRecording(true), 500);
                break;
        }
    }
}
