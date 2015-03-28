package services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import datatypes.Datagram;
import datatypes.TTP_Segment;

public class TTP_Segment_Receiver_Thread {
	
	/** TTP_Segment Receiver Attributes */
	private String source_address;
	private String destination_address; 
	private short source_port;
	private short destination_port;
	
	/** Constructor */
	public TTP_Segment_Receiver_Thread(String source_address, String destination_address, 
			short source_port, short destination_port) {
		
		this.set_source_address(source_address);
		this.set_destination_address(destination_address);
		this.set_source_port(source_port);
		this.set_destination_port(destination_port);		
	}
	
	/** Receiving TTP_Segment and Calculate TTP_Segment checksum 
	 * @throws IOException */
	public boolean checksum(Datagram datagram) throws IOException {		 
		
		/** Conversion */
		TTP_Segment segment = (TTP_Segment)datagram.getData();
		ByteArrayOutputStream byte_stream = new ByteArrayOutputStream();
		ObjectOutputStream output_stream = new ObjectOutputStream(byte_stream);
		output_stream.writeObject(segment.get_data());
		byte[] data = byte_stream.toByteArray();
		
		short checksum = 0;
		boolean overflow_flag = false;		 
		 
		for (int i = 0; i < (data.length -1); i += 2) {
			overflow_flag = false;
			checksum += (data[i] << 8) | (data[i + 1]);
			 
			if (((((data[i] >> 7) & 0x01) == 1) && (((data[i + 1] >> 7) & 0x01) == 1))) {
				 overflow_flag = true;
			}
		}
		 
		if (overflow_flag == true) {
			 checksum = (short) (checksum + 1);
		}
		 
		checksum = (short) ~checksum;
		
		if (checksum == datagram.getChecksum()) {
			return true;
		}
		
		return false;
	}

	/** GETTER and SETTER functions */
	public String get_source_address() {
		return source_address;
	}

	public void set_source_address(String source_address) {
		this.source_address = source_address;
	}

	public String get_destination_address() {
		return destination_address;
	}

	public void set_destination_address(String destination_address) {
		this.destination_address = destination_address;
	}

	public short get_source_port() {
		return source_port;
	}

	public void set_source_port(short source_port) {
		this.source_port = source_port;
	}

	public short get_destination_port() {
		return destination_port;
	}

	public void set_destination_port(short destination_port) {
		this.destination_port = destination_port;
	}	
}
