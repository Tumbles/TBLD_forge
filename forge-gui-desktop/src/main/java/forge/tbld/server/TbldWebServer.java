package forge.tbld.server;

import forge.deck.Deck;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gui.interfaces.IGuiGame;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.player.LobbyPlayerHuman;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A web server for playing Forge in a browser. It serves the web client at {@code /} and accepts
 * one WebSocket connection per seat at {@code /ws?seat=N}. When both seats have connected, it
 * starts a two-player match.
 */
public final class TbldWebServer {

    public static final int DEFAULT_PORT = 8080;

    private static final AttributeKey<BrowserSeat> SEAT = AttributeKey.valueOf("tbldSeat");

    private final List<BrowserSeat> seats = new ArrayList<>();
    private final EventLoopGroup boss = new NioEventLoopGroup(1);
    private final EventLoopGroup workers = new NioEventLoopGroup();
    private Channel serverChannel;
    private volatile boolean matchStarted;
    private volatile HostedMatch hostedMatch;

    public TbldWebServer(final int seatCount) {
        for (int i = 1; i <= seatCount; i++) {
            seats.add(new BrowserSeat(i));
        }
    }

    /** Loads the card database. Must run once, after {@code GuiBase.setInterface}. */
    public static synchronized void initModel() {
        if (modelInitialized) {
            return;
        }
        FModel.initialize(null, preferences -> {
            preferences.setPref(FPref.UI_LANGUAGE, "en-US");
            return null;
        });
        modelInitialized = true;
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
        for (final BrowserSeat s : seats) {
            if (!s.isConnected()) {
                return;
            }
        }
        matchStarted = true;
        final Thread t = new Thread(this::startMatch, "tbld-match-start");
        t.setDaemon(true);
        t.start();
    }

    private void startMatch() {
        final List<RegisteredPlayer> players = new ArrayList<>();
        final Map<RegisteredPlayer, IGuiGame> guis = new LinkedHashMap<>();
        for (final BrowserSeat s : seats) {
            final RegisteredPlayer rp = new RegisteredPlayer(testDeck())
                    .setPlayer(new LobbyPlayerHuman("Player " + s.getSeatIndex()));
            players.add(rp);
            guis.put(rp, s.getGui());
        }
        final HostedMatch match = new HostedMatch();
        hostedMatch = match;
        match.startMatch(new GameRules(GameType.Constructed), null, players, guis, null);
    }

    /** A fixed mono-red deck for testing. */
    static Deck testDeck() {
        final Deck d = new Deck("TBLD test deck");
        d.getMain().add("Mountain", 17);
        d.getMain().add("Searing Touch", 4);
        d.getMain().add("Shock", 4);
        d.getMain().add("Raging Goblin", 4);
        d.getMain().add("Gray Ogre", 4);
        d.getMain().add("Hill Giant", 4);
        d.getMain().add("Lava Axe", 3);
        return d;
    }

    private BrowserSeat seatFor(final String uri) {
        final List<String> param = new QueryStringDecoder(uri).parameters().get("seat");
        if (param == null || param.isEmpty()) {
            return null;
        }
        try {
            final int index = Integer.parseInt(param.get(0));
            return index >= 1 && index <= seats.size() ? seats.get(index - 1) : null;
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private final class Handler extends SimpleChannelInboundHandler<Object> {

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
            if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete hc) {
                final BrowserSeat seat = seatFor(hc.requestUri());
                if (seat == null) {
                    ctx.close();
                    return;
                }
                ctx.channel().attr(SEAT).set(seat);
                seat.attach(ctx.channel());
                maybeStartMatch();
                return;
            }
            super.userEventTriggered(ctx, evt);
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final Object msg) {
            if (msg instanceof TextWebSocketFrame frame) {
                final BrowserSeat seat = ctx.channel().attr(SEAT).get();
                if (seat != null) {
                    final String text = frame.text();
                    try {
                        seat.receive(text);
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
            final BrowserSeat seat = ctx.channel().attr(SEAT).get();
            if (seat != null) {
                seat.detach(ctx.channel());
            }
            super.channelInactive(ctx);
        }

        private void serveStatic(final ChannelHandlerContext ctx, final FullHttpRequest req) {
            final String path = new QueryStringDecoder(req.uri()).path();
            final String resource = "/".equals(path) ? "index.html" : null;
            byte[] body = null;
            if (resource != null) {
                try (InputStream in = TbldWebServer.class.getResourceAsStream("/tbld/web/" + resource)) {
                    if (in != null) {
                        body = in.readAllBytes();
                    }
                } catch (final IOException e) {
                    body = null;
                }
            }
            final FullHttpResponse resp;
            if (body == null) {
                resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
            } else {
                resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(body));
                resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=utf-8");
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
