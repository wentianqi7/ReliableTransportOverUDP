/** A sample server that uses DatagramService */

package applications;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import datatypes.Datagram;
import datatypes.TTP_Segment;
import services.DatagramService;
import services.TTP_Segment_Service; 

public class FTPServer {
	public static short serverPort;
	public static short clientPort;
	public static short window_size;
	public static int time_interval;
	public static final String serverAddr = "127.0.0.1";
	public static final String clientAddr = "127.0.0.1";
	public static DatagramService datagram_service;
	
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		printUsage();
		if(args.length != 3) { 
			System.exit(-1);; 
		} 
		
		serverPort = Short.parseShort(args[0]);
		window_size = Short.parseShort(args[1]);
		time_interval = Integer.parseInt(args[2]);
		datagram_service = new DatagramService(serverPort, 10);
		
		System.out.println("Server is starting... ");	
		run();		
	}
	
	public static void run() throws IOException {
		Map<Short, TTP_Segment_Service> threadMap = new HashMap<Short, TTP_Segment_Service>();
		
		while (true) {
			//DatagramService datagram_service = ts.getDatagramService();
			Datagram datagram = null;
			TTP_Segment segment = null;
			
			try {
				datagram = datagram_service.receiveDatagram();
				segment = (TTP_Segment)datagram.getData();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			
			if (segment.get_flags() == TTP_Segment_Service.SYN) {
				TTP_Segment_Service ts = new TTP_Segment_Service(datagram_service);
				ts.server_connection_status = TTP_Segment_Service.CLOSE;
				threadMap.put(segment.get_source_port(), ts);
				FTPRequestHandler httpHandler = new FTPRequestHandler(ts, datagram, serverPort,
						window_size, time_interval);
				Thread thread = new Thread(httpHandler);
				thread.start();
			}	
			else {
				TTP_Segment_Service ts = threadMap.get(segment.get_source_port());
				ts.server_receiver.datawarehouse.add(datagram);
				ts.server_receiver.datagram_dataware.release(1);
			}
			
		}
	} 
	
	private static void printUsage() { 
		System.out.println("Usage: server<port> <window_size> <time_interval>"); 
	}
}