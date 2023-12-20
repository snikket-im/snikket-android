package eu.siacs.conversations.xmpp.jingle.transports;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.SocksSocketFactory;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.DirectConnectionUtils;
import eu.siacs.conversations.xmpp.jingle.stanzas.SocksByteStreamsTransportInfo;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class SocksByteStreamsTransport implements Transport {

    private final XmppConnection xmppConnection;

    private final AbstractJingleConnection.Id id;

    private final boolean initiator;
    private final boolean useTor;

    private final String streamId;

    private ImmutableList<Candidate> theirCandidates;
    private final String theirDestination;
    private final SettableFuture<Connection> selectedByThemCandidate = SettableFuture.create();
    private final SettableFuture<String> theirProxyActivation = SettableFuture.create();

    private final CountDownLatch terminationLatch = new CountDownLatch(1);

    private final ConnectionProvider connectionProvider;
    private final ListenableFuture<Connection> ourProxyConnection;

    private Connection connection;

    private Callback transportCallback;

    public SocksByteStreamsTransport(
            final XmppConnection xmppConnection,
            final AbstractJingleConnection.Id id,
            final boolean initiator,
            final boolean useTor,
            final String streamId,
            final Collection<Candidate> theirCandidates) {
        this.xmppConnection = xmppConnection;
        this.id = id;
        this.initiator = initiator;
        this.useTor = useTor;
        this.streamId = streamId;
        this.theirDestination =
                Hashing.sha1()
                        .hashString(
                                Joiner.on("")
                                        .join(
                                                Arrays.asList(
                                                        streamId,
                                                        id.with.toEscapedString(),
                                                        id.account.getJid().toEscapedString())),
                                StandardCharsets.UTF_8)
                        .toString();
        final var ourDestination =
                Hashing.sha1()
                        .hashString(
                                Joiner.on("")
                                        .join(
                                                Arrays.asList(
                                                        streamId,
                                                        id.account.getJid().toEscapedString(),
                                                        id.with.toEscapedString())),
                                StandardCharsets.UTF_8)
                        .toString();

        this.connectionProvider =
                new ConnectionProvider(id.account.getJid(), ourDestination, useTor);
        new Thread(connectionProvider).start();
        this.ourProxyConnection = getOurProxyConnection(ourDestination);
        setTheirCandidates(theirCandidates);
    }

    public SocksByteStreamsTransport(
            final XmppConnection xmppConnection,
            final AbstractJingleConnection.Id id,
            final boolean initiator,
            final boolean useTor) {
        this(
                xmppConnection,
                id,
                initiator,
                useTor,
                UUID.randomUUID().toString(),
                Collections.emptyList());
    }

    public void connectTheirCandidates() {
        Preconditions.checkState(
                this.transportCallback != null, "transport callback needs to be set");
        // TODO this needs to go into a variable so we can cancel it
        final var connectionFinder =
                new ConnectionFinder(
                        theirCandidates, theirDestination, selectedByThemCandidate, useTor);
        new Thread(connectionFinder).start();
        Futures.addCallback(
                connectionFinder.connectionFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Connection connection) {
                        final Candidate candidate = connection.candidate;
                        transportCallback.onCandidateUsed(streamId, candidate);
                        establishTransport(connection);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        if (throwable instanceof CandidateErrorException) {
                            transportCallback.onCandidateError(streamId);
                        }
                        establishTransport(null);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void establishTransport(final Connection selectedByUs) {
        Futures.addCallback(
                selectedByThemCandidate,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Connection result) {
                        establishTransport(selectedByUs, result);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        establishTransport(selectedByUs, null);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void establishTransport(
            final Connection selectedByUs, final Connection selectedByThem) {
        final var selection = selectConnection(selectedByUs, selectedByThem);
        if (selection == null) {
            transportCallback.onTransportSetupFailed();
            return;
        }
        if (selection.connection.candidate.type == CandidateType.DIRECT) {
            Log.d(Config.LOGTAG, "final selection " + selection.connection.candidate);
            this.connection = selection.connection;
            this.transportCallback.onTransportEstablished();
        } else {
            final ListenableFuture<String> proxyActivation;
            if (selection.owner == Owner.THEIRS) {
                proxyActivation = this.theirProxyActivation;
            } else {
                proxyActivation = activateProxy(selection.connection.candidate);
            }
            Log.d(Config.LOGTAG, "waiting for proxy activation");
            Futures.addCallback(
                    proxyActivation,
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(final String cid) {
                            // TODO compare cid to selection.connection.candidate
                            connection = selection.connection;
                            transportCallback.onTransportEstablished();
                        }

                        @Override
                        public void onFailure(@NonNull Throwable throwable) {
                            Log.d(Config.LOGTAG, "failed to activate proxy");
                        }
                    },
                    MoreExecutors.directExecutor());
        }
    }

    private ConnectionWithOwner selectConnection(
            final Connection selectedByUs, final Connection selectedByThem) {
        if (selectedByUs != null && selectedByThem != null) {
            if (selectedByUs.candidate.priority == selectedByThem.candidate.priority) {
                return initiator
                        ? new ConnectionWithOwner(selectedByUs, Owner.THEIRS)
                        : new ConnectionWithOwner(selectedByThem, Owner.OURS);
            } else if (selectedByUs.candidate.priority > selectedByThem.candidate.priority) {
                return new ConnectionWithOwner(selectedByUs, Owner.THEIRS);
            } else {
                return new ConnectionWithOwner(selectedByThem, Owner.OURS);
            }
        }
        if (selectedByUs != null) {
            return new ConnectionWithOwner(selectedByUs, Owner.THEIRS);
        }
        if (selectedByThem != null) {
            return new ConnectionWithOwner(selectedByThem, Owner.OURS);
        }
        return null;
    }

    private ListenableFuture<String> activateProxy(final Candidate candidate) {
        Log.d(Config.LOGTAG, "trying to activate our proxy " + candidate);
        final SettableFuture<String> iqFuture = SettableFuture.create();
        final IqPacket proxyActivation = new IqPacket(IqPacket.TYPE.SET);
        proxyActivation.setTo(candidate.jid);
        final Element query = proxyActivation.addChild("query", Namespace.BYTE_STREAMS);
        query.setAttribute("sid", this.streamId);
        final Element activate = query.addChild("activate");
        activate.setContent(id.with.toEscapedString());
        xmppConnection.sendIqPacket(
                proxyActivation,
                (a, response) -> {
                    if (response.getType() == IqPacket.TYPE.RESULT) {
                        Log.d(Config.LOGTAG, "our proxy has been activated");
                        transportCallback.onProxyActivated(this.streamId, candidate);
                        iqFuture.set(candidate.cid);
                    } else if (response.getType() == IqPacket.TYPE.TIMEOUT) {
                        iqFuture.setException(new TimeoutException());
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                a.getJid().asBareJid()
                                        + ": failed to activate proxy on "
                                        + candidate.jid);
                        iqFuture.setException(new IllegalStateException("Proxy activation failed"));
                    }
                });
        return iqFuture;
    }

    private ListenableFuture<Connection> getOurProxyConnection(final String ourDestination) {
        final var proxyFuture = getProxyCandidate();
        return Futures.transformAsync(
                proxyFuture,
                proxy -> {
                    final var connectionFinder =
                            new ConnectionFinder(
                                    ImmutableList.of(proxy), ourDestination, null, useTor);
                    new Thread(connectionFinder).start();
                    return Futures.transform(
                            connectionFinder.connectionFuture,
                            c -> {
                                try {
                                    c.socket.setKeepAlive(true);
                                    Log.d(
                                            Config.LOGTAG,
                                            "set keep alive on our own proxy connection");
                                } catch (final SocketException e) {
                                    throw new RuntimeException(e);
                                }
                                return c;
                            },
                            MoreExecutors.directExecutor());
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Candidate> getProxyCandidate() {
        if (Config.DISABLE_PROXY_LOOKUP) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("Proxy look up is disabled"));
        }
        final Jid streamer = xmppConnection.findDiscoItemByFeature(Namespace.BYTE_STREAMS);
        if (streamer == null) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("No proxy/streamer found"));
        }
        final IqPacket iqRequest = new IqPacket(IqPacket.TYPE.GET);
        iqRequest.setTo(streamer);
        iqRequest.query(Namespace.BYTE_STREAMS);
        final SettableFuture<Candidate> candidateFuture = SettableFuture.create();
        xmppConnection.sendIqPacket(
                iqRequest,
                (a, response) -> {
                    if (response.getType() == IqPacket.TYPE.RESULT) {
                        final Element query = response.findChild("query", Namespace.BYTE_STREAMS);
                        final Element streamHost =
                                query == null
                                        ? null
                                        : query.findChild("streamhost", Namespace.BYTE_STREAMS);
                        final String host =
                                streamHost == null ? null : streamHost.getAttribute("host");
                        final Integer port =
                                Ints.tryParse(
                                        Strings.nullToEmpty(
                                                streamHost == null
                                                        ? null
                                                        : streamHost.getAttribute("port")));
                        if (Strings.isNullOrEmpty(host) || port == null) {
                            candidateFuture.setException(
                                    new IOException("Proxy response is missing attributes"));
                            return;
                        }
                        candidateFuture.set(
                                new Candidate(
                                        UUID.randomUUID().toString(),
                                        host,
                                        streamer,
                                        port,
                                        655360 + (initiator ? 0 : 15),
                                        CandidateType.PROXY));

                    } else if (response.getType() == IqPacket.TYPE.TIMEOUT) {
                        candidateFuture.setException(new TimeoutException());
                    } else {
                        candidateFuture.setException(
                                new IOException(
                                        "received iq error in response to proxy discovery"));
                    }
                });
        return candidateFuture;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        final var connection = this.connection;
        if (connection == null) {
            throw new IOException("No candidate has been selected yet");
        }
        return connection.socket.getOutputStream();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        final var connection = this.connection;
        if (connection == null) {
            throw new IOException("No candidate has been selected yet");
        }
        return connection.socket.getInputStream();
    }

    @Override
    public ListenableFuture<TransportInfo> asTransportInfo() {
        final ListenableFuture<Collection<Connection>> proxyConnections =
                getOurProxyConnectionsFuture();
        return Futures.transform(
                proxyConnections,
                proxies -> {
                    final var candidateBuilder = new ImmutableList.Builder<Candidate>();
                    candidateBuilder.addAll(this.connectionProvider.candidates);
                    candidateBuilder.addAll(Collections2.transform(proxies, p -> p.candidate));
                    final var transportInfo =
                            new SocksByteStreamsTransportInfo(
                                    this.streamId, candidateBuilder.build());
                    return new TransportInfo(transportInfo, null);
                },
                MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<InitialTransportInfo> asInitialTransportInfo() {
        return Futures.transform(
                asTransportInfo(),
                ti ->
                        new InitialTransportInfo(
                                UUID.randomUUID().toString(), ti.transportInfo, ti.group),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Collection<Connection>> getOurProxyConnectionsFuture() {
        return Futures.catching(
                Futures.transform(
                        this.ourProxyConnection,
                        Collections::singleton,
                        MoreExecutors.directExecutor()),
                Exception.class,
                ex -> {
                    Log.d(Config.LOGTAG, "could not find a proxy of our own", ex);
                    return Collections.emptyList();
                },
                MoreExecutors.directExecutor());
    }

    private Collection<Connection> getOurProxyConnections() {
        final var future = getOurProxyConnectionsFuture();
        if (future.isDone()) {
            try {
                return future.get();
            } catch (final Exception e) {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public void terminate() {
        Log.d(Config.LOGTAG, "terminating socks transport");
        this.terminationLatch.countDown();
        final var connection = this.connection;
        if (connection != null) {
            closeSocket(connection.socket);
        }
        this.connectionProvider.close();
    }

    @Override
    public void setTransportCallback(final Callback callback) {
        this.transportCallback = callback;
    }

    @Override
    public void connect() {
        this.connectTheirCandidates();
    }

    @Override
    public CountDownLatch getTerminationLatch() {
        return this.terminationLatch;
    }

    public boolean setCandidateUsed(final String cid) {
        final var ourProxyConnections = getOurProxyConnections();
        final var proxyConnection =
                Iterables.tryFind(ourProxyConnections, c -> c.candidate.cid.equals(cid));
        if (proxyConnection.isPresent()) {
            this.selectedByThemCandidate.set(proxyConnection.get());
            return true;
        }

        // the peer selected a connection that is not our proxy. so we can close our proxies
        closeConnections(ourProxyConnections);

        final var connection = this.connectionProvider.findPeerConnection(cid);
        if (connection.isPresent()) {
            this.selectedByThemCandidate.set(connection.get());
            return true;
        } else {
            Log.d(Config.LOGTAG, "none of the connected candidates has cid " + cid);
            return false;
        }
    }

    public void setCandidateError() {
        this.selectedByThemCandidate.setException(
                new CandidateErrorException("Remote could not connect to any of our candidates"));
    }

    public void setProxyActivated(final String cid) {
        this.theirProxyActivation.set(cid);
    }

    public void setProxyError() {
        this.theirProxyActivation.setException(
                new IllegalStateException("Remote could not activate their proxy"));
    }

    public void setTheirCandidates(Collection<Candidate> candidates) {
        this.theirCandidates =
                Ordering.from(
                                (Comparator<Candidate>)
                                        (o1, o2) -> Integer.compare(o2.priority, o1.priority))
                        .immutableSortedCopy(candidates);
    }

    private static void closeSocket(final Socket socket) {
        try {
            socket.close();
        } catch (final IOException e) {
            Log.w(Config.LOGTAG, "error closing socket", e);
        }
    }

    private static class ConnectionProvider implements Runnable {

        private final ExecutorService clientConnectionExecutorService =
                Executors.newFixedThreadPool(4);

        private final ImmutableList<Candidate> candidates;

        private final int port;

        private final AtomicBoolean acceptingConnections = new AtomicBoolean(true);

        private ServerSocket serverSocket;

        private final String destination;

        private final ArrayList<Connection> peerConnections = new ArrayList<>();

        private ConnectionProvider(
                final Jid account, final String destination, final boolean useTor) {
            final SecureRandom secureRandom = new SecureRandom();
            this.port = secureRandom.nextInt(60_000) + 1024;
            this.destination = destination;
            final InetAddress[] localAddresses;
            if (Config.USE_DIRECT_JINGLE_CANDIDATES && !useTor) {
                localAddresses =
                        DirectConnectionUtils.getLocalAddresses().toArray(new InetAddress[0]);
            } else {
                localAddresses = new InetAddress[0];
            }
            final var candidateBuilder = new ImmutableList.Builder<Candidate>();
            for (int i = 0; i < localAddresses.length; ++i) {
                final var inetAddress = localAddresses[i];
                candidateBuilder.add(
                        new Candidate(
                                UUID.randomUUID().toString(),
                                inetAddress.getHostAddress(),
                                account,
                                port,
                                8257536 + i,
                                CandidateType.DIRECT));
            }
            this.candidates = candidateBuilder.build();
        }

        @Override
        public void run() {
            if (this.candidates.isEmpty()) {
                Log.d(Config.LOGTAG, "no direct candidates. stopping ConnectionProvider");
                return;
            }
            try (final ServerSocket serverSocket = new ServerSocket(this.port)) {
                this.serverSocket = serverSocket;
                while (acceptingConnections.get()) {
                    final Socket clientSocket;
                    try {
                        clientSocket = serverSocket.accept();
                    } catch (final SocketException ignored) {
                        Log.d(Config.LOGTAG, "server socket has been closed.");
                        return;
                    }
                    clientConnectionExecutorService.execute(
                            () -> acceptClientConnection(clientSocket));
                }
            } catch (final IOException e) {
                Log.d(Config.LOGTAG, "could not create server socket", e);
            }
        }

        private void acceptClientConnection(final Socket socket) {
            final var localAddress = socket.getLocalAddress();
            final var hostAddress = localAddress == null ? null : localAddress.getHostAddress();
            final var candidate =
                    Iterables.tryFind(this.candidates, c -> c.host.equals(hostAddress));
            if (candidate.isPresent()) {
                acceptingConnections(socket, candidate.get());

            } else {
                closeSocket(socket);
                Log.d(Config.LOGTAG, "no local candidate found for connection on " + hostAddress);
            }
        }

        private void acceptingConnections(final Socket socket, final Candidate candidate) {
            final var remoteAddress = socket.getRemoteSocketAddress();
            Log.d(
                    Config.LOGTAG,
                    "accepted client connection from " + remoteAddress + " to " + candidate);
            try {
                socket.setSoTimeout(3000);
                final byte[] authBegin = new byte[2];
                final InputStream inputStream = socket.getInputStream();
                final OutputStream outputStream = socket.getOutputStream();
                ByteStreams.readFully(inputStream, authBegin);
                if (authBegin[0] != 0x5) {
                    socket.close();
                }
                final short methodCount = authBegin[1];
                final byte[] methods = new byte[methodCount];
                ByteStreams.readFully(inputStream, methods);
                if (SocksSocketFactory.contains((byte) 0x00, methods)) {
                    outputStream.write(new byte[] {0x05, 0x00});
                } else {
                    outputStream.write(new byte[] {0x05, (byte) 0xff});
                }
                final byte[] connectCommand = new byte[4];
                ByteStreams.readFully(inputStream, connectCommand);
                if (connectCommand[0] == 0x05
                        && connectCommand[1] == 0x01
                        && connectCommand[3] == 0x03) {
                    int destinationCount = inputStream.read();
                    final byte[] destination = new byte[destinationCount];
                    ByteStreams.readFully(inputStream, destination);
                    final byte[] port = new byte[2];
                    ByteStreams.readFully(inputStream, port);
                    final String receivedDestination = new String(destination);
                    final ByteBuffer response = ByteBuffer.allocate(7 + destination.length);
                    final byte[] responseHeader;
                    final boolean success;
                    if (receivedDestination.equals(this.destination)) {
                        responseHeader = new byte[] {0x05, 0x00, 0x00, 0x03};
                        synchronized (this.peerConnections) {
                            peerConnections.add(new Connection(candidate, socket));
                        }
                        success = true;
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                "destination mismatch. received "
                                        + receivedDestination
                                        + " (expected "
                                        + this.destination
                                        + ")");
                        responseHeader = new byte[] {0x05, 0x04, 0x00, 0x03};
                        success = false;
                    }
                    response.put(responseHeader);
                    response.put((byte) destination.length);
                    response.put(destination);
                    response.put(port);
                    outputStream.write(response.array());
                    outputStream.flush();
                    if (success) {
                        Log.d(
                                Config.LOGTAG,
                                remoteAddress + " successfully connected to " + candidate);
                    } else {
                        closeSocket(socket);
                    }
                }
            } catch (final IOException e) {
                Log.d(Config.LOGTAG, "failed to accept client connection to " + candidate, e);
                closeSocket(socket);
            }
        }

        private static void closeServerSocket(@Nullable final ServerSocket serverSocket) {
            if (serverSocket == null) {
                return;
            }
            try {
                serverSocket.close();
            } catch (final IOException ignored) {

            }
        }

        public Optional<Connection> findPeerConnection(String cid) {
            synchronized (this.peerConnections) {
                return Iterables.tryFind(
                        this.peerConnections, connection -> connection.candidate.cid.equals(cid));
            }
        }

        public void close() {
            this.acceptingConnections.set(false); // we have probably done this earlier already
            closeServerSocket(this.serverSocket);
            synchronized (this.peerConnections) {
                closeConnections(this.peerConnections);
                this.peerConnections.clear();
            }
        }
    }

    private static void closeConnections(final Iterable<Connection> connections) {
        for (final var connection : connections) {
            closeSocket(connection.socket);
        }
    }

    private static class ConnectionFinder implements Runnable {

        private final SettableFuture<Connection> connectionFuture = SettableFuture.create();

        private final ImmutableList<Candidate> candidates;
        private final String destination;

        private final ListenableFuture<Connection> selectedByThemCandidate;
        private final boolean useTor;

        private ConnectionFinder(
                final ImmutableList<Candidate> candidates,
                final String destination,
                final ListenableFuture<Connection> selectedByThemCandidate,
                final boolean useTor) {
            this.candidates = candidates;
            this.destination = destination;
            this.selectedByThemCandidate = selectedByThemCandidate;
            this.useTor = useTor;
        }

        @Override
        public void run() {
            for (final Candidate candidate : this.candidates) {
                final Integer selectedByThemCandidatePriority =
                        getSelectedByThemCandidatePriority();
                if (selectedByThemCandidatePriority != null
                        && selectedByThemCandidatePriority > candidate.priority) {
                    Log.d(
                            Config.LOGTAG,
                            "The candidate selected by peer had a higher priority then anything we could try");
                    connectionFuture.setException(
                            new CandidateErrorException(
                                    "The candidate selected by peer had a higher priority then anything we could try"));
                    return;
                }
                try {
                    connectionFuture.set(connect(candidate));
                    Log.d(Config.LOGTAG, "connected to " + candidate);
                    return;
                } catch (final IOException e) {
                    Log.d(Config.LOGTAG, "could not connect to candidate " + candidate);
                }
            }
            connectionFuture.setException(
                    new CandidateErrorException(
                            String.format(
                                    Locale.US,
                                    "Gave up after %d candidates",
                                    this.candidates.size())));
        }

        private Connection connect(final Candidate candidate) throws IOException {
            final var timeout = 3000;
            final Socket socket;
            if (useTor) {
                Log.d(Config.LOGTAG, "using Tor to connect to candidate " + candidate.host);
                socket = SocksSocketFactory.createSocketOverTor(candidate.host, candidate.port);
            } else {
                socket = new Socket();
                final SocketAddress address = new InetSocketAddress(candidate.host, candidate.port);
                socket.connect(address, timeout);
            }
            socket.setSoTimeout(timeout);
            SocksSocketFactory.createSocksConnection(socket, destination, 0);
            socket.setSoTimeout(0);
            return new Connection(candidate, socket);
        }

        private Integer getSelectedByThemCandidatePriority() {
            final var future = this.selectedByThemCandidate;
            if (future != null && future.isDone()) {
                try {
                    final var connection = future.get();
                    return connection.candidate.priority;
                } catch (ExecutionException | InterruptedException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    public static class CandidateErrorException extends IllegalStateException {
        private CandidateErrorException(final String message) {
            super(message);
        }
    }

    private enum Owner {
        THEIRS,
        OURS
    }

    public static class ConnectionWithOwner {
        public final Connection connection;
        public final Owner owner;

        public ConnectionWithOwner(Connection connection, Owner owner) {
            this.connection = connection;
            this.owner = owner;
        }
    }

    public static class Connection {

        public final Candidate candidate;
        public final Socket socket;

        public Connection(Candidate candidate, Socket socket) {
            this.candidate = candidate;
            this.socket = socket;
        }
    }

    public static class Candidate implements Transport.Candidate {
        public final String cid;
        public final String host;
        public final Jid jid;
        public final int port;
        public final int priority;
        public final CandidateType type;

        public Candidate(
                final String cid,
                final String host,
                final Jid jid,
                int port,
                int priority,
                final CandidateType type) {
            this.cid = cid;
            this.host = host;
            this.jid = jid;
            this.port = port;
            this.priority = priority;
            this.type = type;
        }

        public static Candidate of(final Element element) {
            Preconditions.checkArgument(
                    "candidate".equals(element.getName()),
                    "trying to construct candidate from non candidate element");
            Preconditions.checkArgument(
                    Namespace.JINGLE_TRANSPORTS_S5B.equals(element.getNamespace()),
                    "candidate element is in correct namespace");
            final String cid = element.getAttribute("cid");
            final String host = element.getAttribute("host");
            final String jid = element.getAttribute("jid");
            final String port = element.getAttribute("port");
            final String priority = element.getAttribute("priority");
            final String type = element.getAttribute("type");
            if (Strings.isNullOrEmpty(cid)
                    || Strings.isNullOrEmpty(host)
                    || Strings.isNullOrEmpty(jid)
                    || Strings.isNullOrEmpty(port)
                    || Strings.isNullOrEmpty(priority)
                    || Strings.isNullOrEmpty(type)) {
                throw new IllegalArgumentException("Candidate is missing non optional attribute");
            }
            return new Candidate(
                    cid,
                    host,
                    Jid.ofEscaped(jid),
                    Integer.parseInt(port),
                    Integer.parseInt(priority),
                    CandidateType.valueOf(type.toUpperCase(Locale.ROOT)));
        }

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("cid", cid)
                    .add("host", host)
                    .add("jid", jid)
                    .add("port", port)
                    .add("priority", priority)
                    .add("type", type)
                    .toString();
        }

        public Element asElement() {
            final var element = new Element("candidate", Namespace.JINGLE_TRANSPORTS_S5B);
            element.setAttribute("cid", this.cid);
            element.setAttribute("host", this.host);
            element.setAttribute("jid", this.jid);
            element.setAttribute("port", this.port);
            element.setAttribute("priority", this.priority);
            element.setAttribute("type", this.type.toString().toLowerCase(Locale.ROOT));
            return element;
        }
    }

    public enum CandidateType {
        DIRECT,
        PROXY
    }
}
