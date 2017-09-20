package eu.siacs.conversations.ui.service;

import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Locale;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.adapter.MessageAdapter;
import eu.siacs.conversations.utils.WeakReferenceSet;

public class AudioPlayer implements View.OnClickListener, MediaPlayer.OnCompletionListener, SeekBar.OnSeekBarChangeListener, Runnable {

	private static final int REFRESH_INTERVAL = 250;
	private static final Object LOCK = new Object();
	private static MediaPlayer player = null;
	private static Message currentlyPlayingMessage = null;
	private final MessageAdapter messageAdapter;
	private final WeakReferenceSet<RelativeLayout> audioPlayerLayouts = new WeakReferenceSet<>();

	private final Handler handler = new Handler();

	public AudioPlayer(MessageAdapter adapter) {
		this.messageAdapter = adapter;
		synchronized (AudioPlayer.LOCK) {
			if (AudioPlayer.player != null) {
				AudioPlayer.player.setOnCompletionListener(this);
			}
		}
	}

	private static String formatTime(int ms) {
		return String.format(Locale.ENGLISH, "%d:%02d", ms / 60000, Math.min(Math.round((ms % 60000) / 1000f), 59));
	}

	public void init(RelativeLayout audioPlayer, Message message) {
		synchronized (AudioPlayer.LOCK) {
			audioPlayer.setTag(message);
			if (init(ViewHolder.get(audioPlayer), message)) {
				this.audioPlayerLayouts.addWeakReferenceTo(audioPlayer);
				this.stopRefresher(true);
			} else {
				this.audioPlayerLayouts.removeWeakReferenceTo(audioPlayer);
			}
		}
	}

	private boolean init(ViewHolder viewHolder, Message message) {
		viewHolder.runtime.setTextColor(this.messageAdapter.getMessageTextColor(viewHolder.darkBackground, false));
		viewHolder.progress.setOnSeekBarChangeListener(this);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			ColorStateList color = ContextCompat.getColorStateList(messageAdapter.getContext(), viewHolder.darkBackground ? R.color.white70 : R.color.bubble);
			viewHolder.progress.setThumbTintList(color);
			viewHolder.progress.setProgressTintList(color);
		}
		viewHolder.playPause.setAlpha(viewHolder.darkBackground ? 0.7f : 0.57f);
		viewHolder.playPause.setOnClickListener(this);
		if (message == currentlyPlayingMessage) {
			if (AudioPlayer.player != null && AudioPlayer.player.isPlaying()) {
				viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_pause_white_36dp : R.drawable.ic_pause_black_36dp);
				viewHolder.progress.setEnabled(true);
			} else {
				viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_play_arrow_white_36dp : R.drawable.ic_play_arrow_black_36dp);
				viewHolder.progress.setEnabled(false);
			}
			return true;
		} else {
			viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_play_arrow_white_36dp : R.drawable.ic_play_arrow_black_36dp);
			viewHolder.runtime.setText(formatTime(message.getFileParams().runtime));
			viewHolder.progress.setProgress(0);
			viewHolder.progress.setEnabled(false);
			return false;
		}
	}

	@Override
	public synchronized void onClick(View v) {
		if (v.getId() == R.id.play_pause) {
			synchronized (LOCK) {
				startStop((ImageButton) v);
			}
		}
	}

	private void startStop(ImageButton playPause) {
		final RelativeLayout audioPlayer = (RelativeLayout) playPause.getParent();
		final ViewHolder viewHolder = ViewHolder.get(audioPlayer);
		final Message message = (Message) audioPlayer.getTag();
		if (startStop(viewHolder, message)) {
			this.audioPlayerLayouts.clear();
			this.audioPlayerLayouts.addWeakReferenceTo(audioPlayer);
			stopRefresher(true);
		}
	}

	private boolean playPauseCurrent(ViewHolder viewHolder) {
		viewHolder.playPause.setAlpha(viewHolder.darkBackground ? 0.7f : 0.57f);
		if (player.isPlaying()) {
			viewHolder.progress.setEnabled(false);
			player.pause();
			viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_play_arrow_white_36dp : R.drawable.ic_play_arrow_black_36dp);
		} else {
			viewHolder.progress.setEnabled(true);
			player.start();
			this.stopRefresher(true);
			viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_pause_white_36dp : R.drawable.ic_pause_black_36dp);
		}
		return false;
	}

	private boolean play(ViewHolder viewHolder, Message message) {
		AudioPlayer.player = new MediaPlayer();
		try {
			AudioPlayer.currentlyPlayingMessage = message;
			AudioPlayer.player.setDataSource(messageAdapter.getFileBackend().getFile(message).getAbsolutePath());
			AudioPlayer.player.setOnCompletionListener(this);
			AudioPlayer.player.prepare();
			AudioPlayer.player.start();
			viewHolder.progress.setEnabled(true);
			viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_pause_white_36dp : R.drawable.ic_pause_black_36dp);
			return true;
		} catch (Exception e) {
			AudioPlayer.currentlyPlayingMessage = null;
			return false;
		}
	}

	private boolean startStop(ViewHolder viewHolder, Message message) {
		if (message == currentlyPlayingMessage && player != null) {
			return playPauseCurrent(viewHolder);
		}
		if (AudioPlayer.player != null) {
			stopCurrent();
		}
		return play(viewHolder, message);
	}

	private void stopCurrent() {
		if (AudioPlayer.player.isPlaying()) {
			AudioPlayer.player.stop();
		}
		AudioPlayer.player.release();
		AudioPlayer.player = null;
		resetPlayerUi();
	}

	private void resetPlayerUi() {
		for (WeakReference<RelativeLayout> audioPlayer : audioPlayerLayouts) {
			resetPlayerUi(audioPlayer.get());
		}
	}

	private void resetPlayerUi(RelativeLayout audioPlayer) {
		if (audioPlayer == null) {
			return;
		}
		final ViewHolder viewHolder = ViewHolder.get(audioPlayer);
		final Message message = (Message) audioPlayer.getTag();
		viewHolder.playPause.setImageResource(viewHolder.darkBackground ? R.drawable.ic_play_arrow_white_36dp : R.drawable.ic_play_arrow_black_36dp);
		if (message != null) {
			viewHolder.runtime.setText(formatTime(message.getFileParams().runtime));
		}
		viewHolder.progress.setProgress(0);
		viewHolder.progress.setEnabled(false);
	}

	@Override
	public void onCompletion(MediaPlayer mediaPlayer) {
		synchronized (AudioPlayer.LOCK) {
			this.stopRefresher(false);
			if (AudioPlayer.player == mediaPlayer) {
				AudioPlayer.currentlyPlayingMessage = null;
				AudioPlayer.player = null;
			}
			mediaPlayer.release();
			resetPlayerUi();
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		synchronized (AudioPlayer.LOCK) {
			final RelativeLayout audioPlayer = (RelativeLayout) seekBar.getParent();
			final Message message = (Message) audioPlayer.getTag();
			if (fromUser && message == AudioPlayer.currentlyPlayingMessage) {
				float percent = progress / 100f;
				int duration = AudioPlayer.player.getDuration();
				int seekTo = Math.round(duration * percent);
				AudioPlayer.player.seekTo(seekTo);
			}
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {

	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {

	}

	public void stop() {
		synchronized (AudioPlayer.LOCK) {
			stopRefresher(false);
			if (AudioPlayer.player != null) {
				stopCurrent();
			}
			AudioPlayer.currentlyPlayingMessage = null;
		}
	}

	private void stopRefresher(boolean runOnceMore) {
		this.handler.removeCallbacks(this);
		if (runOnceMore) {
			this.handler.post(this);
		}
	}

	@Override
	public void run() {
		synchronized (AudioPlayer.LOCK) {
			if (AudioPlayer.player != null) {
				boolean renew = false;
				final int current = player.getCurrentPosition();
				final int duration = player.getDuration();
				for (WeakReference<RelativeLayout> audioPlayer : audioPlayerLayouts) {
					renew |= refreshAudioPlayer(audioPlayer.get(), current, duration);
				}
				if (renew && AudioPlayer.player.isPlaying()) {
					handler.postDelayed(this, REFRESH_INTERVAL);
				}
			}
		}
	}

	private boolean refreshAudioPlayer(RelativeLayout audioPlayer, int current, int duration) {
		if (audioPlayer == null || audioPlayer.getVisibility() != View.VISIBLE) {
			return false;
		}
		final ViewHolder viewHolder = ViewHolder.get(audioPlayer);
		viewHolder.progress.setProgress(current * 100 / duration);
		viewHolder.runtime.setText(formatTime(current) + " / " + formatTime(duration));
		return true;
	}

	public static class ViewHolder {
		private TextView runtime;
		private SeekBar progress;
		private ImageButton playPause;
		private boolean darkBackground = false;

		public static ViewHolder get(RelativeLayout audioPlayer) {
			ViewHolder viewHolder = (ViewHolder) audioPlayer.getTag(R.id.TAG_AUDIO_PLAYER_VIEW_HOLDER);
			if (viewHolder == null) {
				viewHolder = new ViewHolder();
				viewHolder.runtime = (TextView) audioPlayer.findViewById(R.id.runtime);
				viewHolder.progress = (SeekBar) audioPlayer.findViewById(R.id.progress);
				viewHolder.playPause = (ImageButton) audioPlayer.findViewById(R.id.play_pause);
				audioPlayer.setTag(R.id.TAG_AUDIO_PLAYER_VIEW_HOLDER, viewHolder);
			}
			return viewHolder;
		}

		public void setDarkBackground(boolean darkBackground) {
			this.darkBackground = darkBackground;
		}
	}
}
