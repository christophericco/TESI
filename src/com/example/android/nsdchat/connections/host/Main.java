package com.example.android.nsdchat.connections.host;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import it.cricco.events.EventDispatcher;
import it.cricco.mqtt.Device;
import it.cricco.mqtt.Server;

public class Main {

	private static final Logger log = Logger.getLogger(Main.class.getSimpleName());
	
	public static void main(String[] args) {
		Server server = null;
		Device device = null;
		ServerClient bridge = null;
		EventDispatcher dispatcher = null;
		
		System.out.println("Main started.");
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		
		try {
			showCommand();
			String line = reader.readLine();
			while(!line.equals("exit")){
				if(line.equals("bridge")){
					bridge = new ServerClient(new MessageReaderFactory(),
							new MessageWriterFactory());
					IMessageRouter messageRouter = new MessageRouter();
					dispatcher = new EventDispatcher(messageRouter, bridge.getListener());
					dispatcher.start();
					bridge.setDispatcher(dispatcher);
					bridge.start();
				}
				if(line.equals("create service")){
					System.out.println("Insert port:");
					line = reader.readLine();
					bridge.createServer(Integer.parseInt(line));
				}
				if(line.equals("start server")){
					System.out.println("Insert port:");
					line = reader.readLine();
					server = new Server();
					server.startServer(Integer.parseInt(line));
				}
				if(line.equals("stop server")){
					if(server != null){
						server.stopServer();
					}
				}
				if(line.equals("start connection")){
					System.out.println("Insert port:");
					line = reader.readLine();
					device = new Device();
					device.connect(new InetSocketAddress("localhost", Integer.parseInt(line)));	
				}
				if(line.equals("stop connection")){
					System.out.println("Insert port:");
					line = reader.readLine();
					
					if(device != null){
						device.disconnect();
					}
				}
				if(line.equals("send")){
					System.out.println("Insert messagge:");
					line = reader.readLine();
					
					MqttConnectMessage message = MqttMessageBuilders
							.connect()
								.cleanSession(true)
								.clientId("SampleClient")
								.keepAlive(60)
							.build();
					device.sendMessage(message);
				}
				
				showCommand();
				line = reader.readLine();
			}
			
			if(bridge != null){
				dispatcher.stop();
				bridge.stop();
			}
			if(server != null){
				server.stopServer();
			}
			if(device != null && device.isConnected()){
				device.disconnect();
			}
		} catch (IOException e) {
			log.log(Level.SEVERE, "Main error: ", e);
		} catch (InterruptedException e) {
			log.log(Level.SEVERE, "Interrupted error: ", e);
		}
		
		System.out.println("Server stop");

	}

	private static void showCommand() {
		System.out.println("COMMAND");
		System.out.println("Exit: exit");
		System.out.println("Create bridge: bridge");
		System.out.println("Create service: create service");
		System.out.println("Create server: start server");
		System.out.println("Create connection: start connection");
		System.out.println("Stop connection: stop connection");
		System.out.println("Stop server: stop server");
		System.out.println("Send messagge: send");
	}

}
