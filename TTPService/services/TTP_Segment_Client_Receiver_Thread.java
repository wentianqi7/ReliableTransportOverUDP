package services;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import datatypes.Datagram;
import datatypes.TTP_Segment;

public class TTP_Segment_Client_Receiver_Thread implements Runnable{
	
	/** Previous Layer API and utility */
	private DatagramService datagram_service;
	private Datagram datagram;
	
	/** TTP_Segment_Thread Utility */
	private TTP_Segment_Receiver_Thread receiver_thread;
	private TTP_Segment_Sender_Thread sender_thread;
	
	/** TTP_Segment_Client_Receiver_Thread connection management */
	private List<TTP_Segment> in_order_buffer;
	public int sequence_number;
	public int ack_number;
	private byte connection_status;
	private byte previous_connection_status;
	private byte windows_N;

	public byte[] md5_response;
	public String tempPath;
	
	/** Constructor */
	public TTP_Segment_Client_Receiver_Thread(String source_ip_address, String destination_ip_address,
			short source_port, short destination_port, DatagramService datagramService) {
		
		receiver_thread = new TTP_Segment_Receiver_Thread(
				source_ip_address, destination_ip_address,
				source_port, destination_port);
		
		in_order_buffer = new LinkedList<TTP_Segment>();
		
		datagram_service = datagramService;		
		sequence_number = TTP_Segment_Service.CLIENT_INIT_SEQUENCE;
		ack_number = 0;		
		windows_N = 0;
		previous_connection_status = 0;
	}
	
	/** Reassemble and Retrieve file
	 * This function is to reassemble TTP segment into the file
	 * @param void
	 * @return File
	 *  */
	public File Retrieve_File() {
		String filename = Long.toString(System.currentTimeMillis());
		this.tempPath = "./"+filename;
		/** create new file to assemble the segment */
		File file = new File(tempPath);
		if (file.exists()) {
			file.delete();
		}
		
		FileOutputStream file_stream = null;
		
		try {
			file_stream = new FileOutputStream(file, true);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		for (TTP_Segment segment : in_order_buffer) {
			
			byte[] data;			
			try {
				data = segment.get_data();
				file_stream.write(data);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return file;
	}
	
	/** Client receiving handler */
	@Override
	public void run() {
		while (true) {
			
			/** Receiving datagrams and unwrapped it */
			try {
				datagram = datagram_service.receiveDatagram();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			/** Extract information and placing them in the out-of-order buffer */
			TTP_Segment segment = (TTP_Segment)datagram.getData();
			
			/** If Checksum is incorrect, discard */
			try {
				if (!receiver_thread.checksum(datagram)) {
					continue;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if (segment.get_data() != null) {
				byte[] md5_content = segment.get_data();
				byte[] sample_head = "MD5".getBytes();
				byte[] md5_head = new byte[sample_head.length];
				System.arraycopy(md5_content, 0, md5_head, 0, sample_head.length);
				if (Arrays.equals(sample_head, md5_head)) {
					System.out.println("MD5 Captured");
					md5_response = md5_content;
					continue;
				}
			}
			
			/** If checksum is correct */
			byte flag = segment.get_flags();			
			switch (flag) {				
					
				/** Connection handshake establishment */
				case TTP_Segment_Service.SYN_ACK:
					
					/** Connection status management */
					connection_status = TTP_Segment_Service.SYN_ACK;
					
					/** Sequence number and acknowledge number management */
					sequence_number++;
					ack_number = segment.get_sequence_number() + TTP_Segment_Service.data_size_of(segment);
					
					/** Create TTP Segment */
					TTP_Segment new_segment = sender_thread.create_TTP_segment(ack_number, TTP_Segment_Service.SYN_ACK, null);
					new_segment.set_sequence_number(sequence_number);
					sender_thread.send_TTP_segment(new_segment);
					break;
				
				case TTP_Segment_Service.ACK:
					
					/** Check if the TTP_Segment is out-of-order and duplicate
					 * @protocol GO-BACK-N Protocol 
					 * If it is, trigger ACK
					 * If it isn't, accumulate ack number until N = windows size, Trigger ACK					 * 
					 * */
			
					/** Connection status management */
					connection_status = TTP_Segment_Service.ACK;
					
					if (previous_connection_status == TTP_Segment_Service.RE_TRANS_FILE) {
						ack_number += TTP_Segment_Service.data_size_of(segment);
						previous_connection_status = 0;
					}
					
					if (segment.get_sequence_number() != ack_number) {
						sequence_number++;
						TTP_Segment new_segment2 = sender_thread.create_TTP_segment(ack_number, TTP_Segment_Service.ACK, null);
						new_segment2.set_sequence_number(sequence_number);
						sender_thread.send_TTP_segment(new_segment2);
						break;
					}
					
					/** In-order sequence packet */
					if (segment.get_sequence_number() == ack_number) {
						ack_number = segment.get_sequence_number() + TTP_Segment_Service.data_size_of(segment);
						in_order_buffer.add(segment);
						windows_N ++;
						
						if (windows_N == TTP_Segment_Service.MAX_WINDOWS_SIZE) {
							windows_N = 0;
							sequence_number++;
							TTP_Segment new_segment2 = sender_thread.create_TTP_segment(ack_number, TTP_Segment_Service.ACK, null);
							new_segment2.set_sequence_number(sequence_number);
							sender_thread.send_TTP_segment(new_segment2);
						}
					}
					
					break;
					
				case TTP_Segment_Service.FILE_BREAK:
					windows_N = 0;
					sequence_number++;
					ack_number = segment.get_sequence_number() + TTP_Segment_Service.data_size_of(segment);
					TTP_Segment new_segment2 = sender_thread.create_TTP_segment(ack_number, TTP_Segment_Service.ACK, null);
					new_segment2.set_sequence_number(sequence_number);
					sender_thread.send_TTP_segment(new_segment2);
					break;
				
				case TTP_Segment_Service.FILE_TRANSFER_FINISH:
					
					if (segment.get_sequence_number() != ack_number) {
						sequence_number++;
						
						
					}					
					/** Connection Management */
					connection_status = TTP_Segment_Service.CLOSE;
					break;
				
				case TTP_Segment_Service.RE_TRANS_FILE:
					ack_number = segment.get_sequence_number();
					previous_connection_status = TTP_Segment_Service.RE_TRANS_FILE;
					break;
				
				case TTP_Segment_Service.FIN_ACK:
					
					/** Connection status management */
					connection_status = TTP_Segment_Service.CLOSE;
					
					/** Sequence number, acknowledge number management */
					sequence_number++;
					new_segment2 = sender_thread.create_TTP_segment(ack_number, TTP_Segment_Service.ACK, null);
					new_segment2.set_sequence_number(sequence_number);					
					
					/** Send TTP_Segment packets */
					sender_thread.send_TTP_segment(new_segment2);
					
					/** Wait for 2 MSL to ensure the closure of the connection on server*/
					try {
						Thread.sleep(2 * TTP_Segment_Service.MSL_WAIT * 1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					/** Send TTP_Segment packets */
					sequence_number++;
					new_segment2 = sender_thread.create_TTP_segment(ack_number, TTP_Segment_Service.CLOSE, null);
					new_segment2.set_sequence_number(sequence_number);	
					sender_thread.send_TTP_segment(new_segment2);
					
					break;		
					
				
				case TTP_Segment_Service.CLOSE:
					connection_status = TTP_Segment_Service.CLOSE;
					break;
				
				default:
					connection_status = TTP_Segment_Service.CLOSE;
					break;
			}
			
			
			if (connection_status == TTP_Segment_Service.SYN_ACK ||
					connection_status == TTP_Segment_Service.CLOSE) {
				break;
			}
		}		
	}

	
	public void set_sender(TTP_Segment_Sender_Thread sender) {
		this.sender_thread = sender;
	}
}
