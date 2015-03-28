package services;

import java.util.TimerTask;

import datatypes.TTP_Segment;

public class TTP_Retransmission_Event extends TimerTask{
	
	private TTP_Segment_Sender_Thread sender_thread;
	
	/** Constructor */
	public TTP_Retransmission_Event(TTP_Segment_Sender_Thread sender_thread) {
		this.sender_thread = sender_thread;
	}

	/** Execute when timeout */
	@Override
	public void run() {
		for (TTP_Segment new_segment : sender_thread.sender_windows) {
			sender_thread.send_TTP_segment(new_segment);
		}
	}
}
