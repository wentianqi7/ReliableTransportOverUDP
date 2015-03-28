/*
 *  A Stub that provides datagram send and receive functionality
 *  
 *  Feel free to modify this file to simulate network errors such as packet
 *  drops, duplication, corruption etc. But for grading purposes we will
 *  replace this file with out own version. So DO NOT make any changes to the
 *  function prototypes
 */
package services;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

import datatypes.Datagram;
import datatypes.TTP_Segment;

public class DatagramService {

	private int port;
	private int verbose;
	private DatagramSocket socket;
	private int counter;
	public static final int total_case = 10000;

	public DatagramService(int port, int verbose) throws SocketException {
		super();
		this.port = port;
		this.verbose = verbose;

		socket = new DatagramSocket(port);
	}
	
	public void close_connection() {
		try {
			socket.close();
		}
		catch (Exception e) {
			System.out.println("SOCKET's CLOSE Error");
			e.printStackTrace();
		}
	}

	public void sendDatagram(Datagram datagram) throws IOException {
		Random rand = new Random();
		
		counter=rand.nextInt(total_case);
	
		ByteArrayOutputStream bStream = new ByteArrayOutputStream(1500);
		ObjectOutputStream oStream = new ObjectOutputStream(bStream);
		oStream.writeObject(datagram);
		oStream.flush();

		// Create Datagram Packet
		byte[] data = bStream.toByteArray();
		InetAddress IPAddress = InetAddress.getByName(datagram.getDstaddr());
		DatagramPacket packet = new DatagramPacket(data, data.length,
				IPAddress, datagram.getDstport());
		
		/** Uncomment to test the test cases all at once */
		/**
		if(counter==1) {
			System.out.println("Testing with Delayed Packets...");
			Random r1 = new Random();
			int delay = r1.nextInt(1000) + 500;
			sendDelayedPacket(packet,delay);
			System.out.println("Packet sent after delay of " + delay);
		} else if(counter==2) {
			System.out.println("Testing with Duplicate Packets...");
			Random r2 = new Random();
			int count = r2.nextInt(3) + 3;
			sendDuplicatePackets(packet, count);
			System.out.println("Packet sent " + count + " times");
		} else if(counter==3) {
			System.out.println("Testing with Dropped Packets...");
		} else if(counter==4) {
			System.out.println("Testing with Checksum Error...");
			datagram.setChecksum((short) 2);
		} else if (counter==6) {
			
		} else {
			// Send packet
			socket.send(packet);
		} */
		socket.send(packet);
	}

	public Datagram receiveDatagram() throws IOException,
			ClassNotFoundException {

		byte[] buf = new byte[1500];
		DatagramPacket packet = new DatagramPacket(buf, buf.length);

		socket.receive(packet);

		ByteArrayInputStream bStream = new ByteArrayInputStream(
				packet.getData());
		ObjectInputStream oStream = new ObjectInputStream(bStream);
		Datagram datagram = (Datagram) oStream.readObject();

		return datagram;
	}
	
	/**
	 * Takes a packet and a delay as parameters. It sends the packet after the given delay.
	 * This is the test case to check delayed packets.
	 * 
	 * @param packet
	 * @param delay
	 * @throws IOException
	 */
	private void sendDelayedPacket(DatagramPacket packet,int delay) throws IOException {
		try {
			Thread.sleep(delay);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		socket.send(packet);
	}

	/**
	 * Takes a DatagramPacket and a count as a parameter, and sends the packet
	 * count number of times. This is the test case for duplicate packets.
	 * 
	 * @param packet
	 * @param count
	 * @throws IOException
	 */
	private void sendDuplicatePackets(DatagramPacket packet, int count) throws IOException {
		for(int i = 0; i<count; i++)
			socket.send(packet);
	}
	
	/***
	 * Sending files in out-of-order delivery
	 * 
	 * @param DatagramPacket, the first incoming switch the order with the second incoming
	 * @throws IOException 
	 */
	private void send_out_of_order_packet(DatagramPacket packet1, DatagramPacket packet2) throws IOException {
		socket.send(packet2);
		socket.send(packet1);
	}
	
	
	/***
	 * Bit Flip Error
	 */
	private void flip_bit(Datagram datagram) {
		datagram.setChecksum((short) 2);
	}
	
	/** 
	 * Sending files less to MSS
	 * @throws IOException 
	 * */
	private void send_Less_Than_MSS() throws IOException {
		DatagramPacket packet = new DatagramPacket(new byte[1], 1);
		socket.send(packet);
	}
	
	
}
