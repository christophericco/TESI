package it.cricco.mqtt;

import java.io.IOException;
import java.util.logging.Logger;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;

public class NettyServer implements IServer{

    private static final String CLASS_NAME = NettyServer.class.getSimpleName();
    private static final Logger log = Logger.getLogger(CLASS_NAME);

    private EventLoopGroup mWorkerGroup;
    private EventLoopGroup mBossGroup;

    @Override
    public void initialize(int port) throws IOException {

        mBossGroup = new NioEventLoopGroup(1);
        mWorkerGroup = new NioEventLoopGroup(1);

        final NettyHandler handler = new NettyHandler();

        ServerBootstrap b = new ServerBootstrap();
        b.group(mBossGroup, mWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        //pipeline.addFirst("idleStateHandler", new IdleStateHandler(0,0,30));
                        //pipeline.addAfter("idleStateHandler", "idleEventHandler", new IdleTimeoutHandler());
                        pipeline.addLast("decoder", new MqttDecoder());
                        pipeline.addLast("encoder", MqttEncoder.INSTANCE);
                        pipeline.addLast("handler", handler);

                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        try {
            // Bind and start to accept incoming connections.
            ChannelFuture f = b.bind(port);
            log.info("Server bind port: " + port);
            f.sync();
        } catch (InterruptedException ex) {
            log.severe("Binding error: " + ex.getLocalizedMessage());
        }
    }

    @Override
    public void stop() {
        if (mWorkerGroup == null || mBossGroup == null) {
            throw new IllegalStateException("Invoked stop on Server that wasn't initialized");
        }

        mWorkerGroup.shutdownGracefully();
        mBossGroup.shutdownGracefully();

        log.info("Shutdown NIOEventLoopGroup");
    }
}
