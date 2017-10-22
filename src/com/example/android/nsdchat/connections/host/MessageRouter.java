package com.example.android.nsdchat.connections.host;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by chris on 12/04/17.
 */

public class MessageRouter implements IMessageRouter{
	
	class Service {
		int port;
		int newPort;
		
		Service(int port, int newPort){
			this.port = port;
			this.newPort = newPort;
		}
	}
	
	class Client {
		int portServer;
		String clientId;
		boolean linked;
		
		Client(String clientId, int portServer, boolean linked){
			this.portServer = portServer;
			this.clientId = clientId;
			this.linked = linked;
		}
	}
	
	class Link{
		Client clientIn;
		Client clientOut;
		
		Link(Client clIn, Client clOut){
			this.clientIn = clIn;
			this.clientOut = clOut;
		}
	}
	
	private Map<Integer, Service> mapService = new HashMap<>();
	private Map<String, Client> mapInClient = new HashMap<>();
	private Map<String, Client> mapOutClient = new HashMap<>();
	private List<Link> links = new ArrayList<>();
			
	
	@Override
	public String routing(String sessionId) {
		Optional<Link> link = links.stream()
		.filter(l -> l.clientIn.clientId.equals(sessionId) || l.clientOut.clientId.equals(sessionId))
		.findFirst();
		if(link.isPresent()){
			if(sessionId.equals(link.get().clientIn.clientId)){
				return link.get().clientOut.clientId;
			}else{
				return link.get().clientIn.clientId;
			}
		}else{
			return null;
		}
	}

	@Override
	public int addIncomingClient(String sessionId, int serverPort) {
		if(!mapInClient.containsKey(sessionId)) {
			mapInClient.put(sessionId, new Client(sessionId, serverPort, false));
		}
		
		return mapService.get(serverPort).port;
	}
	
	@Override
	public int addOutcomingClient(String sessionId, int serverPort) {
		if(!mapOutClient.containsKey(sessionId)) {
			Client clOut = new Client(sessionId, serverPort, true);
			mapOutClient.put(sessionId, clOut);
			
			Optional<Service> service = mapService.values().stream()
					.filter(s -> s.port == serverPort)
					.findFirst();
			if(service.isPresent()){
				int port = service.get().newPort;
				Optional<Client> clIn = mapInClient.values().stream()
				.filter(c -> c.portServer == port && !c.linked)
				.findFirst();
				
				if(clIn.isPresent()){
					links.add(new Link(clIn.get(),clOut));
				}
			}
		}
		
		return 0;
	}

	@Override
	public String removeClient(String sessionId) {
		Optional<Link> link = links.stream()
				.filter(l -> l.clientIn.clientId.equals(sessionId) || l.clientOut.clientId.equals(sessionId))
				.findFirst();
		
		if(link.isPresent()){
			links.remove(link.get());
			mapInClient.remove(link.get().clientIn.clientId);
			mapOutClient.remove(link.get().clientOut.clientId);
			if(sessionId.equals(link.get().clientIn.clientId)){
				return link.get().clientOut.clientId;
			}else{
				return link.get().clientIn.clientId;
			}
		}
		return null;
	}

	@Override
	public void addService(int port, int newPort) {
		mapService.put(newPort, new Service(port, newPort));
	}

	@Override
	public void removeService(int newPort) {
		mapService.remove(newPort);
	}
}
