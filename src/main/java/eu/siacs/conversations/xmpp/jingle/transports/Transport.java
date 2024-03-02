package eu.siacs.conversations.xmpp.jingle.transports;

import com.google.common.util.concurrent.ListenableFuture;

import eu.siacs.conversations.xmpp.jingle.stanzas.GenericTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.Group;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

public interface Transport {

    OutputStream getOutputStream() throws IOException;

    InputStream getInputStream() throws IOException;

    ListenableFuture<TransportInfo> asTransportInfo();

    ListenableFuture<InitialTransportInfo> asInitialTransportInfo();

    default void readyToSentAdditionalCandidates() {}

    void terminate();

    void setTransportCallback(final Callback callback);

    void connect();

    CountDownLatch getTerminationLatch();

    interface Callback {
        void onTransportEstablished();

        void onTransportSetupFailed();

        void onAdditionalCandidate(final String contentName, final Candidate candidate);

        void onCandidateUsed(String streamId, SocksByteStreamsTransport.Candidate candidate);

        void onCandidateError(String streamId);

        void onProxyActivated(String streamId, SocksByteStreamsTransport.Candidate candidate);
    }

    enum Direction {
        SEND,
        RECEIVE,
        SEND_RECEIVE
    }

    class InitialTransportInfo extends TransportInfo {
        public final String contentName;

        public InitialTransportInfo(
                String contentName, GenericTransportInfo transportInfo, Group group) {
            super(transportInfo, group);
            this.contentName = contentName;
        }
    }

    class TransportInfo {

        public final GenericTransportInfo transportInfo;
        public final Group group;

        public TransportInfo(final GenericTransportInfo transportInfo, final Group group) {
            this.transportInfo = transportInfo;
            this.group = group;
        }

        public TransportInfo(final GenericTransportInfo transportInfo) {
            this.transportInfo = transportInfo;
            this.group = null;
        }
    }

    interface Candidate {}
}
