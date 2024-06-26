package main;

import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import serializedObjects.PingObject;

public class PingThread extends Thread {
	
	private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private Socket socket;
    private int pingedUser;

    public PingThread(Socket socket, int pingedUser) {
        this.socket = socket;
        this.pingedUser = pingedUser;
    }

    @Override
    public void run() {
    	long startTime = System.currentTimeMillis();
		try {
			while (!stopRequested.get() && System.currentTimeMillis() - startTime < 2 * 60 * 1000) { // 2 minutes in milliseconds
				int index = (int) ((System.currentTimeMillis() - startTime) / 1000); // Index based on elapsed time
				
				try {
					InetAddress inet = InetAddress.getByName(socket.getInetAddress().getHostAddress());
					long pingStartTime = System.currentTimeMillis();
					if (inet.isReachable(5000)) { // Timeout set to 5 seconds
						long pingEndTime = System.currentTimeMillis();
						int pingTime = (int) Math.round(pingEndTime - pingStartTime);

						OutputStream outputStream = socket.getOutputStream();
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
		} catch (Exception e) {
			e.printStackTrace();
		}
    }

    public void stopRequest() {
    	stopRequested.set(true);
    }
}
