package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.Intent;

public class UriHandlerActivity extends Activity {

    @Override
    public void onStart() {
        super.onStart();
        handleIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleIntent(Intent data) {
        if (data == null) {
            finish();
            return;
        }

        switch (data.getAction()) {
            case Intent.ACTION_VIEW:
            case Intent.ACTION_SENDTO:
                final Intent intent = new Intent(getApplicationContext(),
                        StartConversationActivity.class);
                intent.setAction(data.getAction());
                intent.setData(data.getData());
                intent.setAction(data.getAction());
                startActivity(intent);
        }

        finish();
    }
}
