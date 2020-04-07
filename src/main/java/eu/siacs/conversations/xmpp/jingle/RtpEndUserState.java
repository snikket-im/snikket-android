package eu.siacs.conversations.xmpp.jingle;

public enum RtpEndUserState {
    INCOMING_CALL, //received a 'propose' message
    CONNECTING, //session-initiate or session-accepted but no webrtc peer connection yet
    CONNECTED, //session-accepted and webrtc peer connection is connected
    FINDING_DEVICE, //'propose' has been sent out; no 184 ack yet
    RINGING, //'propose' has been sent out and it has been 184 acked
    ACCEPTED_ON_OTHER_DEVICE, //received 'accept' from one of our own devices
    ACCEPTING_CALL, //'proceed' message has been sent; but no session-initiate has been received
    ENDING_CALL, //libwebrt says 'closed' but session-terminate hasnt gone through
    ENDED, //close UI
    DECLINED_OR_BUSY, //other party declined; no retry button
    CONNECTIVITY_ERROR //network error; retry button
}
