package main;

import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;

public class UserWithSocket {

	private int id;
	private Socket userSocket;
	private ObjectOutputStream clientObjectOutputStream;
	private OutputStream outputStream;

	public UserWithSocket(int id, Socket userSocket) {
		super();
		this.id = id;
		this.userSocket = userSocket;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		 this.id = id;
	}

	public Socket getUserSocket() {
		return userSocket;
	}

	public void setUserSocket(Socket userSocket) {
		this.userSocket = userSocket;
	}

	public void createStream() {
		try {
			outputStream = userSocket.getOutputStream();
			clientObjectOutputStream = new ObjectOutputStream(outputStream);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public ObjectOutputStream getClientObjectOutputStream() {
		return clientObjectOutputStream;
	}

	public void setClientObjectOutputStream(ObjectOutputStream clientObjectOutputStream) {
		this.clientObjectOutputStream = clientObjectOutputStream;
	}

	public OutputStream getOutputStream() {
		return outputStream;
	}

	public void setOutputStream(OutputStream outputStream) {
		this.outputStream = outputStream;
	}
	
	
}
