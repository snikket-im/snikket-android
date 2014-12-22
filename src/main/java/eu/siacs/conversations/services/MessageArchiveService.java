package eu.siacs.conversations.services;

import android.util.Log;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.generator.AbstractGenerator;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnAdvancedStreamFeaturesLoaded;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class MessageArchiveService implements OnAdvancedStreamFeaturesLoaded {

	private final XmppConnectionService mXmppConnectionService;

	private final HashSet<Query> queries = new HashSet<Query>();
	private ArrayList<Query> pendingQueries = new ArrayList<Query>();

	public enum PagingOrder {
		NORMAL,
		REVERSE
	};

	public MessageArchiveService(final XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	public void catchup(final Account account) {
		long startCatchup = getLastMessageTransmitted(account);
		long endCatchup = account.getXmppConnection().getLastSessionEstablished();
		if (startCatchup == 0) {
			return;
		} else if (endCatchup - startCatchup >= Config.MAM_MAX_CATCHUP) {
			startCatchup = endCatchup - Config.MAM_MAX_CATCHUP;
			List<Conversation> conversations = mXmppConnectionService.getConversations();
			for (Conversation conversation : conversations) {
				if (conversation.getMode() == Conversation.MODE_SINGLE && conversation.getAccount() == account && startCatchup > conversation.getLastMessageTransmitted()) {
					this.query(conversation,startCatchup);
				}
			}
		}
		final Query query = new Query(account, startCatchup, endCatchup);
		this.queries.add(query);
		this.execute(query);
	}

	private long getLastMessageTransmitted(final Account account) {
		long timestamp = 0;
		for(final Conversation conversation : mXmppConnectionService.getConversations()) {
			if (conversation.getAccount() == account) {
				long tmp = conversation.getLastMessageTransmitted();
				if (tmp > timestamp) {
					timestamp = tmp;
				}
			}
		}
		return timestamp;
	}

	public Query query(final Conversation conversation) {
		return query(conversation,conversation.getAccount().getXmppConnection().getLastSessionEstablished());
	}

	public Query query(final Conversation conversation, long end) {
		return this.query(conversation,conversation.getLastMessageTransmitted(),end);
	}

	public Query query(Conversation conversation, long start, long end) {
		synchronized (this.queries) {
			if (start > end) {
				return null;
			}
			final Query query = new Query(conversation, start, end,PagingOrder.REVERSE);
			this.queries.add(query);
			this.execute(query);
			return query;
		}
	}

	public void executePendingQueries(final Account account) {
		List<Query> pending = new ArrayList<>();
		synchronized(this.pendingQueries) {
			for(Iterator<Query> iterator = this.pendingQueries.iterator(); iterator.hasNext();) {
				Query query = iterator.next();
				if (query.getAccount() == account) {
					pending.add(query);
					iterator.remove();
				}
			}
		}
		for(Query query : pending) {
			this.execute(query);
		}
	}

	private void execute(final Query query) {
		final Account account=  query.getAccount();
		if (account.getStatus() == Account.State.ONLINE) {
			Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": running mam query " + query.toString());
			IqPacket packet = this.mXmppConnectionService.getIqGenerator().queryMessageArchiveManagement(query);
			this.mXmppConnectionService.sendIqPacket(account, packet, new OnIqPacketReceived() {
				@Override
				public void onIqPacketReceived(Account account, IqPacket packet) {
					if (packet.getType() == IqPacket.TYPE_ERROR) {
						Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": error executing mam: " + packet.toString());
						finalizeQuery(query);
					}
				}
			});
		} else {
			synchronized (this.pendingQueries) {
				this.pendingQueries.add(query);
			}
		}
	}

	private void finalizeQuery(Query query) {
		synchronized (this.queries) {
			this.queries.remove(query);
		}
		final Conversation conversation = query.getConversation();
		if (conversation != null) {
			conversation.sort();
			if (conversation.setLastMessageTransmitted(query.getEnd())) {
				this.mXmppConnectionService.databaseBackend.updateConversation(conversation);
			}
			if (query.hasCallback()) {
				query.callback();
			} else {
				this.mXmppConnectionService.updateConversationUi();
			}
		} else {
			for(Conversation tmp : this.mXmppConnectionService.getConversations()) {
				if (tmp.getAccount() == query.getAccount()) {
					tmp.sort();
					if (tmp.setLastMessageTransmitted(query.getEnd())) {
						this.mXmppConnectionService.databaseBackend.updateConversation(tmp);
					}
				}
			}
		}
	}

	public boolean queryInProgress(Conversation conversation, XmppConnectionService.OnMoreMessagesLoaded callback) {
		synchronized (this.queries) {
			for(Query query : queries) {
				if (query.conversation == conversation) {
					if (!query.hasCallback() && callback != null) {
						query.setCallback(callback);
					}
					return true;
				}
			}
			return false;
		}
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
		Element first = set == null ? null : set.findChild("first");
		Element relevant = query.getPagingOrder() == PagingOrder.NORMAL ? last : first;
		boolean abort = (query.getStart() == 0 && query.getTotalCount() >= Config.PAGE_SIZE) || query.getTotalCount() >= Config.MAM_MAX_MESSAGES;
		if (complete || relevant == null || abort) {
			this.finalizeQuery(query);
			Log.d(Config.LOGTAG,query.getAccount().getJid().toBareJid().toString()+": finished mam after "+query.getTotalCount()+" messages");
		} else {
			final Query nextQuery;
			if (query.getPagingOrder() == PagingOrder.NORMAL) {
				nextQuery = query.next(last == null ? null : last.getContent());
			} else {
				nextQuery = query.prev(first == null ? null : first.getContent());
			}
			this.execute(nextQuery);
			this.finalizeQuery(query);
			synchronized (this.queries) {
				this.queries.remove(query);
				this.queries.add(nextQuery);
			}
		}
	}

	public Query findQuery(String id) {
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
			this.catchup(account);
		}
	}

	public class Query {
		private int totalCount = 0;
		private int messageCount = 0;
		private long start;
		private long end;
		private Jid with = null;
		private String queryId;
		private String reference = null;
		private Account account;
		private Conversation conversation;
		private PagingOrder pagingOrder = PagingOrder.NORMAL;
		private XmppConnectionService.OnMoreMessagesLoaded callback = null;


		public Query(Conversation conversation, long start, long end) {
			this(conversation.getAccount(), start, end);
			this.conversation = conversation;
			this.with = conversation.getJid().toBareJid();
		}

		public Query(Conversation conversation, long start, long end, PagingOrder order) {
			this(conversation,start,end);
			this.pagingOrder = order;
		}

		public Query(Account account, long start, long end) {
			this.account = account;
			this.start = start;
			this.end = end;
			this.queryId = new BigInteger(50, mXmppConnectionService.getRNG()).toString(32);
		}

		private Query page(String reference) {
			Query query = new Query(this.account,this.start,this.end);
			query.reference = reference;
			query.conversation = conversation;
			query.with = with;
			query.totalCount = totalCount;
			query.callback = callback;
			return query;
		}

		public Query next(String reference) {
			Query query = page(reference);
			query.pagingOrder = PagingOrder.NORMAL;
			return query;
		}

		public Query prev(String reference) {
			Query query = page(reference);
			query.pagingOrder = PagingOrder.REVERSE;
			return query;
		}

		public String getReference() {
			return reference;
		}

		public PagingOrder getPagingOrder() {
			return this.pagingOrder;
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

		public void setCallback(XmppConnectionService.OnMoreMessagesLoaded callback) {
			this.callback = callback;
		}

		public void callback() {
			if (this.callback != null) {
				this.callback.onMoreMessagesLoaded(messageCount,conversation);
				if (messageCount==0) {
					this.callback.informUser(R.string.no_more_history_on_server);
				}
			}
		}

		public long getEnd() {
			return end;
		}

		public Conversation getConversation() {
			return conversation;
		}

		public Account getAccount() {
			return this.account;
		}

		public void incrementTotalCount() {
			this.totalCount++;
		}

		public void incrementMessageCount() {
			this.messageCount++;
		}

		public int getTotalCount() {
			return this.totalCount;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("with=");
			if (this.with==null) {
				builder.append("*");
			} else {
				builder.append(with.toString());
			}
			builder.append(", start=");
			builder.append(AbstractGenerator.getTimestamp(this.start));
			builder.append(", end=");
			builder.append(AbstractGenerator.getTimestamp(this.end));
			if (this.reference!=null) {
				if (this.pagingOrder == PagingOrder.NORMAL) {
					builder.append(", after=");
				} else {
					builder.append(", before=");
				}
				builder.append(this.reference);
			}
			return builder.toString();
		}

		public boolean hasCallback() {
			return this.callback != null;
		}
	}
}
