package it.cricco.mqtt;

import java.util.logging.Logger;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;

@ChannelHandler.Sharable
class NettyHandler extends ChannelInboundHandlerAdapter {

    private static final String CLASS_NAME = NettyHandler.class.getSimpleName();
    private static final Logger log = Logger.getLogger(CLASS_NAME);
    

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) {
    	MqttMessage msg = (MqttMessage) message;
        log.info("Received a message type: " + msg.fixedHeader().messageType());

        try{
            switch (msg.fixedHeader().messageType()){
                case CONNECT:
                	log.info("CONNECT");
                	ctx.writeAndFlush(MqttMessageBuilders.connAck().returnCode(MqttConnectReturnCode.CONNECTION_ACCEPTED).build());
                	break;
                case DISCONNECT:
                	log.info("DISCONNECT");
                	break;
                case PUBLISH:
                	log.info("PUBLISH");
                	break;
                case SUBSCRIBE:
                	log.info("SUBSCRIBE");
                	break;
                case UNSUBSCRIBE:
                	log.info("UNSUBSCRIBE");
                	break;
                case PINGREQ:
                	log.info("PINGREQ");
                	break;
                case PINGRESP:
                	log.info("PINGRESP");
                	break;
                case CONNACK:
                	log.info("CONNACK");
                	break;
                case PUBACK:
                	log.info("PUBACK");
                	break;
                case PUBCOMP:
                	log.info("PUBCOMP");
                	break;
                case PUBREC:
                	log.info("PUBREC");
                	break;
                case PUBREL:
                	log.info("PUBREL");
                	break;
                case SUBACK:
                	log.info("SUBACK");
                	break;
                case UNSUBACK:
                	log.info("UNSUBACK");
                	break;
                default:
                	break;
            }
        }catch (Exception e){
            log.severe("Bad error in processing the message: " + e.getLocalizedMessage());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("Channel inactive: " + ctx.channel().id());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    	log.info("Channel active: " + ctx.channel().id());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception{
    	log.severe("Error in channel " + ctx.channel().id() + ": " + cause.getLocalizedMessage());
    }

}
