package eu.siacs.conversations.ui;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.databinding.DataBindingUtil;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMediaViewerBinding;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import java.util.ArrayList;
import java.util.List;

public class MediaViewerActivity extends XmppActivity {

    public static final String EXTRA_MESSAGE_UUID =
            "eu.siacs.conversations.extra.MESSAGE_UUID";

    private ActivityMediaViewerBinding binding;
    private String conversationUuid;
    private String messageUuid;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding =
                DataBindingUtil.setContentView(this, R.layout.activity_media_viewer);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot(), false, false);
        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar());
        final var intent = getIntent();
        if (intent != null) {
            this.conversationUuid = intent.getStringExtra(ConversationsActivity.EXTRA_CONVERSATION);
            this.messageUuid = intent.getStringExtra(EXTRA_MESSAGE_UUID);
        }
    }

    @Override
    protected void refreshUiReal() {}

    @Override
    protected void onBackendConnected() {
        if (conversationUuid == null) {
            return;
        }
        final Conversation conversation =
                xmppConnectionService.findConversationByUuidReliable(conversationUuid);
        if (conversation == null) {
            return;
        }
        final List<Message> allMessages = new ArrayList<>();
        conversation.populateWithMessages(allMessages);
        final List<Message> images = new ArrayList<>();
        for (final Message message : allMessages) {
            if (isViewableImage(message)) {
                images.add(message);
            }
        }
        if (images.isEmpty()) {
            return;
        }
        int startIndex = 0;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getUuid().equals(messageUuid)) {
                startIndex = i;
                break;
            }
        }
        final int total = images.size();
        final MediaPagerAdapter adapter = new MediaPagerAdapter(images);
        binding.mediaPager.setAdapter(adapter);
        binding.mediaPager.setCurrentItem(startIndex, false);
        updateTitle(startIndex + 1, total);
        binding.mediaPager.addOnPageChangeListener(
                new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageSelected(final int position) {
                        updateTitle(position + 1, total);
                    }
                });
    }

    private void updateTitle(final int current, final int total) {
        setTitle(current + " / " + total);
    }

    private static boolean isViewableImage(final Message message) {
        if (!message.isFileOrImage()) {
            return false;
        }
        if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            return false;
        }
        final Message.FileParams params = message.getFileParams();
        return params.width > 0 && params.height > 0;
    }

    private class MediaPagerAdapter extends PagerAdapter {

        private final List<Message> images;

        MediaPagerAdapter(final List<Message> images) {
            this.images = images;
        }

        @Override
        public int getCount() {
            return images.size();
        }

        @Override
        public boolean isViewFromObject(final View view, final Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(final ViewGroup container, final int position) {
            final ImageView imageView = new ImageView(MediaViewerActivity.this);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setBackgroundColor(Color.BLACK);
            final Message message = images.get(position);
            final var file =
                    xmppConnectionService.getFileBackend().getFile(message);
            if (file.exists()) {
                imageView.setImageURI(Uri.fromFile(file));
            }
            container.addView(imageView);
            return imageView;
        }

        @Override
        public void destroyItem(
                final ViewGroup container, final int position, final Object object) {
            container.removeView((View) object);
        }
    }
}
