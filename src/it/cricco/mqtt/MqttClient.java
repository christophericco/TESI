package it.cricco.mqtt;


import java.net.InetAddress;
import java.util.logging.Logger;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;

public class MqttClient implements IClient {

    private static final String CLASS_NAME = MqttClient.class.getSimpleName();
    private static final Logger log = Logger.getLogger(CLASS_NAME);


    private EventLoopGroup mWorkerGroup = null;

    private Channel mChannel = null;


    @Override
    public void connect(InetAddress host, int port) {
        mWorkerGroup = new NioEventLoopGroup(1);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(mWorkerGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("decoder", new MqttDecoder());
                        pipeline.addLast("encoder", MqttEncoder.INSTANCE);
                        pipeline.addLast("handler", new NettyHandler());
                    }
                })
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_KEEPALIVE, true);

        try{
        	mChannel = bootstrap.connect(host, port).sync().channel();
        }catch(InterruptedException ex){
        	log.severe("Error in client: " + ex.getLocalizedMessage());
        }
    }

    @Override
    public void sendMessage(MqttMessage msg) throws InterruptedException {
        mChannel.writeAndFlush(msg);
    }

    @Override
    public void disconnect() throws InterruptedException {
        if(mChannel == null){
            throw new IllegalStateException("Invoked close on an client that wasn't connected");
        }
        mChannel.close();
        if (mWorkerGroup == null) {
            throw new IllegalStateException("Invoked close on an client that wasn't connected");
        }
        mWorkerGroup.shutdownGracefully();
    }

}
