package eu.siacs.conversations.services;

import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import rocks.xmpp.addr.Jid;

public class PushManagementService {

    protected final XmppConnectionService mXmppConnectionService;

    PushManagementService(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    private static Data findResponseData(IqPacket response) {
        final Element command = response.findChild("command", Namespace.COMMANDS);
        final Element x = command == null ? null : command.findChild("x", Namespace.DATA);
        return x == null ? null : Data.parse(x);
    }

    private Jid getAppServer() {
        return Jid.of(mXmppConnectionService.getString(R.string.app_server));
    }

    void registerPushTokenOnServer(final Account account) {
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": has push support");
        retrieveFcmInstanceToken(token -> {
            final String androidId = PhoneHelper.getAndroidId(mXmppConnectionService);
            final IqPacket packet = mXmppConnectionService.getIqGenerator().pushTokenToAppServer(getAppServer(), token, androidId);
            mXmppConnectionService.sendIqPacket(account, packet, (a, response) -> {
                final Data data = findResponseData(response);
                if (response.getType() == IqPacket.TYPE.RESULT && data != null) {
                    try {
                        String node = data.getValue("node");
                        String secret = data.getValue("secret");
                        Jid jid = Jid.of(data.getValue("jid"));
                        if (node != null && secret != null) {
                            enablePushOnServer(a, jid, node, secret);
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": invalid response from app server");
                }
            });
        });
    }

    public void unregisterChannel(final Account account, final String channel) {
        final String androidId = PhoneHelper.getAndroidId(mXmppConnectionService);
        final Jid appServer = getAppServer();
        final IqPacket packet = mXmppConnectionService.getIqGenerator().unregisterChannelOnAppServer(appServer, androidId, channel);
        mXmppConnectionService.sendIqPacket(account, packet, (a, response) -> {
            if (response.getType() == IqPacket.TYPE.RESULT) {
                Log.d(Config.LOGTAG,a.getJid().asBareJid()+": successfully unregistered channel");
            } else if (response.getType() == IqPacket.TYPE.ERROR) {
                Log.d(Config.LOGTAG, a.getJid().asBareJid()+": unable to unregister channel with hash "+channel);
            }
        });
    }

    void registerPushTokenOnServer(final Conversation conversation) {
        Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": room "+conversation.getJid().asBareJid()+" has push support");
        retrieveFcmInstanceToken(token -> {
            final Jid muc = conversation.getJid().asBareJid();
            final String androidId = PhoneHelper.getAndroidId(mXmppConnectionService);
            final IqPacket packet = mXmppConnectionService.getIqGenerator().pushTokenToAppServer(getAppServer(), token, androidId, muc);
            packet.setTo(muc);
            mXmppConnectionService.sendIqPacket(conversation.getAccount(), packet, (a, response) -> {
                final Data data = findResponseData(response);
                if (response.getType() == IqPacket.TYPE.RESULT && data != null) {
                    try {
                        final String node = data.getValue("node");
                        final String secret = data.getValue("secret");
                        final Jid jid = Jid.of(data.getValue("jid"));
                        if (node != null && secret != null) {
                            enablePushOnServer(conversation, jid, node, secret);
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": invalid response from app server");
                }
            });
        });
    }

    private void enablePushOnServer(final Account account, final Jid appServer, final String node, final String secret) {
        final IqPacket enable = mXmppConnectionService.getIqGenerator().enablePush(appServer, node, secret);
        mXmppConnectionService.sendIqPacket(account, enable, (a, p) -> {
            if (p.getType() == IqPacket.TYPE.RESULT) {
                Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": successfully enabled push on server");
            } else if (p.getType() == IqPacket.TYPE.ERROR) {
                Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": enabling push on server failed");
            }
        });
    }

    private void enablePushOnServer(final Conversation conversation, final Jid appServer, final String node, final String secret) {
        final Jid muc = conversation.getJid().asBareJid();
        final IqPacket enable = mXmppConnectionService.getIqGenerator().enablePush(appServer, node, secret);
        enable.setTo(muc);
        mXmppConnectionService.sendIqPacket(conversation.getAccount(), enable, (a, p) -> {
            if (p.getType() == IqPacket.TYPE.RESULT) {
                Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": successfully enabled push on " + muc);
                if (conversation.setAttribute(Conversation.ATTRIBUTE_ALWAYS_NOTIFY, node)) {
                    mXmppConnectionService.updateConversation(conversation);
                }
            } else if (p.getType() == IqPacket.TYPE.ERROR) {
                Log.d(Config.LOGTAG, a.getJid().asBareJid() + ": enabling push on " + muc + " failed");
            }
        });
    }

    public void disablePushOnServer(final Conversation conversation) {
        final Jid muc = conversation.getJid().asBareJid();
        final String node = conversation.getAttribute(Conversation.ATTRIBUTE_PUSH_NODE);
        if (node != null) {
            final IqPacket disable = mXmppConnectionService.getIqGenerator().disablePush(getAppServer(), node);
            disable.setTo(muc);
            mXmppConnectionService.sendIqPacket(conversation.getAccount(), disable, (account, response) -> {
                if (response.getType() == IqPacket.TYPE.ERROR) {
                    Log.d(Config.LOGTAG,account.getJid().asBareJid()+": unable to disable push for room "+muc);
                }
            });
        } else {
            Log.d(Config.LOGTAG,conversation.getAccount().getJid().asBareJid()+": room "+muc+" has no stored node. unable to disable push");
        }
    }

    private void retrieveFcmInstanceToken(final OnGcmInstanceTokenRetrieved instanceTokenRetrieved) {
        FirebaseInstanceId.getInstance().getInstanceId().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.d(Config.LOGTAG, "unable to get Firebase instance token", task.getException());
            }
            final InstanceIdResult result;
            try {
                result = task.getResult();
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "unable to get Firebase instance token due to bug in library ", e);
                return;
            }
            if (result != null) {
                instanceTokenRetrieved.onGcmInstanceTokenRetrieved(result.getToken());
            }
        });

    }


    public boolean available(Account account) {
        final XmppConnection connection = account.getXmppConnection();
        return connection != null
                && connection.getFeatures().sm()
                && connection.getFeatures().push()
                && playServicesAvailable();
    }

    private boolean playServicesAvailable() {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mXmppConnectionService) == ConnectionResult.SUCCESS;
    }

    public boolean isStub() {
        return false;
    }

    interface OnGcmInstanceTokenRetrieved {
        void onGcmInstanceTokenRetrieved(String token);
    }
}
