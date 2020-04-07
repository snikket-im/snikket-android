package eu.siacs.conversations.ui;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityRtpSessionBinding;

public class RtpSessionActivity extends XmppActivity {

    public static final String EXTRA_WITH = "with";

    private ActivityRtpSessionBinding binding;

    public void onCreate(Bundle savedInstanceState) {
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_rtp_session);
        this.binding.acceptCall.setOnClickListener(this::acceptCall);
        this.binding.rejectCall.setOnClickListener(this::rejectCall);
    }

    private void rejectCall(View view) {

    }

    private void acceptCall(View view) {

    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {

    }
}
