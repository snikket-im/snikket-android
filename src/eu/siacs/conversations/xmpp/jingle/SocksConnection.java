package eu.siacs.conversations.xmpp.jingle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
	private String jid;
	private int port;
	private boolean isProxy = false;
	private String destination;
	private OutputStream outputStream;
	
	public SocksConnection(JingleConnection jingleConnection, String host, String jid, int port, String type) {
		this.jingleConnection = jingleConnection;
		this.host = host;
		this.jid = jid;
		this.port = port;
		this.isProxy = "proxy".equalsIgnoreCase(type);
		try {
			MessageDigest mDigest = MessageDigest.getInstance("SHA-1");
			StringBuilder destBuilder = new StringBuilder();
			destBuilder.append(jingleConnection.getSessionId());
			destBuilder.append(jingleConnection.getInitiator());
			destBuilder.append(jingleConnection.getResponder());
			mDigest.reset();
			this.destination = CryptoHelper.bytesToHex(mDigest.digest(destBuilder.toString().getBytes()));
			Log.d("xmppService","host="+host+", port="+port+", destination: "+destination);
		} catch (NoSuchAlgorithmException e) {
			
		}
	}
	
	public boolean connect() {
		try {
			this.socket = new Socket(this.host, this.port);
			InputStream is = socket.getInputStream();
			this.outputStream = socket.getOutputStream();
			byte[] login = {0x05, 0x01, 0x00};
			byte[] expectedReply = {0x05,0x00};
			byte[] reply = new byte[2];
			this.outputStream.write(login);
			is.read(reply);
			if (Arrays.equals(reply, expectedReply)) {
				String connect = ""+'\u0005'+'\u0001'+'\u0000'+'\u0003'+'\u0028'+this.destination+'\u0000'+'\u0000';
				this.outputStream.write(connect.getBytes());
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

	public void send(File file) {
		FileInputStream fileInputStream = null;
		try {
			fileInputStream = new FileInputStream(file);
			int count;
			byte[] buffer = new byte[8192];
			while ((count = fileInputStream.read(buffer)) > 0) {
			  this.outputStream.write(buffer, 0, count);
			}
		   
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				if (fileInputStream!=null) {
					fileInputStream.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public boolean isProxy() {
		return this.isProxy;
	}
	
	public String getJid() {
		return this.jid;
	}
}
