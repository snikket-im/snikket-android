package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import com.google.android.material.color.MaterialColors;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityEasyInviteBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.BarcodeProvider;
import eu.siacs.conversations.utils.EasyOnboardingInvite;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.manager.EasyOnboardingManager;
import java.util.concurrent.TimeoutException;

public class EasyOnboardingInviteActivity extends XmppActivity {

    private ActivityEasyInviteBinding binding;

    private EasyOnboardingInvite easyOnboardingInvite;

    @Override
    public void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_easy_invite);
        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar(), true);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        this.binding.shareButton.setOnClickListener(v -> share());
        if (bundle != null && bundle.containsKey("invite")) {
            this.easyOnboardingInvite = bundle.getParcelable("invite");
            if (this.easyOnboardingInvite != null) {
                showInvite(this.easyOnboardingInvite);
                return;
            }
        }
        this.showLoading();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.easy_onboarding_invite, menu);
        final MenuItem share = menu.findItem(R.id.action_share);
        share.setVisible(easyOnboardingInvite != null);
        return super.onCreateOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.action_share) {
            share();
            return true;
        } else {
            return super.onOptionsItemSelected(menuItem);
        }
    }

    private void share() {
        final String shareText =
                getString(
                        R.string.easy_invite_share_text,
                        easyOnboardingInvite.getDomain(),
                        easyOnboardingInvite.getShareableLink());
        final Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_invite_with)));
    }

    @Override
    protected void refreshUiReal() {
        invalidateOptionsMenu();
        if (easyOnboardingInvite != null) {
            showInvite(easyOnboardingInvite);
        } else {
            showLoading();
        }
    }

    private void showLoading() {
        this.binding.inProgress.setVisibility(View.VISIBLE);
        this.binding.invite.setVisibility(View.GONE);
    }

    private void showInvite(final EasyOnboardingInvite invite) {
        this.binding.inProgress.setVisibility(View.GONE);
        this.binding.invite.setVisibility(View.VISIBLE);
        this.binding.tapToShare.setText(
                getString(R.string.tap_share_button_send_invite, invite.getDomain()));
        final Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        final int width = Math.min(size.x, size.y);
        final boolean nightMode =
                (this.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
        final int black;
        final int white;
        if (nightMode) {
            black =
                    MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorSurface,
                            "No surface color configured");
            white =
                    MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorSurfaceInverse,
                            "No inverse surface color configured");
        } else {
            black =
                    MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorSurfaceInverse,
                            "No inverse surface color configured");
            white =
                    MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorSurface,
                            "No surface color configured");
        }
        final Bitmap bitmap =
                BarcodeProvider.create2dBarcodeBitmap(
                        invite.getShareableLink(), width, black, white);
        binding.qrCode.setImageBitmap(bitmap);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle bundle) {
        super.onSaveInstanceState(bundle);
        if (easyOnboardingInvite != null) {
            bundle.putParcelable("invite", easyOnboardingInvite);
        }
    }

    @Override
    protected void onBackendConnected() {
        if (easyOnboardingInvite != null) {
            return;
        }
        final Intent launchIntent = getIntent();
        final String accountExtra = launchIntent.getStringExtra(EXTRA_ACCOUNT);
        final Jid jid = accountExtra == null ? null : Jid.of(accountExtra);
        if (jid == null) {
            return;
        }
        final Account account = xmppConnectionService.findAccountByJid(jid);
        final var future =
                account.getXmppConnection().getManager(EasyOnboardingManager.class).get();
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(EasyOnboardingInvite invite) {
                        EasyOnboardingInviteActivity.this.easyOnboardingInvite = invite;
                        Log.d(Config.LOGTAG, "invite requested");
                        refreshUi();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(Config.LOGTAG, "could not get invite", t);
                        if (t instanceof UnsupportedOperationException) {
                            Toast.makeText(
                                            getApplicationContext(),
                                            R.string
                                                    .server_does_not_support_easy_onboarding_invites,
                                            Toast.LENGTH_SHORT)
                                    .show();
                        } else if (t instanceof TimeoutException) {
                            Toast.makeText(
                                            getApplicationContext(),
                                            R.string.remote_server_timeout,
                                            Toast.LENGTH_SHORT)
                                    .show();
                        } else {
                            Toast.makeText(
                                            getApplicationContext(),
                                            R.string.unable_to_parse_invite,
                                            Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                },
                ContextCompat.getMainExecutor(this));
    }

    public static void launch(final Account account, final Activity context) {
        final Intent intent = new Intent(context, EasyOnboardingInviteActivity.class);
        intent.putExtra(EXTRA_ACCOUNT, account.getJid().asBareJid().toString());
        context.startActivity(intent);
    }
}
