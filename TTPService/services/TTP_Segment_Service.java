package services;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

import datatypes.Datagram;
import datatypes.TTP_Segment;

public class TTP_Segment_Service {
	
	/** Previous Layer services */
	private DatagramService datagram_service;
	
	/** Upper Layer Variables */
	public static String request_file_name; 
	
	/** TTP_Segment API Instance */
	private TTP_Segment_Sender_Thread client_sender;
	private TTP_Segment_Sender_Thread server_sender;
	public TTP_Segment_Client_Receiver_Thread client_receiver;
	public TTP_Segment_Server_Receiver_Thread server_receiver;
	
	/** TTP_Segment API Result */
	public List<TTP_Segment> in_order_receiving_TTP_segment;
	
	/** TTP_Segment Constant */
	public static final int CLIENT_INIT_SEQUENCE = 14740;
	private static final int SERVER_INIT_SEQUENCE = 10601;
	public static short MAX_WINDOWS_SIZE = 5;
	public static int MMS = 500 * 1024 * 1024;
	
	/** TTP_Segment Connection Management Variables */
	public byte client_connection_status;
	public byte server_connection_status;
	public static final byte CLOSE = 0;
	public static final byte OPEN = 1;
	public static final byte SYN = 2;
	public static final byte SYN_ACK = 3;
	public static final byte ACK = 4;
	public static final byte FIN = 5;
	public static final byte FIN_ACK = 6;
	public static final byte FETCH_FILE = 7;
	public static final byte FILE_BREAK = 8;
	public static final byte RE_TRANS_FILE = 9;
	public static final byte FILE_TRANSFER_FINISH = 10;
	public static final byte MSL_WAIT = 120;
	
	/** Constructor 
	 * @throws SocketException */
	public TTP_Segment_Service(DatagramService datagram_service) throws SocketException {
		//datagram_service = new DatagramService(port, verbose);
		this.datagram_service = datagram_service;
		client_connection_status = CLOSE;
		server_connection_status = CLOSE;
		
		in_order_receiving_TTP_segment = new LinkedList<TTP_Segment>();
	}
	
	public DatagramService getDatagramService() {
		return datagram_service;
	}
	
	/** TTP_Segment_Service API For Client */	
	/** 
	 * TTP_Segment Create connection from client side 
	 * 
	 * @param client_IP_address
	 * @param server_IP_address
	 * @param client_port
	 * @param server_port
	 * @return void
	 *  */	
	public void create_connection(String client_IP_address, String server_IP_address, 
			short client_port, short server_port, int retrans_timer_interval) {
		
		/** Bidirectional communication */		
		client_sender = new TTP_Segment_Sender_Thread(client_IP_address, server_IP_address, 
				client_port, server_port, datagram_service, retrans_timer_interval);
		
		client_receiver = new TTP_Segment_Client_Receiver_Thread(client_IP_address, server_IP_address, 
				client_port, server_port, datagram_service);
		
		/** Set Client receiver */
		client_sender.set_client_receiver(client_receiver);
		client_receiver.set_sender(client_sender);
		
		/** Stuffing sender and start handshake */
		TTP_Segment segment = client_sender.create_TTP_segment(client_receiver.ack_number, SYN, null);
		segment.set_sequence_number(client_receiver.sequence_number);
		client_sender.send_TTP_segment(segment);
		
		/** Waiting for receive */
		client_receiver.run();		
		
		/** State Transition Management */
		client_connection_status = OPEN;
	}
	
	/** TTP_Segment_Service API For Client */
	/** 
	 * This function is to send requests for file 
	 * @param Object request is a FTP packet 
	 * @return void
	 *  */	
	public void send_request(byte[] request) {
		
		/** Connection management */
		client_connection_status = TTP_Segment_Service.FETCH_FILE;
		
		/** Create segment */
		client_receiver.sequence_number++;
		TTP_Segment segment = client_sender.create_TTP_segment(
				client_receiver.ack_number, TTP_Segment_Service.FETCH_FILE, request);	
		try {
			System.out.println("request filename: "+new String(request, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		segment.set_sequence_number(client_receiver.sequence_number);
		client_sender.send_TTP_segment(segment);
	}
	
	/** TTP_Segment_Service API For Client */
	/** 
	 * This function is to receive requested piece of file 
	 * @param void 
	 * @return void
	 *  */	
	public void receive_file() {
		client_receiver.run();
	}
	
	public void send_md5(byte[] request) {
		/** Create segment */
		TTP_Segment segment = server_sender.create_TTP_segment(
				server_receiver.get_ack_number(), TTP_Segment_Service.ACK, request);
		server_sender.send_TTP_segment(segment);
	}
	
	/** TTP_Segment_Service API
	 * 
	 * This function is used by the FTP server to accept the connection and start file transfer
	 * 
	 * @param source_ip_address
	 * @param destination_ip_address
	 * @param source_port
	 * @param destination_port
	 * @param ack_number
	 * @param windows_size
	 * @return void
	 *  */
	public void listen_connection(String source_address, String destination_address, 
			short source_port, short destination_port, short windows_size, 
			int retrans_timer_interval, Datagram trans_datagram) {
		
		/** Negotiation of windows size */
		MAX_WINDOWS_SIZE = windows_size;
		
		/** Timer timeout */
		
		/** Preparing Sender Thread */
		server_sender = new TTP_Segment_Sender_Thread(source_address, destination_address, 
				source_port, destination_port, datagram_service, retrans_timer_interval);		
		
		/** State Transition Management */
		server_connection_status = OPEN;
		
		/** Waiting for receive */
		server_receiver = new TTP_Segment_Server_Receiver_Thread(
				source_address, destination_address,
				source_port, destination_port,
				datagram_service, trans_datagram
		);		
		
		server_sender.set_receiver(server_receiver);
		server_receiver.set_sender(server_sender);
		
		server_receiver.run();		
	}
	
	/** TTP_Segment_Service API For server */
	/** This function is to send requested file 
	 * @param byte[] data of file
	 * @return void
	 *  */	
	public void send(byte[] data, boolean first_time, boolean not_end) {
		
		int remaining_length = data.length;
		byte[] frag = new byte[MMS];
		int offset = 0;
		
		if (remaining_length < 500) {
			TTP_Segment segment = server_sender.create_TTP_segment(server_receiver.get_ack_number(), ACK, data);
			server_sender.send_TTP_segment(segment);			
			segment = server_sender.create_TTP_segment(server_receiver.get_ack_number(), FILE_TRANSFER_FINISH, null);
			server_sender.send_TTP_segment(segment);
			return ;
		}
		
		while (remaining_length > 0) {
			
			/** Fragments isn't the end */
			if (remaining_length >= MMS) {
				frag = Arrays.copyOfRange(data, offset, offset + MMS);		
				server_sender.send(data, true);
			}
			else {/** Fragments is the end */
				frag = Arrays.copyOfRange(data, offset, offset + remaining_length);
				if (first_time == false) {
					server_sender.re_trans_file();					
				}
				first_time = false;
				server_sender.send(data, not_end);
			}
			
			remaining_length -= MMS;
			offset += MMS;
		}
			
	}
	
	/** TTP_Segment_Service API For Client */
	/** This function is to send requests for file 
	 * @param byte[] request
	 * @return void
	 *  */	
	public File Retrieve_File() {
		File file = client_receiver.Retrieve_File();
		return file;
	}
	
	/** TTP_Segment_Service API for both client and server
	 * This function is to close down the connection to release the resource
	 * @param void
	 * @return void
	 *  */

	public void tear_down_connection() {
		
		/** Stuffing the FIN state */
		TTP_Segment segment = client_sender.create_TTP_segment(client_sender.get_ack_number(), FIN, null);
		segment.set_sequence_number(client_sender.get_sequence_number());
		client_sender.send_TTP_segment(segment);
		client_sender.set_sequence_number(client_sender.get_sequence_number() + 1);
		
		/** State Transition Management */
		client_connection_status = MSL_WAIT;
		
		/** Client close connection */
		datagram_service.close_connection();
	}
	
	/** data_size_of
	 * This is an utility Function to calculate next sequence number
	 * @param Object data
	 * */
	public static int data_size_of(Object data) {
		
		ByteArrayOutputStream byte_output = null;
		try {
			byte_output = new ByteArrayOutputStream();
			ObjectOutputStream object_output = new ObjectOutputStream(byte_output);
			object_output.writeObject(data);
			object_output.close();			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return byte_output.toByteArray().length;
	}
	

	/** Getter Functions */
	/** Setter Functions */
	public static int get_Server_Init_Sequence() {
		return get_server_init_sequence();
	}

	public TTP_Segment_Sender_Thread get_client_sender() {
		return client_sender;
	}
	
	public TTP_Segment_Sender_Thread get_server_sender() {
		return server_sender;
	}

	public void set_client_sender(TTP_Segment_Sender_Thread sender) {
		client_sender = sender;
	}

	public void set_server_sender(TTP_Segment_Sender_Thread sender) {
		server_sender = sender;
	}

	public static int get_server_init_sequence() {
		return SERVER_INIT_SEQUENCE;
	}
	
}
