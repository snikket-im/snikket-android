package eu.siacs.conversations.crypto;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

import net.java.otr4j.OtrEngineHost;
import net.java.otr4j.OtrException;
import net.java.otr4j.OtrPolicy;
import net.java.otr4j.OtrPolicyImpl;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.InstanceTag;
import net.java.otr4j.session.SessionID;

public class OtrEngine implements OtrEngineHost {

	private Account account;
	private OtrPolicy otrPolicy;
	private KeyPair keyPair;
	private XmppConnectionService mXmppConnectionService;

	public OtrEngine(XmppConnectionService service, Account account) {
		this.account = account;
		this.otrPolicy = new OtrPolicyImpl();
		this.otrPolicy.setAllowV1(false);
		this.otrPolicy.setAllowV2(true);
		this.otrPolicy.setAllowV3(true);
		this.keyPair = loadKey(account.getKeys());
		this.mXmppConnectionService = service;
	}

	private KeyPair loadKey(JSONObject keys) {
		if (keys == null) {
			return null;
		}
		try {
			BigInteger x = new BigInteger(keys.getString("otr_x"), 16);
			BigInteger y = new BigInteger(keys.getString("otr_y"), 16);
			BigInteger p = new BigInteger(keys.getString("otr_p"), 16);
			BigInteger q = new BigInteger(keys.getString("otr_q"), 16);
			BigInteger g = new BigInteger(keys.getString("otr_g"), 16);
			KeyFactory keyFactory = KeyFactory.getInstance("DSA");
			DSAPublicKeySpec pubKeySpec = new DSAPublicKeySpec(y, p, q, g);
			DSAPrivateKeySpec privateKeySpec = new DSAPrivateKeySpec(x, p, q, g);
			PublicKey publicKey = keyFactory.generatePublic(pubKeySpec);
			PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
			return new KeyPair(publicKey, privateKey);
		} catch (JSONException e) {
			return null;
		} catch (NoSuchAlgorithmException e) {
			return null;
		} catch (InvalidKeySpecException e) {
			return null;
		}
	}

	private void saveKey() {
		PublicKey publicKey = keyPair.getPublic();
		PrivateKey privateKey = keyPair.getPrivate();
		KeyFactory keyFactory;
		try {
			keyFactory = KeyFactory.getInstance("DSA");
			DSAPrivateKeySpec privateKeySpec = keyFactory.getKeySpec(
					privateKey, DSAPrivateKeySpec.class);
			DSAPublicKeySpec publicKeySpec = keyFactory.getKeySpec(publicKey,
					DSAPublicKeySpec.class);
			this.account.setKey("otr_x", privateKeySpec.getX().toString(16));
			this.account.setKey("otr_g", privateKeySpec.getG().toString(16));
			this.account.setKey("otr_p", privateKeySpec.getP().toString(16));
			this.account.setKey("otr_q", privateKeySpec.getQ().toString(16));
			this.account.setKey("otr_y", publicKeySpec.getY().toString(16));
		} catch (final NoSuchAlgorithmException | InvalidKeySpecException e) {
			e.printStackTrace();
		}

    }

	@Override
	public void askForSecret(SessionID id, InstanceTag instanceTag, String question) {
		try {
			final Jid jid = Jid.fromSessionID(id);
			Conversation conversation = this.mXmppConnectionService.find(this.account,jid);
			if (conversation!=null) {
				conversation.smp().hint = question;
				conversation.smp().status = Conversation.Smp.STATUS_CONTACT_REQUESTED;
				mXmppConnectionService.updateConversationUi();
			}
		} catch (InvalidJidException e) {
			Log.d(Config.LOGTAG,account.getJid().toBareJid()+": smp in invalid session "+id.toString());
		}
	}

	@Override
	public void finishedSessionMessage(SessionID arg0, String arg1)
			throws OtrException {

	}

	@Override
	public String getFallbackMessage(SessionID arg0) {
		return "I would like to start a private (OTR encrypted) conversation but your client doesn’t seem to support that";
	}

	@Override
	public byte[] getLocalFingerprintRaw(SessionID arg0) {
		try {
			return new OtrCryptoEngineImpl().getFingerprintRaw(getPublicKey());
		} catch (OtrCryptoException e) {
			return null;
		}
	}

	public PublicKey getPublicKey() {
		if (this.keyPair == null) {
			return null;
		}
		return this.keyPair.getPublic();
	}

	@Override
	public KeyPair getLocalKeyPair(SessionID arg0) throws OtrException {
		if (this.keyPair == null) {
			KeyPairGenerator kg;
			try {
				kg = KeyPairGenerator.getInstance("DSA");
				this.keyPair = kg.genKeyPair();
				this.saveKey();
				mXmppConnectionService.databaseBackend.updateAccount(account);
			} catch (NoSuchAlgorithmException e) {
				Log.d(Config.LOGTAG,
						"error generating key pair " + e.getMessage());
			}
		}
		return this.keyPair;
	}

	@Override
	public String getReplyForUnreadableMessage(SessionID arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OtrPolicy getSessionPolicy(SessionID arg0) {
		return otrPolicy;
	}

	@Override
	public void injectMessage(SessionID session, String body)
			throws OtrException {
		MessagePacket packet = new MessagePacket();
		packet.setFrom(account.getJid());
		if (session.getUserID().isEmpty()) {
			packet.setAttribute("to", session.getAccountID());
		} else {
			packet.setAttribute("to", session.getAccountID() + "/" + session.getUserID());
		}
		packet.setBody(body);
		packet.addChild("private", "urn:xmpp:carbons:2");
		packet.addChild("no-copy", "urn:xmpp:hints");
		packet.addChild("no-store", "urn:xmpp:hints");
		packet.setType(MessagePacket.TYPE_CHAT);
		account.getXmppConnection().sendMessagePacket(packet);
	}

	@Override
	public void messageFromAnotherInstanceReceived(SessionID id) {
		Log.d(Config.LOGTAG,
				"unreadable message received from " + id.getAccountID());
	}

	@Override
	public void multipleInstancesDetected(SessionID arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void requireEncryptedMessage(SessionID arg0, String arg1)
			throws OtrException {
		// TODO Auto-generated method stub

	}

	@Override
	public void showError(SessionID arg0, String arg1) throws OtrException {
		Log.d(Config.LOGTAG,"show error");
	}

	@Override
	public void smpAborted(SessionID id) throws OtrException {
		setSmpStatus(id, Conversation.Smp.STATUS_NONE);
	}

	private void setSmpStatus(SessionID id, int status) {
		try {
			final Jid jid = Jid.fromSessionID(id);
			Conversation conversation = this.mXmppConnectionService.find(this.account,jid);
			if (conversation!=null) {
				conversation.smp().status = status;
				mXmppConnectionService.updateConversationUi();
			}
		} catch (final InvalidJidException ignored) {

		}
	}

	@Override
	public void smpError(SessionID id, int arg1, boolean arg2)
			throws OtrException {
		setSmpStatus(id, Conversation.Smp.STATUS_NONE);
	}

	@Override
	public void unencryptedMessageReceived(SessionID arg0, String arg1)
			throws OtrException {
		throw new OtrException(new Exception("unencrypted message received"));
	}

	@Override
	public void unreadableMessageReceived(SessionID arg0) throws OtrException {
		Log.d(Config.LOGTAG,"unreadable message received");
		throw new OtrException(new Exception("unreadable message received"));
	}

	@Override
	public void unverify(SessionID id, String arg1) {
		setSmpStatus(id, Conversation.Smp.STATUS_FAILED);
	}

	@Override
	public void verify(SessionID id, String fingerprint, boolean approved) {
		Log.d(Config.LOGTAG,"OtrEngine.verify("+id.toString()+","+fingerprint+","+String.valueOf(approved)+")");
		try {
			final Jid jid = Jid.fromSessionID(id);
			Conversation conversation = this.mXmppConnectionService.find(this.account,jid);
			if (conversation!=null) {
				if (approved) {
					conversation.getContact().addOtrFingerprint(CryptoHelper.prettifyFingerprint(fingerprint));
				}
				conversation.smp().hint = null;
				conversation.smp().status = Conversation.Smp.STATUS_FINISHED;
				mXmppConnectionService.updateConversationUi();
				mXmppConnectionService.syncRosterToDisk(conversation.getAccount());
			}
		} catch (final InvalidJidException ignored) {
		}
	}

}
