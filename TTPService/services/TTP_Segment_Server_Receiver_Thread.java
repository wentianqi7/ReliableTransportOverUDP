package services;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.Semaphore;

import datatypes.Datagram;
import datatypes.TTP_Segment;

public class TTP_Segment_Server_Receiver_Thread implements Runnable{
	
	/** Previous Layer API and utility */
	private DatagramService datagram_service;
	private Datagram datagram;
	private boolean istrans;
	
	/** TTP API and utility to upper layer */
	public Object data;
	
	/** TTP_Segment_Server_Receiver_Thread instance */
	private TTP_Segment_Receiver_Thread receiver_thread;	
	private TTP_Segment_Sender_Thread sender_thread;	
	
	/** TTP Segment variable: only used in server sender */
	private int sequence_number;
	private int ack_number;
	
	/** TTP Connection Management */	
	private byte connection_status;

	/** TTP Resource */
	public Semaphore datagram_dataware;
	public LinkedList<Datagram> datawarehouse;
	
	private static int go_back_N_counter = 0;
	
	/** Constructor */
	public TTP_Segment_Server_Receiver_Thread(String source_ip_address, String destination_ip_address,
			short source_port, short destination_port, 
			DatagramService datagram_service, Datagram datagram) {
		
		receiver_thread = new TTP_Segment_Receiver_Thread(
				source_ip_address, destination_ip_address,
				source_port, destination_port);
		
		this.datagram_service = datagram_service;
		this.datagram = datagram;
		this.istrans = true;
		
		sequence_number = TTP_Segment_Service.get_server_init_sequence();
		datawarehouse = new LinkedList<Datagram>();
		datagram_dataware = new Semaphore(0);
	}
	
	/** go_back_N_send 
	 * This function is to sliding the sender's windows 
	 * */
	private void go_back_N_send(TTP_Segment segment) {
		
		/** DEBUG INFO */
		/** System.out.println("GO BACK N " + (go_back_N_counter++) + " times"); */
		
		/** Remove all cumulative ACKed datagrams from sender list */
		for (int i = 0; i < sender_thread.sender_windows.size(); i++) {
			TTP_Segment new_segment = sender_thread.sender_windows.get(i);
			
			if (new_segment.get_sequence_number() < segment.get_ack_number()) {
				sender_thread.sender_windows.remove(i);
				i = i - 1;
			}
		}
		
		/** Timeout cancel, reset timer */
		sender_thread.timer.cancel();
		sender_thread.timer = new Timer();
		TTP_Segment_Sender_Thread.retransmission_timeout_event = new TTP_Retransmission_Event(sender_thread);
		
		/** Retransmission when timeout or when there is out-of-order packet */
		sender_thread.timer.schedule(TTP_Segment_Sender_Thread.retransmission_timeout_event, 
				TTP_Segment_Sender_Thread.get_retransmission_timeout());
		
		
		/** System.out.println("frag size: " + sender_thread.getFrag_segment_list().size()); */
		
		/** Automatically Sliding the windows */		
		while (sender_thread.sender_windows.size() < TTP_Segment_Service.MAX_WINDOWS_SIZE
				&& !sender_thread.getFrag_segment_list().isEmpty()) {
			sender_thread.sender_windows.add(sender_thread.getFrag_segment_list().remove(0));
		}		
		
		for (TTP_Segment new_segment : sender_thread.sender_windows) {
			sender_thread.send_TTP_segment(new_segment);
		}	
		
	}
	
	/** Server receive function handler */
	@Override
	public void run() {
		while (true) {
			if (istrans) {
				istrans = false;
			} else {
				/** Receiving datagrams and unwrapped it */
				try {
					datagram_dataware.acquire(1);
					datagram = datawarehouse.remove(0);
					datagram_dataware.release(0);
					
				} catch (Exception e) {
					e.printStackTrace();
				}
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
			
			
			/** If checksum is correct, handle request */
			byte flag = segment.get_flags();			
			switch (flag) {
				
				case TTP_Segment_Service.SYN:
					
					/** Connection Management */
					connection_status = TTP_Segment_Service.SYN_ACK;
					
					/** Sequence number, acknowledge number management */
					ack_number = segment.get_sequence_number() + 1;
					
					TTP_Segment new_segment = sender_thread.create_TTP_segment(
							ack_number, TTP_Segment_Service.SYN_ACK, null);					
					
					new_segment.set_sequence_number(sequence_number);
					sequence_number += TTP_Segment_Service.data_size_of(new_segment);
					
					/** Send the TTP_Segment */
					sender_thread.send_TTP_segment(new_segment);
		
					break;	
				
				case TTP_Segment_Service.SYN_ACK:	
					
					/** Connection Management */
					connection_status = TTP_Segment_Service.ACK;		
					
					/** Sequence number, acknowledge number management */
					ack_number++;
					break;	
					
				case TTP_Segment_Service.ACK:
					
					/** Connection Management */
					if (sender_thread.getFrag_segment_list().isEmpty()) {
						connection_status = TTP_Segment_Service.FETCH_FILE;
						break;
					}
					
					connection_status = TTP_Segment_Service.ACK;
					
					/** Sequence number, acknowledge number management */
					ack_number++;
					
					go_back_N_send(segment);
					break;
					
				case TTP_Segment_Service.FETCH_FILE:
					
					/** Connection Management */
					connection_status = TTP_Segment_Service.FETCH_FILE;
					
					/** Sequence number, acknowledge number management */
					ack_number++;
					
					data = segment.get_data();
					break;																

				case TTP_Segment_Service.FIN:
					
					/** Connection Management */
					connection_status = TTP_Segment_Service.FIN_ACK;
					
					/** Sequence number, acknowledge number management */
					ack_number++;
					
					sequence_number = segment.get_sequence_number() + 1;
					segment = sender_thread.create_TTP_segment(sender_thread.get_ack_number(), TTP_Segment_Service.FIN_ACK, segment.get_data());
					segment.set_sequence_number(sender_thread.get_sequence_number());
					sender_thread.send_TTP_segment(segment);
					sender_thread.set_sequence_number(sender_thread.get_sequence_number() + 
							TTP_Segment_Service.data_size_of(segment));
					
					sequence_number += TTP_Segment_Service.data_size_of(segment);
					break;						

				case TTP_Segment_Service.CLOSE:
					connection_status = TTP_Segment_Service.CLOSE;
					break;		
					
				default:
					connection_status = TTP_Segment_Service.CLOSE;
					break;
			}			
			
			if (	connection_status == TTP_Segment_Service.FETCH_FILE ||
					connection_status == TTP_Segment_Service.CLOSE) {
				break;
			}
		}		
	}

	/** GETTER and SETTER Functions */
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
	public void set_sender(TTP_Segment_Sender_Thread sender) {
		this.sender_thread = sender;
	}
}
