package main;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import serializedObjects.ChatMessage;
import serializedObjects.FirstConnectionObject;
import serializedObjects.RequestPing;
import serializedObjects.RequestUserListObject;
import serializedObjects.Room;
import serializedObjects.ConnectToRoomObject;
import serializedObjects.RoomsEnum;
import serializedObjects.StopPingObject;
import serializedObjects.UpdateChatMessageObject;
import serializedObjects.UpdateMessage;
import serializedObjects.UserDetailsObject;

//TODO STOPPARE I LOOP INFINITI QUANDO NON C'è PIU GENTE
public class VoiceServer {

	private static final int PORT = 8888;
	private static final int UPDATEPORT = 8887;
	private HashMap<Integer, ServerSocket> chatRoomServers;//mappa che per ogni room associa una porta in ascolto
	private HashMap<Integer, List<UserWithSocket>> voiceChatRoomClients;//mappa che per ogni room associa una lista di connessioni con gli utenti
	private HashMap<Integer, List<UserWithSocket>> textChatRoomClients;
	private HashMap<Integer, List<UserWithSocket>> videoChatRoomClients;                                     
	private HashMap<Integer, UserWithSocket> updateSocketList;
	private HashMap<Integer, Room> chatRooms;
	private HashMap<Integer, PingThread> pingThreads;
	private ExecutorService pool = Executors.newCachedThreadPool();
	//private final ExecutorService pingExecutor = Executors.newSingleThreadExecutor();//Executors.newFixedThreadPool(5);


	public VoiceServer() {
		this.chatRoomServers = new HashMap<Integer, ServerSocket>();
		this.voiceChatRoomClients = new HashMap<Integer, List<UserWithSocket>>();
		this.textChatRoomClients = new HashMap<Integer, List<UserWithSocket>>();
		this.videoChatRoomClients = new HashMap<Integer, List<UserWithSocket>>();
		this.updateSocketList = new HashMap<Integer, UserWithSocket>();
		this.chatRooms = new HashMap<Integer, Room>();
		this.pingThreads=new HashMap<Integer, PingThread>();
	}

	public void start() {
		/*Thread hashPortServerThread = new Thread(() -> startHashPort(HASH_PORT));
		hashPortServerThread.start();*/

		Thread portServerThread = new Thread(() -> startServer(PORT));
		Thread updatePortServerThread = new Thread(() -> startUpdateServer(UPDATEPORT));

		portServerThread.start();
		updatePortServerThread.start();
	}


	/*private void startHashPort(int port) {
		try (ServerSocket serverHashSocket = new ServerSocket(port)) {
			System.out.println("Update Server started on port " + port);

			while (true) {
				Socket clientSocket = serverHashSocket.accept();
				System.out.println("Client connected: " + clientSocket.getInetAddress() + " on port: " + port);

				Thread clientHandlerThread = new Thread(() -> {
					try {
						InputStream inputStream = clientSocket.getInputStream();
						ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);

						while (true) {							
							String hash=(String)objectInputStream.readObject();
							System.out.println(hash);

							if(!hash.equals(HASH)) {
								DatabaseDetailsObject object=new DatabaseDetailsObject(null, null, null);
								OutputStream outputStream = clientSocket.getOutputStream();
								ObjectOutputStream objectoutputStream = new ObjectOutputStream(outputStream);
								objectoutputStream.writeObject(object);

							}else {
								DatabaseDetails databaseDetails=DatabaseDetails.getInstance();
								DatabaseDetailsObject object=new DatabaseDetailsObject(databaseDetails.getDB_URL(), databaseDetails.getDB_USER(), databaseDetails.getDB_PASSWORD());

								OutputStream outputStream = clientSocket.getOutputStream();
								ObjectOutputStream objectoutputStream = new ObjectOutputStream(outputStream);
								objectoutputStream.writeObject(object);
							}

						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				});

				clientHandlerThread.setDaemon(true);
				clientHandlerThread.start();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}*/

	private void startServer(int port) {
		try (ServerSocket serverSocket = new ServerSocket(port)) {
			System.out.println("Server started on port " + port);
			while (true) {
				Socket clientSocket = serverSocket.accept();
				System.out.println("Client connected: " + clientSocket.getInetAddress() + " on port: " + port);

				Thread clientHandlerThread = new Thread(() -> {
					try {

						while (true) {
							ObjectInputStream objectInputStream = new ObjectInputStream(clientSocket.getInputStream());
							ConnectToRoomObject object = (ConnectToRoomObject) objectInputStream.readObject();
							RoomsEnum type = object.getMainRoom().getType();
							UserDetailsObject user=object.getUser();

							if(!type.equals(RoomsEnum.VOICE)) {
								connectToChatRoom(user, object.getMainRoom(), clientSocket);
							}else {
								LinkedList<Room> rooms=new LinkedList<Room>();
								rooms.add(object.getMainRoom());
								rooms.add(object.getTextRoom());
								rooms.add(object.getVideoRoom());
								for(Room room: rooms) {
									if(!room.getType().equals(RoomsEnum.VIDEO)) {
										connectToChatRoom(user, room, clientSocket);
									}else {
										listenOnVoiceRoom(room);
									}
								}
							}
						}
					} catch (IOException | ClassNotFoundException e) {
						e.printStackTrace();
					}
				});

				clientHandlerThread.setDaemon(true);
				clientHandlerThread.start();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	private void startUpdateServer(int port) {
		try (ServerSocket serverUpdateSocket = new ServerSocket(port)) {
			System.out.println("Update Server started on port " + port);

			while (true) {
				Socket clientSocket = serverUpdateSocket.accept();
				System.out.println("Client connected: " + clientSocket.getInetAddress() + " on port: " + port);

				Thread clientHandlerThread = new Thread(() -> {
					try {

						while (true) {
							InputStream inputStream = clientSocket.getInputStream();
							ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
							Object receivedObject = (Object) objectInputStream.readObject();

							if (receivedObject instanceof OpenPortsMessage) {
								OpenPortsMessage object = (OpenPortsMessage) receivedObject;
								int start = object.getStart();
								int end = object.getEnd();
								SSHOpenPorts openPorts = new SSHOpenPorts();
								openPorts.openPorts(start, end);
							} else if (receivedObject instanceof UserDetailsObject) {
								UserDetailsObject object = (UserDetailsObject) receivedObject;
								updateUserStatus(object);
							} else if(receivedObject instanceof RequestUserListObject){
								RequestUserListObject object=(RequestUserListObject)receivedObject;
								getUpdatedUsersList(object, clientSocket);
							}else if(receivedObject instanceof FirstConnectionObject){ 
								FirstConnectionObject object=(FirstConnectionObject)receivedObject;
								updateSocketList.put(object.getUserId(), new UserWithSocket(object.getUserId(), clientSocket));		
								System.out.println("User Identified: "+object.getUserId());
							}else if(receivedObject instanceof RequestPing){
								RequestPing object=(RequestPing)receivedObject;
								PingThread ping=new PingThread(updateSocketList.get(object.getUserRequesting()).getUserSocket(), object.getUserRequested());
								pingThreads.put(object.getUserRequesting(), ping);
								ping.start();
							}else if(receivedObject instanceof StopPingObject){
								StopPingObject object=(StopPingObject)receivedObject;
								try {
									pingThreads.get(object.getUserRequesting()).stopRequest();
									pingThreads.remove(object.getUserRequesting());
								}catch(Exception e) {

								}
							}else {
								System.out.println("Unknown object received");
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				});

				clientHandlerThread.setDaemon(true);
				clientHandlerThread.start();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void connectToChatRoom(UserDetailsObject userDetails, Room newRoom,Socket clientSocket) {
		System.out.println("Client requested connection for port "+ newRoom.getPort());
		if (chatRoomServers.containsKey(newRoom.getId())) {//controllo se server è gia in ascolto su quella porta/room
			try {
				Socket roomClientSocket = chatRoomServers.get(newRoom.getId()).accept();//acetto la connessione con utente
				System.out.println("Connection established for chat room " + newRoom.getId() + ": " + roomClientSocket.getInetAddress());

				if(newRoom.getType().equals(RoomsEnum.VOICE)) {
					List<UserWithSocket> roomClients = voiceChatRoomClients.getOrDefault(newRoom.getId(), new ArrayList<>());//prendo l'elenco degli utenti connessi a quella room
					UserWithSocket user=new UserWithSocket(userDetails.getUserId(), roomClientSocket);
					user.createStream();
					roomClients.add(user);

					voiceChatRoomClients.put(newRoom.getId(), roomClients);//agguirno la mappa con l'elenco contenente il nuovo utente
					chatRooms.get(newRoom.getId()).addUser(userDetails);//aggiungo l'utente anche alla mappa che associa ogni room a un elenco non di connessioni ma di id utente
					int id= newRoom.getId();
					int userId=userDetails.getUserId();
					sendUserList(newRoom.getId());
					Thread audioReceiverThread = new Thread(() -> receiveAndBroadcastAudio(roomClientSocket, id, userId));
					audioReceiverThread.setDaemon(true);
					audioReceiverThread.start();
				}else if(newRoom.getType().equals(RoomsEnum.TEXT)){
					List<UserWithSocket> roomClients = textChatRoomClients.getOrDefault(newRoom.getId(), new ArrayList<>());//prendo l'elenco degli utenti connessi a quella room
					UserWithSocket user=new UserWithSocket(userDetails.getUserId(), roomClientSocket);
					user.createStream();
					roomClients.add(user);

					textChatRoomClients.put(newRoom.getId(), roomClients);//agguirno la mappa con l'elenco contenente il nuovo utente
					chatRooms.get(newRoom.getId()).addUser(userDetails);//aggiungo l'utente anche alla mappa che associa ogni room a un elenco non di connessioni ma di id utente
					int id= newRoom.getId();

					if(newRoom.getAssociatedRoom()==0) {
						sendUserList(newRoom.getId());
					}
					Thread messageReceiverThread = new Thread(() -> receiveAndBroadcastMessages(roomClientSocket, id));
					messageReceiverThread.setDaemon(true);
					messageReceiverThread.start();
				}else if(newRoom.getType().equals(RoomsEnum.VIDEO)) {
					int id= newRoom.getId();
					List<UserWithSocket> roomClients=videoChatRoomClients.get(newRoom.getId());

					UserWithSocket user=new UserWithSocket(userDetails.getUserId(), roomClientSocket);
					user.createStream();
					roomClients.add(user);

					if(chatRooms.containsKey(id)){
						chatRooms.get(id).addUser(userDetails);//aggiungo l'utente anche alla mappa che associa ogni room a un elenco non di connessioni ma di id utente
					}else {
						chatRooms.put(id, newRoom);
					}

					Thread vidoReceiverThread = new Thread(() -> receiveAndBroadcastVideo(roomClientSocket, id));
					vidoReceiverThread.setDaemon(true);
					vidoReceiverThread.start();
				}else {

				}


			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			try {
				ServerSocket roomServerSocket = new ServerSocket(newRoom.getPort());//metto il server in ascolto sulla porta della room
				System.out.println("Server Listening on port: " + newRoom.getPort());
				Socket roomClientSocket = roomServerSocket.accept();//accetto la connessione dell'utente
				System.out.println("Connection established for chat room " + newRoom.getId() + ": " + roomClientSocket.getInetAddress());

				chatRoomServers.put(newRoom.getId(), roomServerSocket);
				List<UserWithSocket> roomClients = new ArrayList<>();

				UserWithSocket user=new UserWithSocket(userDetails.getUserId(), roomClientSocket);
				user.createStream();
				roomClients.add(user);

				// Avvia un thread per gestire la ricezione dell'audio dal client
				if(newRoom.getType().equals(RoomsEnum.VOICE)) {
					voiceChatRoomClients.put(newRoom.getId(), roomClients);		
					newRoom.addUser(userDetails);
					chatRooms.put(newRoom.getId(), newRoom);
					sendUserList(newRoom.getId());
					int id=newRoom.getId();
					int userId=userDetails.getUserId();
					Thread audioReceiverThread = new Thread(() -> receiveAndBroadcastAudio(roomClientSocket, id, userId));
					audioReceiverThread.setDaemon(true);
					audioReceiverThread.start();

				}else if(newRoom.getType().equals(RoomsEnum.TEXT)){
					textChatRoomClients.put(newRoom.getId(), roomClients);
					newRoom.addUser(userDetails);
					chatRooms.put(newRoom.getId(), newRoom);
					if(newRoom.getAssociatedRoom()==0) {
						sendUserList(newRoom.getId());
					}
					int id=newRoom.getId();
					Thread messageReceiverThread = new Thread(() -> receiveAndBroadcastMessages(roomClientSocket, id));
					messageReceiverThread.setDaemon(true);
					messageReceiverThread.start();
				}else {

				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void listenOnVoiceRoom(Room room) {
		if (!chatRoomServers.containsKey(room.getId())) {
			try {
				ServerSocket videoServerSocket = new ServerSocket(room.getPort());
				System.out.println("Server Listening on port: " + room.getPort());
				chatRoomServers.put(room.getId(), videoServerSocket);
				List<UserWithSocket> roomClients=new ArrayList<>();
				videoChatRoomClients.put(room.getId(), roomClients);
				chatRooms.put(room.getId(), room);
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void receiveAndBroadcastAudio(Socket audioSocket, int roomId, int userId) {
		try {
			int bufferSize = 960;
			byte[] buffer = new byte[bufferSize];
			boolean speaking;

			// Crea uno stream audio in entrata
			InputStream audioInputStream = audioSocket.getInputStream();
			SpeakingSubject speakingSubject = new SpeakingSubject();

			ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(0);
			AtomicBoolean receivedPacket = new AtomicBoolean(false);


			speakingSubject.addObserver(new SpeakingObserver() {
				@Override
				public void speakingChanged(boolean newSpeakingValue) {
					chatRooms.get(roomId).getUsers().get(userId).setSpeaking(newSpeakingValue);
					sendUserList(roomId);
					//System.out.println("Speaking value changed: " + newSpeakingValue);
				}
			});

			scheduler.scheduleAtFixedRate(() -> {
				if (!receivedPacket.get()) {
					speakingSubject.setSpeaking(false);
				}
				receivedPacket.set(false);
			}, 0, 500, TimeUnit.MILLISECONDS);


			while (true) {
				int bytesRead = audioInputStream.read(buffer, 0, buffer.length);
				if (bytesRead == -1) {
					break;
				}

				receivedPacket.set(true);

				speaking=zeroByteCheck(buffer);
				speakingSubject.setSpeaking(speaking);

				System.out.println("Received audio data from client " + audioSocket.getInetAddress());
				List<UserWithSocket> roomClients = voiceChatRoomClients.get(roomId);

				for (UserWithSocket client : roomClients) {
					//if (client.getUserSocket() != audioSocket) {
					//if(client.getId()!=userId) {
					OutputStream clientOutputStream = client.getOutputStream();
					clientOutputStream.write(buffer, 0, bytesRead);
					System.out.println("Broadcasted audio data to client");
					//}

				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean zeroByteCheck(byte[] array) {
		for (byte b : array) {
			if (b != 0) {
				return true;
			}
		}
		return false;
	}

	private void sendUserList(int roomId) {
		try {
			for (Entry<Integer, UserDetailsObject> user: chatRooms.get(roomId).getUsers().entrySet()) {
				OutputStream outputStream = updateSocketList.get(user.getKey()).getUserSocket().getOutputStream();
				ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
				UpdateMessage message=new UpdateMessage(roomId, chatRooms.get(roomId).getUsers());
				objectOutputStream.writeObject(message);
				objectOutputStream.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void getUpdatedUsersList(RequestUserListObject request, Socket clientSocket) {
		try {
			OutputStream outputStream = clientSocket.getOutputStream();
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
			UpdateMessage message=new UpdateMessage(request.getRoomId(), chatRooms.get(request.getRoomId()).getUsers());
			objectOutputStream.writeObject(message);
			objectOutputStream.flush();
			System.out.println("Sent Updated User List");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	private void receiveAndBroadcastMessages(Socket socket, int id) {
		try {
			while (true) {
				InputStream inputStream = socket.getInputStream();
				ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
				Object receivedObject = objectInputStream.readObject();

				List<UserWithSocket> roomClients = textChatRoomClients.get(id);

				for (UserWithSocket client : roomClients) {
					if (client.getUserSocket() != socket) {
						ObjectOutputStream objectOutputStream = client.getClientObjectOutputStream();

						if (receivedObject instanceof ChatMessage || receivedObject instanceof UpdateChatMessageObject) {
							objectOutputStream.writeObject(receivedObject);
							objectOutputStream.flush();
							System.out.println(receivedObject instanceof ChatMessage ? "Message Broadcasted" : "Message Updated");
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void receiveAndBroadcastVideo(Socket socket, int roomId) {
		try {
			InputStream inputStream = socket.getInputStream();
			ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
			while (true) {
				byte[] imageBytes = (byte[]) objectInputStream.readObject();
				//System.out.println("Frame ricevuto");
				List<UserWithSocket> roomClients = videoChatRoomClients.get(roomId);
				for (UserWithSocket client : roomClients) {
					if(client.getUserSocket()!=socket) {
						pool.submit(() -> {
							try {
								ObjectOutputStream clientObjectOutputStream = client.getClientObjectOutputStream();
								synchronized (clientObjectOutputStream) {
									clientObjectOutputStream.writeObject(imageBytes);
									clientObjectOutputStream.flush();
								}
								//System.out.println("Frame Broadcasted");
							} catch (Exception e) {
								e.printStackTrace();
							}
						});
					}
				}
			}
		} catch (Exception e) {
			//e.printStackTrace();
		}
	}

	private void updateUserStatus(UserDetailsObject details) {

		if(details.isLive()==true && chatRooms.get(details.getMainRoomId()).getUsers().get(details.getUserId()).isLive()==false) {
			chatRooms.get(details.getMainRoomId()).getUsers().get(details.getUserId()).setLive(true);
		}

		try {
			if(details.isLive()==false && chatRooms.get(chatRooms.get(details.getMainRoomId()).getAssociatedRoom()).getUsers().get(details.getUserId()).isLive()==true) {
				chatRooms.get(chatRooms.get(details.getMainRoomId()).getAssociatedRoom()).getUsers().get(details.getUserId()).setLive(false);
				stopStreaming(details);
			}
		}catch(Exception e) {
		}

		if(details.isMicrophoneOn()==false && chatRooms.get(details.getMainRoomId()).getUsers().get(details.getUserId()).isMicrophoneOn()==true) {
			chatRooms.get(details.getMainRoomId()).getUsers().replace(details.getUserId(), details);
		}

		if(details.isMicrophoneOn()==true && chatRooms.get(details.getMainRoomId()).getUsers().get(details.getUserId()).isMicrophoneOn()==false) {
			chatRooms.get(details.getMainRoomId()).getUsers().replace(details.getUserId(), details);
		}

		if(details.isAudioOn()==false && chatRooms.get(details.getMainRoomId()).getUsers().get(details.getUserId()).isAudioOn()==true) {
			chatRooms.get(details.getMainRoomId()).getUsers().replace(details.getUserId(), details);
		}

		if(details.isAudioOn()==true && chatRooms.get(details.getMainRoomId()).getUsers().get(details.getUserId()).isAudioOn()==false) {
			chatRooms.get(details.getMainRoomId()).getUsers().replace(details.getUserId(), details);
		}

		if(details.isDisconnect()==true) {
			chatRooms.get(details.getMainRoomId()).getUsers().replace(details.getUserId(), details);
			sendUserList(details.getMainRoomId());
			disconnectFromRoom(details);
		}

		if(details.isDisconnect()==false) {
			sendUserList(details.getMainRoomId());
		}
	}

	private void disconnectFromRoom(UserDetailsObject details) {
		try {
			chatRooms.get(details.getMainRoomId()).getUsers().remove(details.getUserId());

			if(voiceChatRoomClients.containsKey(details.getMainRoomId())==true) {
				disconnectUserFromRoom(details.getMainRoomId(), details.getUserId(), voiceChatRoomClients);
				System.out.println("User " + details.getUserId() + " disconnected from voice room " + details.getMainRoomId());

				int associatedTextRoom=chatRooms.get(details.getMainRoomId()).getAssociatedTextRoom();
				int associatedVideoRoom=chatRooms.get(details.getMainRoomId()).getAssociatedVideoRoom();

				disconnectUserFromRoom(associatedTextRoom, details.getUserId(), textChatRoomClients);
				System.out.println("User " + details.getUserId() + " disconnected from associated text room " + associatedTextRoom);

				disconnectUserFromRoom(associatedVideoRoom, details.getUserId(), videoChatRoomClients);
				System.out.println("User " + details.getUserId() + " disconnected from associated video room " + associatedVideoRoom);

			}

			if(textChatRoomClients.containsKey(details.getMainRoomId())==true) {
				disconnectUserFromRoom(details.getMainRoomId(), details.getUserId(), textChatRoomClients);
				System.out.println("User " + details.getUserId() + " disconnected from text room " + details.getMainRoomId());
			}

			if(videoChatRoomClients.containsKey(details.getMainRoomId())==true) {
				disconnectUserFromRoom(details.getMainRoomId(), details.getUserId(), videoChatRoomClients);
				System.out.println("User " + details.getUserId() + " disconnected from video room " + details.getMainRoomId());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void disconnectUserFromRoom(int userId, int roomId, Map<Integer, List<UserWithSocket>> roomClients) {
		List<UserWithSocket> clients = roomClients.get(roomId);
		if (clients != null) {
			clients.removeIf(user -> user.getId() == userId);
			UserWithSocket userToBeDeleted = clients.stream().filter(user -> user.getId() == userId).findFirst().orElse(null);
			if (userToBeDeleted != null && userToBeDeleted.getUserSocket() != null) {
				try {
					userToBeDeleted.getUserSocket().close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/*public void getPingTime(Socket socket, int receiver, int pingedUser) {


		long startTime = System.currentTimeMillis();
		Thread pingThread = new Thread(() -> {
			boolean stopRequested = false;
			try {
				while (!stopRequested && System.currentTimeMillis() - startTime < 2 * 60 * 1000) { // 2 minutes in milliseconds
					int index = (int) ((System.currentTimeMillis() - startTime) / 1000); // Index based on elapsed time

					try {
						InetAddress inet = InetAddress.getByName(socket.getInetAddress().getHostAddress());
						long pingStartTime = System.currentTimeMillis();
						if (inet.isReachable(5000)) { // Timeout set to 5 seconds
							long pingEndTime = System.currentTimeMillis();
							int pingTime = (int) Math.round(pingEndTime - pingStartTime);

							OutputStream outputStream = updateSocketList.get(receiver).getUserSocket().getOutputStream();
							ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
							PingObject ping = new PingObject(pingTime, pingedUser);
							objectOutputStream.writeObject(ping);
							objectOutputStream.flush();
						} else {
							System.out.println("Ping time " + (index + 1) + ": -1 (Not reachable)");
						}
					} catch (Exception e) {
						e.printStackTrace();
						System.out.println("Ping time " + (index + 1) + ": -1 (Exception occurred)");
					}
					Thread.sleep(1000); // Wait for 1 second before next iteration
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});
		pingThread.start();
	}*/

	private void stopStreaming(UserDetailsObject details) {
		int videoRoomId=details.getMainRoomId();
		int mainRoomId=chatRooms.get(details.getMainRoomId()).getAssociatedRoom();

		chatRooms.get(mainRoomId).getUsers().put(details.getUserId(), details);
		sendUserList(mainRoomId);

		disconnectUserFromRoom(details.getUserId(), videoRoomId, videoChatRoomClients);
		System.out.println("User " + details.getUserId() + " disconnected from video room " + videoRoomId);
	}

	public static void main(String[] args) {
		VoiceServer server = new VoiceServer();
		server.start();
	}
}