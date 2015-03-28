package applications;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import datatypes.Datagram;
import datatypes.TTP_Segment;
import services.TTP_Segment_Service;


public class FTPRequestHandler implements Runnable{
	public TTP_Segment_Service ts;
	public Datagram datagram;
	public short serverPort;
	public int time_interval;
	public short window_size;
	public static final int OFFSET = 6;
	public static final String serverAddr = "127.0.0.1";
	public static final String clientAddr = "127.0.0.1";
	public static final byte[] md5_head = "MD5".getBytes();
	public static final int hash_buf = 16;
	public static final int buflen = 1024 * 1024 * 500;
	
	public FTPRequestHandler(TTP_Segment_Service ts, Datagram datagram, short serverPort,
			short window_size, int time_interval) {
		super();
		this.datagram = datagram;
		this.ts = ts;
		this.serverPort = serverPort;
		this.window_size = window_size;
		this.time_interval = time_interval;
	}
	
	@Override
	public void run() {
		// Handling FTP Client Request
		try {
			TTP_Segment segment = (TTP_Segment)datagram.getData();
			short clientPort = segment.get_source_port();
			System.out.println("Start handle client request: "+clientPort);
			
			ts.listen_connection(serverAddr, clientAddr, serverPort, clientPort, 
					window_size, time_interval, datagram);
			
			System.out.println("Client Requset Received");
			byte[] request = (byte[])ts.server_receiver.data;
			
			if (request != null) {
				System.out.println("FTP Server received file request");
			} else {
				System.out.println("Request is null");
				System.exit(1);
			}
			
			String filename = new String(request, "UTF-8");
			System.out.println("Requested Filename: " + filename);
			
			// fetch file
			File outfile = new File(filename);
			long remaining_len = outfile.length();
			byte[] buffer = new byte[Math.min(buflen, (int)remaining_len)];
			byte[] md5_content = null;
			boolean first = true;
	
			FileInputStream in = new FileInputStream(filename);
			while (remaining_len > 0) {
				in.read(buffer, 0, (int)(Math.min(remaining_len, buffer.length)));
				if (first) {
					md5_content = constructMd5(buffer);
					ts.send_md5(md5_content);
					System.out.println("MD5 Send");
					first = false;
					Thread.sleep(1000);
				}
				remaining_len -= buflen;
				if (remaining_len > 0) {
					ts.send(buffer, true, true);
				} else {
					ts.send(buffer, true, false);
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Exit Thread");
		Thread.currentThread().interrupt();
		return;
	}
	
	/** Calcualte MD5 */
	public byte[] constructMd5(byte[] buffer) throws NoSuchAlgorithmException {
		byte[] md5_response = new byte[md5_head.length+hash_buf];
		System.arraycopy(md5_head, 0, md5_response, 0, md5_head.length);
		MessageDigest md = MessageDigest.getInstance("MD5");
		byte[] md5_file = md.digest(buffer);
		System.arraycopy(md5_file, 0, md5_response, md5_head.length, hash_buf);
		return md5_response;
	}
}
