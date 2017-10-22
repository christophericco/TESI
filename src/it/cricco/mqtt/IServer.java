package it.cricco.mqtt;

import java.io.IOException;


interface IServer {

    void initialize(int port) throws IOException;

    void stop();
}
