package eu.siacs.conversations.services;

public class MediaPlayer extends android.media.MediaPlayer {

    private int streamType;

    @Override
    public void setAudioStreamType(int streamType) {
        this.streamType = streamType;
        super.setAudioStreamType(streamType);
    }

    public int getAudioStreamType() {
        return streamType;
    }
}
