/* * A sample client that uses DatagramService */ 
package applications;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException; 
import java.util.Arrays;

import datatypes.Datagram;
import datatypes.TTP_Segment;
import services.DatagramService;
import services.TTP_Segment_Service; 

public class FTPClient { 
	private static TTP_Segment_Service ts; 
	public static int fileSize = 0;
	public static final String caddr = "127.0.0.1";
	public static final String saddr = "127.0.0.1";
	public static final String clientPath = "/../";
	public static final int hash_buf = 16;
	public static final int buflen = 1024 * 1024 * 500;
	/**
	* @param args
	* @throws IOException 
	* @throws ClassNotFoundException 
	* @throws NoSuchAlgorithmException 
	*/ 
	public static void main(String[] args) throws IOException, ClassNotFoundException { 
		printUsage();
		if (args.length != 5) {
			System.exit(-1);
		}
	
		// variables read from command line
		short clientPort = Short.parseShort(args[0]); 
		short serverPort = Short.parseShort(args[1]);
		String fileName = args[2];
		int windows_size = Integer.parseInt(args[3]);
		int time_interval = Integer.parseInt(args[4]);
		
		String store_filepath = System.getProperty("user.dir") + clientPath;
		byte[] md5_head = "MD5".getBytes();
		byte[] head_bytes = new byte[md5_head.length];
		byte[] md5_body = new byte[hash_buf];
		
		System.out.println("Starting client ...");
		// initiate ttp service
		DatagramService datagram_service = new DatagramService(clientPort, 10);
		ts = new TTP_Segment_Service(datagram_service); 
		ts.client_connection_status = TTP_Segment_Service.CLOSE;
		// Client initiates connection to the server using TTP
		ts.create_connection(caddr, saddr, clientPort, serverPort, time_interval);
		System.out.println("Client Connection established.\n");
		
		/* Create a FTP segment */
		byte[] request = fileName.getBytes(Charset.forName("UTF-8"));
		
		try {
			/* Client send FTP segment */
			ts.send_request(request);
			System.out.println("Client has sent request with filename\n");

			// start to receive data
			ts.receive_file();
			
			System.out.println("Transfer Finished");
			
			// retrieve md5
			byte[] md5_info = ts.client_receiver.md5_response;
			System.arraycopy(md5_info, 0, head_bytes, 0, md5_head.length);
			if (Arrays.equals(head_bytes, md5_head)) {
				System.out.println("MD5 Received");
				System.arraycopy(md5_info, head_bytes.length, md5_body, 0, hash_buf);
			}
			
			// receive data and verification
			File respFile = ts.Retrieve_File();
			System.out.println("file length: "+respFile.length());
			byte[] data = new byte[Math.min((int)respFile.length(), buflen)];
			InputStream ios = null;
			try {
				ios = new FileInputStream(respFile);
				File file =new File(store_filepath + fileName);
				if (file.exists()) {
					file.delete();
				}
				FileOutputStream fos = new FileOutputStream(file,true);
				BufferedOutputStream bs = new BufferedOutputStream(fos);
				
				boolean first = true;
				while ( ios.read(data) != -1) {
					if (first) {
						MessageDigest md = MessageDigest.getInstance("MD5");
						byte[] md5_compute = md.digest(data);
						
						if (Arrays.equals(md5_body, md5_compute)) {
							// computed and reference md5 are identical
							System.out.println("MD5 Verified");
							// store the data in local file
						} else {
							System.out.println("Error: MD5 digest does not match");
							System.exit(-1);
						}
						first = false;
					}
					
					bs.write(data);
		        } 
				bs.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			File todelete = new File(ts.client_receiver.tempPath);
			todelete.delete();
			
			/** Client close connection */
			ts.tear_down_connection();
			System.out.println("Client Connection closed.");
			System.out.println("Client Exit Without Error.");
			System.exit(0);
		} catch (Exception e) {
			e.printStackTrace();
		}
	} 
	
	private static void printUsage() {
		System.out.println("Usage: server <localport> <serverport> <filename> <windows size> <time_interval>\n"); 
	}
}