package eu.siacs.conversations.xmpp.jingle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import eu.siacs.conversations.utils.CryptoHelper;

import android.util.Log;

public class SocksConnection {
	
	private JingleConnection jingleConnection;
	private Socket socket;
	private String host;
	private int port;
	private String destination;
	
	public SocksConnection(JingleConnection jingleConnection, String host, int port) {
		this.jingleConnection = jingleConnection;
		this.host = host;
		this.port = port;
		try {
			MessageDigest mDigest = MessageDigest.getInstance("SHA-1");
			StringBuilder destBuilder = new StringBuilder();
			destBuilder.append(jingleConnection.getSessionId());
			destBuilder.append(jingleConnection.getInitiator());
			destBuilder.append(jingleConnection.getResponder());
			mDigest.reset();
			Log.d("xmppService","plain destination: "+destBuilder.toString());
			this.destination = CryptoHelper.bytesToHex(mDigest.digest(destBuilder.toString().getBytes()));
			Log.d("xmppService","host="+host+", port="+port+", destination: "+destination);
		} catch (NoSuchAlgorithmException e) {
			
		}
	}
	
	public boolean connect() {
		try {
			this.socket = new Socket(this.host, this.port);
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();
			byte[] login = {0x05, 0x01, 0x00};
			byte[] expectedReply = {0x05,0x00};
			byte[] reply = new byte[2];
			os.write(login);
			is.read(reply);
			if (Arrays.equals(reply, expectedReply)) {
				String connect = ""+'\u0005'+'\u0001'+'\u0000'+'\u0003'+'\u0028'+this.destination+'\u0000'+'\u0000';
				os.write(connect.getBytes());
				byte[] result = new byte[2];
				is.read(result);
				int status = result[0];
				return (status==0);
			} else {
				socket.close();
				return false;
			}
		} catch (UnknownHostException e) {
			return false;
		} catch (IOException e) {
			return false;
		}
	}
}
