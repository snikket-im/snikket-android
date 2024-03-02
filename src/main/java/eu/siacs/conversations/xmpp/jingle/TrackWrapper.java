package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import com.google.common.base.CaseFormat;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import eu.siacs.conversations.Config;

import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class TrackWrapper<T extends MediaStreamTrack> {
    public final T track;
    public final RtpSender rtpSender;

    private TrackWrapper(final T track, final RtpSender rtpSender) {
        Preconditions.checkNotNull(track);
        Preconditions.checkNotNull(rtpSender);
        this.track = track;
        this.rtpSender = rtpSender;
    }

    public static <T extends MediaStreamTrack> TrackWrapper<T> addTrack(
            final PeerConnection peerConnection, final T mediaStreamTrack) {
        final RtpSender rtpSender = peerConnection.addTrack(mediaStreamTrack);
        return new TrackWrapper<>(mediaStreamTrack, rtpSender);
    }

    public static <T extends MediaStreamTrack> Optional<T> get(
            @Nullable final PeerConnection peerConnection, final TrackWrapper<T> trackWrapper) {
        if (trackWrapper == null) {
            return Optional.absent();
        }
        final RtpTransceiver transceiver =
                peerConnection == null ? null : getTransceiver(peerConnection, trackWrapper);
        if (transceiver == null) {
            final String id;
            try {
                id = trackWrapper.rtpSender.id();
            } catch (final IllegalStateException e) {
                return Optional.absent();
            }
            Log.w(Config.LOGTAG, "unable to detect transceiver for " + id);
            return Optional.of(trackWrapper.track);
        }
        final RtpTransceiver.RtpTransceiverDirection direction = transceiver.getDirection();
        if (direction == RtpTransceiver.RtpTransceiverDirection.SEND_ONLY
                || direction == RtpTransceiver.RtpTransceiverDirection.SEND_RECV) {
            return Optional.of(trackWrapper.track);
        } else {
            Log.d(Config.LOGTAG, "withholding track because transceiver is " + direction);
            return Optional.absent();
        }
    }

    public static <T extends MediaStreamTrack> RtpTransceiver getTransceiver(
            @Nonnull final PeerConnection peerConnection, final TrackWrapper<T> trackWrapper) {
        final RtpSender rtpSender = trackWrapper.rtpSender;
        for (final RtpTransceiver transceiver : peerConnection.getTransceivers()) {
            if (transceiver.getSender().id().equals(rtpSender.id())) {
                return transceiver;
            }
        }
        return null;
    }

    public static String id(final Class<? extends MediaStreamTrack> clazz) {
        return String.format(
                "%s-%s",
                CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN, clazz.getSimpleName()),
                UUID.randomUUID().toString());
    }
}
