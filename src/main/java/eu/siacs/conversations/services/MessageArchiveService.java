package eu.siacs.conversations.services;

import android.util.Log;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnAdvancedStreamFeaturesLoaded;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class MessageArchiveService implements OnAdvancedStreamFeaturesLoaded {

	private final XmppConnectionService mXmppConnectionService;

	private final HashSet<Query> queries = new HashSet<Query>();

	public MessageArchiveService(final XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	public void query(final Conversation conversation) {
		synchronized (this.queries) {
			final Account account = conversation.getAccount();
			long start = conversation.getLastMessageTransmitted();
			long end = account.getXmppConnection().getLastSessionEstablished();
			if (end - start >= Config.MAX_HISTORY_AGE) {
				start = end - Config.MAX_HISTORY_AGE;
			}
			final Query query = new Query(conversation, start, end);
			this.queries.add(query);
			IqPacket packet = this.mXmppConnectionService.getIqGenerator().queryMessageArchiveManagement(query);
			this.mXmppConnectionService.sendIqPacket(account, packet, new OnIqPacketReceived() {
				@Override
				public void onIqPacketReceived(Account account, IqPacket packet) {
					if (packet.getType() == IqPacket.TYPE_ERROR) {
						finalizeQuery(query);
					}
				}
			});
		}
	}

	private void finalizeQuery(Query query) {
		synchronized (this.queries) {
			this.queries.remove(query);
		}
		final Conversation conversation = query.getConversation();
		conversation.sort();
		if (conversation.setLastMessageTransmitted(query.getEnd())) {
			this.mXmppConnectionService.databaseBackend.updateConversation(conversation);
		}
		this.mXmppConnectionService.updateConversationUi();
	}

	public void processFin(Element fin) {
		if (fin == null) {
			return;
		}
		Query query = findQuery(fin.getAttribute("queryid"));
		if (query == null) {
			return;
		}
		boolean complete = fin.getAttributeAsBoolean("complete");
		Element set = fin.findChild("set","http://jabber.org/protocol/rsm");
		Element last = set == null ? null : set.findChild("last");
		if (complete || last == null) {
			final Account account = query.getConversation().getAccount();
			Log.d(Config.LOGTAG,account.getJid().toBareJid().toString()+": completed mam query for "+query.getWith().toString());
			this.finalizeQuery(query);
		} else {
			final Query nextQuery = query.next(last == null ? null : last.getContent());
			IqPacket packet = this.mXmppConnectionService.getIqGenerator().queryMessageArchiveManagement(nextQuery);
			synchronized (this.queries) {
				this.queries.remove(query);
				this.queries.add(nextQuery);
			}
			this.mXmppConnectionService.sendIqPacket(query.getConversation().getAccount(),packet,new OnIqPacketReceived() {
				@Override
				public void onIqPacketReceived(Account account, IqPacket packet) {
					if (packet.getType() == IqPacket.TYPE_ERROR) {
						finalizeQuery(nextQuery);
					}
				}
			});
		}
	}

	private Query findQuery(String id) {
		if (id == null) {
			return null;
		}
		synchronized (this.queries) {
			for(Query query : this.queries) {
				if (query.getQueryId().equals(id)) {
					return query;
				}
			}
			return null;
		}
	}

	@Override
	public void onAdvancedStreamFeaturesAvailable(Account account) {
		if (account.getXmppConnection() != null && account.getXmppConnection().getFeatures().mam()) {
			List<Conversation> conversations = mXmppConnectionService.getConversations();
			for (Conversation conversation : conversations) {
				if (conversation.getMode() == Conversation.MODE_SINGLE && conversation.getAccount() == account) {
					this.query(conversation);
				}
			}
		} else {
			Log.d(Config.LOGTAG,"no mam available");
		}
	}

	public class Query {
		private long start;
		private long end;
		private Jid with;
		private String queryId;
		private String after = null;
		private Conversation conversation;

		public Query(Conversation conversation, long start, long end) {
			this.conversation = conversation;
			this.with = conversation.getContactJid().toBareJid();
			this.start = start;
			this.end = end;
			this.queryId = new BigInteger(50, mXmppConnectionService.getRNG()).toString(32);
		}

		public Query next(String after) {
			Query query = new Query(this.conversation,this.start,this.end);
			query.after = after;
			return query;
		}

		public String getAfter() {
			return after;
		}

		public String getQueryId() {
			return queryId;
		}

		public Jid getWith() {
			return with;
		}

		public long getStart() {
			return start;
		}

		public long getEnd() {
			return end;
		}

		public Conversation getConversation() {
			return conversation;
		}
	}
}
