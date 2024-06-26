package main;

import java.io.InputStream;
import java.util.Properties;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class SSHOpenPorts {
	
	private String HOST;
	private String USER;
	private String PASSWORD;
	
	
	public SSHOpenPorts() {		
		Properties properties = new Properties();
		try (InputStream input = SSHOpenPorts.class.getClassLoader().getResourceAsStream("server.properties")) {			
			properties.load(input);
			HOST = properties.getProperty("HOST");
			USER = properties.getProperty("USER");
			PASSWORD = properties.getProperty("PASSWORD");
		}catch(Exception e) {
			e.printStackTrace();
		}		
	}
	
    public void openPorts(int start, int end) {

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(USER, HOST, 22);
            session.setPassword(PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            String command = "sudo ufw allow " + start + ":" + end + "/tcp";
            channel.setCommand(command);

            InputStream in = channel.getInputStream();
            channel.connect();

            // Lettura della risposta dal server (potresti gestire la risposta in base alle tue esigenze)
            byte[] tmp = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int bytesRead = in.read(tmp, 0, 1024);
                    if (bytesRead < 0) break;
                    System.out.print(new String(tmp, 0, bytesRead));
                }
                if (channel.isClosed()) {
                    if (in.available() > 0) continue;
                    System.out.println("Exit status: " + channel.getExitStatus());
                    break;
                }
            }

            channel.disconnect();
            session.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}