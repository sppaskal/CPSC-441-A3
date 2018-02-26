import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
public class ReceiverThread extends Thread {
	
	private DatagramSocket socket = null;
	FastClient fastClient;
	private volatile boolean status = true;
	// define constructor
	public ReceiverThread(FastClient fastClient, DatagramSocket socket) {
		
		this.fastClient = fastClient;	
		this.socket = socket;
	}
	// The run method 
	public void run() { 
	// while not terminated : 
	// 1. receive a DatagramPacket pkt from UDP socket 
	// 2. call processAck(new Segment(pkt) ) in the parent process
		while(status){
			byte[] segBytes = new byte[Segment.MAX_SEGMENT_SIZE];
			try{
				DatagramPacket receivedSegment = new DatagramPacket(segBytes, segBytes.length);
				socket.receive(receivedSegment);
				fastClient.processAck(new Segment(receivedSegment));
				
				
			}
			catch(IOException e){
				e.printStackTrace();
			} 
			catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public boolean kill(){
		status = false;
		return status;
	}
}