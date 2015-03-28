package services;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;

import datatypes.Datagram;
import datatypes.TTP_Segment;


public class TTP_Segment_Sender_Thread {
	
	/** Datagrams service API */
	private DatagramService datagram_service;
	
	public List<TTP_Segment> sender_windows;
	
	/** TTP Recevier */
	private TTP_Segment_Server_Receiver_Thread server_receiver;
	private TTP_Segment_Client_Receiver_Thread client_receiver;
	
	/** TTP Segment */	
	private short source_port;
	private short destination_port;
	private String source_address;
	private String destination_address;
	private int sequence_number;
	private int ack_number;
	private TTP_Segment segment;
	
	/** Fragment segments lists */
	private List<TTP_Segment> frag_segment_list;
	
	/** TTP constant */
	private final int MSS = 500;	
	
	/** TTP Retransmission Timer and Timeout events */
	public Timer timer;
	public static TTP_Retransmission_Event retransmission_timeout_event;
	private static int retransmission_timeout = 3600 * 3;
	
	/** Constructor */
	public TTP_Segment_Sender_Thread (String source_ip_address, String destination_ip_address,
			short source_port, short destination_port, DatagramService datagram_service, int timeout) {
		
		super();
		this.source_address = source_ip_address;
		this.destination_address = destination_ip_address;
		this.source_port = source_port;
		this.destination_port = destination_port;		
		this.frag_segment_list = new LinkedList<TTP_Segment>();
		this.datagram_service = datagram_service;
		this.retransmission_timeout = timeout * 3600;
		sender_windows = new LinkedList<TTP_Segment>();
		
		timer = new Timer();
	}
	
	/** Checksum 
	 * This function is to calculate the checksum of the 
	 * @param
	 * @void
	 * */
	public int checksum_of(byte[] data) {
		/** Checksum */
		short checksum = 0;
		int overflow_flag = 0;
		 
		for (int i = 0; i < (data.length -1); i += 2) {
			overflow_flag = 0;
			checksum += (data[i] << 8) | (data[i + 1]);
			 
			if (((((data[i] >> 7) & 0x01) == 1) && (((data[i + 1] >> 7) & 0x01) == 1))) {
				overflow_flag = 1;
			}
		}
		 
		if (overflow_flag == 1) {
			checksum = (short) (checksum + 1);
		}
		 
		checksum = (short) ~checksum;
		return checksum;
	}
	
	/** File fragmentation 
	 * This function is to fragment ftp file into TTP segment
	 * @param byte[] sending_buffer
	 * @return void
	 * */
	public void fragmentate(byte[] sending_buffer, boolean frag_flag) {
		
		if (sending_buffer == null) {
			sending_buffer = new byte[1];
		}
		
		int remaining_length = sending_buffer.length;
		byte[] TTP_segment = new byte[MSS];
		int offset = 0;
		
		while (remaining_length > 0) {
			
			/** Fragments isn't the end */
			if (remaining_length >= MSS) {
				TTP_segment = Arrays.copyOfRange(sending_buffer, offset, offset + MSS);		
			}
			else {
				TTP_segment = Arrays.copyOfRange(sending_buffer, offset, offset + remaining_length);
			}
			
			remaining_length -= MSS;
			offset += MSS;
			
			/** Sequence number, acknowledge number management */			
			TTP_Segment new_segment = new TTP_Segment(this.source_port, this.destination_port,
					server_receiver.get_sequence_number(), TTP_Segment_Service.ACK, 
					TTP_Segment_Service.MAX_WINDOWS_SIZE, TTP_segment);
	
			new_segment.set_sequence_number(server_receiver.get_sequence_number());
			new_segment.set_ack_number(server_receiver.get_ack_number());
			
			server_receiver.set_sequence_number(server_receiver.get_sequence_number()
					+ TTP_Segment_Service.data_size_of(new_segment));
			/** Calculate and set checksum */
			int checksum = checksum_of(TTP_segment);
			new_segment.set_checksum(checksum);
			
			/** Append to the sending queue */
			frag_segment_list.add(new_segment);
		}
		if (frag_flag == true) {
			TTP_Segment new_segment = new TTP_Segment(this.source_port, this.destination_port,
					server_receiver.get_sequence_number(), TTP_Segment_Service.FILE_BREAK, 
					TTP_Segment_Service.MAX_WINDOWS_SIZE, TTP_segment); 
			
			new_segment.set_data(null);
			new_segment.set_sequence_number(server_receiver.get_sequence_number());
			new_segment.set_ack_number(server_receiver.get_ack_number());
			
			server_receiver.set_sequence_number(server_receiver.get_sequence_number()
					+ TTP_Segment_Service.data_size_of(new_segment));
			
			/** Calculate and set checksum */
			int checksum = checksum_of(TTP_segment);
			new_segment.set_checksum(checksum);
			
			/** Append to the sending queue */
			frag_segment_list.add(new_segment);
		}
		
		else {
			TTP_Segment new_segment = new TTP_Segment(this.source_port, this.destination_port,
					server_receiver.get_sequence_number(), TTP_Segment_Service.FILE_TRANSFER_FINISH, 
					TTP_Segment_Service.MAX_WINDOWS_SIZE, TTP_segment); 
			
			new_segment.set_data(null);
			new_segment.set_sequence_number(server_receiver.get_sequence_number());
			new_segment.set_ack_number(server_receiver.get_ack_number());
			
			server_receiver.set_sequence_number(server_receiver.get_sequence_number()
					+ TTP_Segment_Service.data_size_of(new_segment));
			
			/** Calculate and set checksum */
			int checksum = checksum_of(TTP_segment);
			new_segment.set_checksum(checksum);
			
			/** Append to the sending queue */
			frag_segment_list.add(new_segment);
		}
	}
	
	/** Encapsulate with Datagram
	 * The function is to encapsulate the TTP_segment to Datagram
	 * @param TTP_Segment
	 * @return void
	 */
	 public Datagram Encapsulate(TTP_Segment segment) {
		 
		 /** Using Datagram API */
		Datagram datagram = new Datagram();
		datagram.setSrcaddr(this.source_address);
		datagram.setDstaddr(this.destination_address);
		datagram.setSrcport(this.source_port);
		datagram.setDstport(this.destination_port);		
		 
		/** Calculate checksum */
		/** Conversion: from Object to byte[] through stream */
		ByteArrayOutputStream byte_stream = new ByteArrayOutputStream();
		ObjectOutputStream object_stream = null;
			
		try {
			object_stream = new ObjectOutputStream(byte_stream);
			object_stream.writeObject(segment.get_data());
		}
		catch (IOException e) {
			e.printStackTrace();
		}
			
		byte[] data = byte_stream.toByteArray();
		
		/** Checksum */
		short checksum = 0;
		int overflow_flag = 0;
		 
		for (int i = 0; i < (data.length -1); i += 2) {
			overflow_flag = 0;
			checksum += (data[i] << 8) | (data[i + 1]);
			 
			if (((((data[i] >> 7) & 0x01) == 1) && (((data[i + 1] >> 7) & 0x01) == 1))) {
				overflow_flag = 1;
			}
		}
		 
		if (overflow_flag == 1) {
			checksum = (short) (checksum + 1);
		}
		 
		checksum = (short) ~checksum;
	 	
		datagram.setChecksum(checksum);
		datagram.setData(segment);
		return datagram;
	 }
	
	 public TTP_Segment create_TTP_segment(int ack_number, byte flags, byte[] data) {
		
		 /** Create new memory for TTP_Segment */
		 this.segment = new TTP_Segment(this.source_port, this.destination_port, 
				 sequence_number, flags, TTP_Segment_Service.MAX_WINDOWS_SIZE, data);
		 
		 segment.set_ack_number(ack_number);
		 sequence_number += TTP_Segment_Service.data_size_of(segment);
		 return this.segment;
	 }
	
	public void send_TTP_segment(TTP_Segment segment) {
		Datagram datagram = Encapsulate(segment);
		
		try {
			datagram_service.sendDatagram(datagram);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/** Overloading Send File
	 * @param byte[] sending_buffer
	 * @return boolean indicator of result of the operation
	 * */
	public boolean send(byte[] sending_buffer, boolean frag_flag) {
		
		/** Fragmented the sending_buffer into instance variable segment list */
		fragmentate(sending_buffer, frag_flag);
		
		/** Send the first windows size TTP_segment */
		while (sender_windows.size() < TTP_Segment_Service.MAX_WINDOWS_SIZE) {
			sender_windows.add(frag_segment_list.remove(0));
		}
		
		for (TTP_Segment segment : sender_windows) {
			send_TTP_segment(segment);
		}		

		/** Timeout counting start */
		retransmission_timeout_event = new TTP_Retransmission_Event(this);
		timer.schedule(retransmission_timeout_event, retransmission_timeout);
		
		server_receiver.run();
		return true;
	}

	/** Overloading Send File
	 * The function implements the same functionality as the send_TTP_Segment except for the absence of timer of retransmission
	 * @param byte[] sending_buffer
	 * @return boolean indicator of result of the operation
	 * */
	public void re_trans_file() {			
		
		/** Send TTP_segment */
		TTP_Segment new_segment = new TTP_Segment(this.source_port, this.destination_port,
				server_receiver.get_sequence_number(), TTP_Segment_Service.RE_TRANS_FILE, 
				TTP_Segment_Service.MAX_WINDOWS_SIZE, new byte[1]); 
		
		new_segment.set_data(null);
		new_segment.set_sequence_number(server_receiver.get_sequence_number());
		new_segment.set_ack_number(server_receiver.get_ack_number());
		
		server_receiver.set_sequence_number(server_receiver.get_sequence_number());
		
		/** Calculate and set checksum */
		int checksum = checksum_of(new byte[1]);
		new_segment.set_checksum(checksum);
	}
	

	/** Getter Functions */
	/** Setter Functions */
	public int get_sequence_number() {
		return this.sequence_number;
	}
	
	public void set_sequence_number(int sequence_number) {
		this.sequence_number = sequence_number;
	}	
	
	public int get_ack_number() {
		return ack_number;
	}

	public void set_ack_number(int ack_number) {
		this.ack_number = ack_number;
	}
	
	public List<TTP_Segment> getFrag_segment_list() {
		return frag_segment_list;
	}

	public void setFrag_segment_list(List<TTP_Segment> frag_segment_list) {
		this.frag_segment_list = frag_segment_list;
	}

	public static int get_retransmission_timeout() {
		return retransmission_timeout;
	}
	
	public void set_receiver(TTP_Segment_Server_Receiver_Thread server_receiver) {
		this.server_receiver = server_receiver;
		return ;
	}
	
	public void set_client_receiver(TTP_Segment_Client_Receiver_Thread client_receiver) {
		this.client_receiver = client_receiver;
		return ;
	}
}
