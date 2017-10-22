package it.cricco.mqtt;

import io.netty.handler.codec.mqtt.MqttMessage;

import java.net.InetAddress;


interface IClient {

    void connect(InetAddress host, int port) throws InterruptedException;

    void sendMessage(MqttMessage msg) throws InterruptedException;

    void disconnect() throws InterruptedException;
}
