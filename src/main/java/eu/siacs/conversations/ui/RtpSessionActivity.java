package eu.siacs.conversations.ui;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import java.lang.ref.WeakReference;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityRtpSessionBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.jingle.RtpEndUserState;
import rocks.xmpp.addr.Jid;

public class RtpSessionActivity extends XmppActivity implements XmppConnectionService.OnJingleRtpConnectionUpdate {

    public static final String EXTRA_WITH = "with";
    public static final String EXTRA_SESSION_ID = "session_id";

    private WeakReference<JingleRtpConnection> rtpConnectionReference;

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
        requireRtpConnection().rejectCall();
        finish();
    }

    private void acceptCall(View view) {
        requireRtpConnection().acceptCall();
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {
        final Intent intent = getIntent();
        final Account account = extractAccount(intent);
        final String with = intent.getStringExtra(EXTRA_WITH);
        final String sessionId = intent.getStringExtra(EXTRA_SESSION_ID);
        if (with != null && sessionId != null) {
            final WeakReference<JingleRtpConnection> reference = xmppConnectionService.getJingleConnectionManager()
                    .findJingleRtpConnection(account, Jid.ofEscaped(with), sessionId);
            if (reference == null || reference.get() == null) {
                finish();
                return;
            }
            this.rtpConnectionReference = reference;
            binding.with.setText(getWith().getDisplayName());
            showState(requireRtpConnection().getEndUserState());
        }
    }

    private void showState(final RtpEndUserState state) {
        switch (state) {
            case INCOMING_CALL:
                binding.status.setText(R.string.rtp_state_incoming_call);
                break;
            case CONNECTING:
                binding.status.setText(R.string.rtp_state_connecting);
                break;
            case CONNECTED:
                binding.status.setText(R.string.rtp_state_connected);
                break;
            case ACCEPTING_CALL:
                binding.status.setText(R.string.rtp_state_accepting_call);
                break;
        }
    }

    private Contact getWith() {
        final AbstractJingleConnection.Id id = requireRtpConnection().getId();
        final Account account = id.account;
        return account.getRoster().getContact(id.with);
    }

    private JingleRtpConnection requireRtpConnection() {
        final JingleRtpConnection connection = this.rtpConnectionReference != null ? this.rtpConnectionReference.get() : null;
        if (connection == null) {
            throw new IllegalStateException("No RTP connection found");
        }
        return connection;
    }

    @Override
    public void onJingleRtpConnectionUpdate(Account account, Jid with, RtpEndUserState state) {
        final AbstractJingleConnection.Id id = requireRtpConnection().getId();
        if (account == id.account && id.with.equals(with)) {
            runOnUiThread(()->{
                showState(state);
            });
        } else {
            Log.d(Config.LOGTAG,"received update for other rtp session");
        }

    }
}
