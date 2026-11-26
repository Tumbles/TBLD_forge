package forge.tbld.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import forge.deck.Deck;
import forge.game.GameView;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gui.interfaces.IGuiGame;
import forge.interfaces.IGameController;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.player.LobbyPlayerHuman;
import forge.sound.SoundSystem;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;

/**
 * A web server for playing Forge in a browser. It serves the web client at {@code /} and accepts
 * one WebSocket connection per seat at {@code /ws?seat=N}. When every seat has connected, it
 * starts a match. A browser can ask for the match to be abandoned and a new one started.
 */
public final class TbldWebServer {

    public static final int DEFAULT_PORT = 8080;

    private static final AttributeKey<Integer> SEAT = AttributeKey.valueOf("tbldSeat");

    private final int seatCount;
    /** Each seat's browser connection, indexed by seat number minus one. */
    private final AtomicReferenceArray<Channel> channels;
    /** The seats of the current match. */
    private volatile List<BrowserSeat> seats;
    private final EventLoopGroup boss = new NioEventLoopGroup(1);
    private final EventLoopGroup workers = new NioEventLoopGroup();
    private Channel serverChannel;
    private volatile boolean matchStarted;
    private volatile HostedMatch hostedMatch;
    /** The deck every seat plays. */
    private final Supplier<Deck> deck;

    public TbldWebServer(final int seatCount) {
        this(seatCount, TbldWebServer::testDeck);
    }

    public TbldWebServer(final int seatCount, final Supplier<Deck> deck) {
        this.seatCount = seatCount;
        this.deck = deck;
        this.channels = new AtomicReferenceArray<>(seatCount);
        this.seats = newSeats();
    }

    private List<BrowserSeat> newSeats() {
        final List<BrowserSeat> result = new ArrayList<>();
        for (int i = 1; i <= seatCount; i++) {
            final int index = i - 1;
            result.add(new BrowserSeat(i, () -> channels.get(index)));
        }
        return result;
    }

    private boolean isConnected(final int index) {
        final Channel c = channels.get(index);
        return c != null && c.isActive();
    }

    private void broadcast(final String json) {
        for (int i = 0; i < seatCount; i++) {
            final Channel c = channels.get(i);
            if (c != null && c.isActive()) {
                c.writeAndFlush(new TextWebSocketFrame(json));
            }
        }
    }

    /** Loads the card database. Must run once, after {@code GuiBase.setInterface}. */
    public static synchronized void initModel() {
        if (modelInitialized) {
            return;
        }
        FModel.initialize(null, preferences -> {
            preferences.setPref(FPref.UI_LANGUAGE, "en-US");
            preferences.setPref(FPref.UI_ENABLE_SOUNDS, false);
            preferences.setPref(FPref.UI_ENABLE_MUSIC, false);
            return null;
        });
        silence();
        modelInitialized = true;
    }

    /**
     * The server never plays sounds or music on the machine it runs on. Forge's sound system would
     * otherwise play game sounds there, and its alternative sound system ignores the sound setting.
     */
    static void silence() {
        FModel.getPreferences().setPref(FPref.UI_ENABLE_SOUNDS, false);
        FModel.getPreferences().setPref(FPref.UI_ENABLE_MUSIC, false);
        FModel.getPreferences().setPref(FPref.UI_ALT_SOUND_SYSTEM, false);
        SoundSystem.instance.setIgnorePlayRequests(true);
        SoundSystem.instance.pause();
    }

    private static boolean modelInitialized;

    /** Starts listening. Returns the bound port, which differs from {@code port} when it is 0. */
    public int start(final int port) throws InterruptedException {
        final ServerBootstrap b = new ServerBootstrap()
                .group(boss, workers)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(1 << 16))
                                .addLast(new WebSocketServerProtocolHandler(WebSocketServerProtocolConfig.newBuilder()
                                        .websocketPath("/ws")
                                        .checkStartsWith(true)
                                        .maxFramePayloadLength(1 << 20)
                                        .build()))
                                .addLast(new Handler());
                    }
                });
        serverChannel = b.bind(port).sync().channel();
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        boss.shutdownGracefully();
        workers.shutdownGracefully();
    }

    public void awaitClose() throws InterruptedException {
        serverChannel.closeFuture().sync();
    }

    public HostedMatch getHostedMatch() {
        return hostedMatch;
    }

    private synchronized void maybeStartMatch() {
        if (matchStarted) {
            return;
        }
        for (int i = 0; i < seatCount; i++) {
            if (!isConnected(i)) {
                return;
            }
        }
        matchStarted = true;
        final List<BrowserSeat> matchSeats = seats;
        final Thread t = new Thread(() -> startMatch(matchSeats), "tbld-match-start");
        t.setDaemon(true);
        t.start();
    }

    /** Abandons the current match, if any, and starts a new one with the connected seats. */
    public synchronized void restart() {
        final List<BrowserSeat> old = seats;
        final HostedMatch oldMatch = hostedMatch;
        // Conceding releases the engine's wait for input, so the old game thread can finish
        for (final BrowserSeat s : old) {
            try {
                final IGameController controller = s.getController();
                final GameView gv = oldMatch == null ? null : oldMatch.getGameView();
                if (controller != null && gv != null && !gv.isGameOver()) {
                    controller.concede();
                }
            } catch (final RuntimeException e) {
                e.printStackTrace();
            }
        }
        for (final BrowserSeat s : old) {
            s.retire();
        }
        if (oldMatch != null) {
            try {
                oldMatch.endCurrentGame();
            } catch (final RuntimeException e) {
                e.printStackTrace();
            }
        }
        hostedMatch = null;
        seats = newSeats();
        matchStarted = false;
        final JsonObject reset = new JsonObject();
        reset.addProperty("t", "reset");
        broadcast(reset.toString());
        maybeStartMatch();
    }

    private void startMatch(final List<BrowserSeat> seats) {
        // The web client outlines playable cards using the selection's actionable list
        FModel.getPreferences().setPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, true);
        // Cards are picked on the board only from the battlefield and the player's own hand. Cards in
        // other zones (a library, an opponent's hand) are offered in a dialog, which the web client
        // shows as a popup of cards.
        FModel.getPreferences().setPref(FPref.UI_SELECT_FROM_CARD_DISPLAYS, false);
        // Also here because the tests load the model without initModel
        silence();
        final List<RegisteredPlayer> players = new ArrayList<>();
        final Map<RegisteredPlayer, IGuiGame> guis = new LinkedHashMap<>();
        for (final BrowserSeat s : seats) {
            final RegisteredPlayer rp = new RegisteredPlayer(deck.get())
                    .setPlayer(new LobbyPlayerHuman("Player " + s.getSeatIndex()));
            players.add(rp);
            guis.put(rp, s.getGui());
        }
        final HostedMatch match = new HostedMatch();
        hostedMatch = match;
        final GameRules rules = new GameRules(GameType.Constructed);
        // TBLD: each player starts with the emblem for their place in turn order
        rules.setTbldStartingEmblems(true);
        match.startMatch(rules, null, players, guis, null);
    }

    /** A fixed mono-red deck for testing. Its Mountains are TBLD Mountain version 69. */
    static Deck testDeck() {
        final Deck d = new Deck("TBLD test deck");
        d.getMain().add("Mountain|TBLD|69", 17);
        d.getMain().add("Searing Touch", 4);
        d.getMain().add("Shock", 4);
        d.getMain().add("Raging Goblin", 4);
        d.getMain().add("Gray Ogre", 4);
        d.getMain().add("Hill Giant", 4);
        d.getMain().add("Lava Axe", 3);
        return d;
    }

    /** The seat index (from 0) that a WebSocket URI asks for, or -1. */
    private int seatFor(final String uri) {
        final List<String> param = new QueryStringDecoder(uri).parameters().get("seat");
        if (param == null || param.isEmpty()) {
            return -1;
        }
        try {
            final int seat = Integer.parseInt(param.get(0));
            return seat >= 1 && seat <= seatCount ? seat - 1 : -1;
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    private final class Handler extends SimpleChannelInboundHandler<Object> {

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
            if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete hc) {
                final int index = seatFor(hc.requestUri());
                if (index < 0) {
                    ctx.close();
                    return;
                }
                ctx.channel().attr(SEAT).set(index);
                channels.set(index, ctx.channel());
                final JsonObject hello = new JsonObject();
                hello.addProperty("t", "hello");
                hello.addProperty("seat", index + 1);
                ctx.channel().writeAndFlush(new TextWebSocketFrame(hello.toString()));
                seats.get(index).resend();
                maybeStartMatch();
                return;
            }
            super.userEventTriggered(ctx, evt);
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final Object msg) {
            if (msg instanceof TextWebSocketFrame frame) {
                final Integer index = ctx.channel().attr(SEAT).get();
                if (index != null) {
                    try {
                        final JsonObject json = JsonParser.parseString(frame.text()).getAsJsonObject();
                        if (json.has("t") && "restart".equals(json.get("t").getAsString())) {
                            final Thread t = new Thread(TbldWebServer.this::restart, "tbld-restart");
                            t.setDaemon(true);
                            t.start();
                        } else {
                            seats.get(index).receive(json);
                        }
                    } catch (final RuntimeException e) {
                        e.printStackTrace();
                    }
                }
            } else if (msg instanceof FullHttpRequest req) {
                serveStatic(ctx, req);
            }
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
            final Integer index = ctx.channel().attr(SEAT).get();
            if (index != null) {
                channels.compareAndSet(index, ctx.channel(), null);
            }
            super.channelInactive(ctx);
        }

        private void serveStatic(final ChannelHandlerContext ctx, final FullHttpRequest req) {
            final String path = new QueryStringDecoder(req.uri()).path();
            final String resource = "/".equals(path) ? "index.html" : null;
            byte[] body = null;
            String contentType = "text/html; charset=utf-8";
            if (resource != null) {
                try (InputStream in = TbldWebServer.class.getResourceAsStream("/tbld/web/" + resource)) {
                    if (in != null) {
                        body = in.readAllBytes();
                    }
                } catch (final IOException e) {
                    body = null;
                }
            } else if (path.startsWith(TbldPictures.URL_PREFIX)) {
                // A TBLD card picture
                final String fileName = path.substring(TbldPictures.URL_PREFIX.length());
                final Path file = TbldPictures.file(fileName);
                if (file != null) {
                    try {
                        body = Files.readAllBytes(file);
                        contentType = TbldPictures.contentType(fileName);
                    } catch (final IOException e) {
                        body = null;
                    }
                }
            }
            final FullHttpResponse resp;
            if (body == null) {
                resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
            } else {
                resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(body));
                resp.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
                if (resource == null) {
                    resp.headers().set(HttpHeaderNames.CACHE_CONTROL, "max-age=3600");
                }
            }
            resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());
            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }
    }

    /** Entry point for {@code forge server [port]}. */
    public static void run(final String[] args) throws InterruptedException {
        int port = DEFAULT_PORT;
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }
        initModel();
        final TbldWebServer server = new TbldWebServer(2);
        final int bound = server.start(port);
        System.out.println("TBLD server running. Open http://localhost:" + bound + "/ in two browser tabs.");
        server.awaitClose();
    }
}
