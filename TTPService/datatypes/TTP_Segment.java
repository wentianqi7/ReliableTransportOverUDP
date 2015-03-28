package datatypes;
import java.io.Serializable;


public class TTP_Segment implements Serializable{
	
	/** Segment header section */
	private short source_port;
	private short destination_port;
	private int sequence_number;
	private int ack_number;
	private int checksum;
	private byte upper_layer_protocol;
	private byte flags;
	private short windows;
	
	/** Data/Segment payload */
	byte[] data;
	
	/** Constructor */
	public TTP_Segment() {
		super();
	}
	public TTP_Segment(short source_port, short destination_port, 
			int sequence_number, byte flags, short window, byte[] data) {
		
		super();
		this.source_port = source_port;
		this.destination_port = destination_port;
		this.sequence_number = sequence_number;
		this.flags = flags;
		this.windows = window;
		this.data = data;		
	}
	
	/** Getter */
	public short get_source_port() {
		return source_port;
	}
	
	public short get_destination_port() {
		return destination_port;
	}
	
	public int get_sequence_number() {
		return sequence_number;
	}
	
	public int get_ack_number() {
		return ack_number;
	}
	
	public int get_checksum() {
		return checksum;
	}
	
	public byte get_upper_layer_protocol() {
		return upper_layer_protocol;
	}
	
	public byte get_flags() {
		return flags;
	}
	
	public short get_flow_windows() {
		return windows;
	}
	
	public byte[] get_data() {
		return data;
	}
	
	/** Setter */
	public void set_source_port(short source_port) {
		this.source_port = source_port;
	}
	
	public void set_destination_port(short destination_port) {
		this.destination_port = destination_port;
	}
	
	public void set_sequence_number(int sequence_number) {
		this.sequence_number = sequence_number;
	}
	
	public void set_ack_number(int ack_number) {
		this.ack_number = ack_number;
	}
	
	public void set_checksum(int checksum) {
		this.checksum = checksum;
	}
	
	public void set_upper_layer_protocol(byte upper_layer_protocol) {
		this.upper_layer_protocol = upper_layer_protocol;
	}
	
	public void set_flags(byte flags) {
		this.flags = flags;
	}
	
	public void set_flow_windows(short flow_windows) {
		this.windows = flow_windows;
	}
	
	public void set_data(byte[] data) {
		this.data = data;
	}
}
