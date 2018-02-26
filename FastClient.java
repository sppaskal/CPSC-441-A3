import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Timer;

/**
 * FastClient Class
 * 
 * FastClient implements a basic reliable FTP client application based on UDP data transmission and selective repeat protocol
 * 
 */
public class FastClient {

	public String serverName;
	public int serverPort;
	public int windowSize;
	public int timeOut;
	private TxQueue queue;
	private TxQueueNode queueNode;
	private Timer timer;
	private int sequenceNumber;
	public byte response;
	public Socket socket;
	public DatagramSocket socketUDP;
	public FileInputStream inputStream;
	public InetAddress ip;
	
 	/**
        * Constructor to initialize the program 
        * 
        * @param server_name    server name or IP
        * @param server_port    server port
        * @param window         window size
	* @param timeout	time out value
 	 * @throws UnknownHostException 
        */
	public FastClient(String server_name, int server_port, int window, int timeout) throws UnknownHostException {
	
	/* initialize */
		serverName = server_name;
		serverPort = server_port;
		windowSize = window;
		timeOut = timeout;
		queue = new TxQueue(windowSize);
		ip = InetAddress.getByName("localhost"); //This is needed for the DatagramPacket parameter.	
	}
	
	/* send file 
        * @param file_name      file to be transfered
	*/

	public void send(String file_name) {

		try
		{
			// connects to port server app listesing at port 8888 in the same machine
			socket = new Socket(serverName, 8888);
	
			// Create necessary streams
			DataOutputStream clientOutput= new DataOutputStream(socket.getOutputStream());
			DataInputStream serverResponse = new DataInputStream(socket.getInputStream());
			
			clientOutput.writeUTF(file_name);
			response = serverResponse.readByte();
			
			System.out.println("Server handshake response: " + response);
			System.out.println("-----------------------------------");
			
			File file = new File(file_name);
			inputStream = new FileInputStream(file); //Getting contents of file into inputStream.
			
			//If we get an okay from the server and file exists
			if(response == 0 && file.exists()) {
				
				socketUDP = new DatagramSocket();
				socketUDP.setSoTimeout(timeOut);
				
				ReceiverThread receiveThread = new ReceiverThread(this, socketUDP);
				receiveThread.start();

				byte[] dataChunk = new byte[Segment.MAX_PAYLOAD_SIZE]; //This will contain the byte chunk that will be put in the segment
				sequenceNumber = 0;
				int bytesRead = 0;
				//While there are more bytes to read and queue is not full
				while ((bytesRead = inputStream.read(dataChunk)) != -1) {	
					
					byte[] sendChunk = new byte[bytesRead];
					System.arraycopy(dataChunk, 0, sendChunk, 0, bytesRead);
					//Create the segment, containing the sequence number and the data chunk
					Segment sendSegment = new Segment(sequenceNumber, sendChunk);
					
					while(queue.isFull()){
						Thread.yield();	
					}
		
					//////////////////////////////////////////////////////
					
					
					processSend(sendSegment);
					sequenceNumber++;
					
					//////////////////////////////////////////////////////	
			
				}
				
				//Wait while the queue empties
				while(!queue.isEmpty()) {
					Thread.yield();
				}
				//Once queue is empty kill the ack thread
				receiveThread.kill();
				
			}
						
			//If we get an error
			else{
				System.out.println("Something went wrong");
			}

			clientOutput.writeByte(0); //Close the TCP connection
			
		}	
	
		catch (Exception e)
		{
			System.out.println("Error: " + e.getMessage());
		}
		finally 
		{
			if (socket != null) 
			{
				try 
				{
					socket.close();
					socketUDP.close();
					inputStream.close();
				} 
				catch (IOException ex) 
				{
				// ignore
				}	
			}
		}
	}

	public synchronized void processSend(Segment segment){
			// send seg to the UDP socket
			// add seg to the transmission queue txQueue
			// if txQueue. size () == 1, start the timer
			byte[] segbytes = segment.getBytes();
			DatagramPacket sendPacket = new DatagramPacket(segbytes, segbytes.length, ip, this.serverPort);
			try{
				queue.add(segment);

				socketUDP.send(sendPacket);
				System.out.println("Sent: " + segment.getSeqNum());

				queue.getNode(segment.getSeqNum()).setStatus(TxQueueNode.SENT);
				
				timer = new Timer(true);
				//TimeoutHandler will be called when we have a timeout
				timer.schedule(new TimeoutHandler(this) , this.timeOut);
			
			}catch(IOException e){
				e.printStackTrace();
			}
			catch(IllegalArgumentException e){
				e.printStackTrace();
			}
			catch(InterruptedException e){
				e.printStackTrace();
			}	
	}
	
	public synchronized void processAck(Segment ack) throws InterruptedException{
		// If ack belongs to the current sender window => set the
		// state of segment in the transmission queue as
		// "acknowledged". Also, until an unacknowledged
		// segment is found at the head of the transmission
		// queue, keep removing segments from the queue
		// Otherwise => ignore ack
	
		if(queue.getSegment(ack.getSeqNum()) != null){
			queue.getNode(ack.getSeqNum()).setStatus(TxQueueNode.ACKNOWLEDGED);
			while(queue.getHeadNode() != null && queue.getHeadNode().getStatus() == TxQueueNode.ACKNOWLEDGED){
				queue.remove();
			}
		}
	}
	
	public synchronized void processTime(int sequenceNumber){
		// Keeping track of timer tasks for each segment may
		// be difficult. An easier way is to check whether the
		// time-out happened for a segment that belongs
		// to the current window and not yet acknowledged.
		// If yes => then resend the segment and schedule
		// timer task for the segment.
		// Otherwise => ignore the time-out event.
		
		//Check if segment is in the window and then check if it has already been acknowledged.
		if(queue.getSegment(sequenceNumber) != null && queue.getNode(sequenceNumber).getStatus() != TxQueueNode.ACKNOWLEDGED){
			//Send the segment that has timed out.
			System.out.println("Packet has been dropped... resend packet (" + sequenceNumber + ")");
			processSend(queue.getSegment(sequenceNumber));	
		}
		else{
			return;
		}
	}
	

	public int getSeqNum(){
		return sequenceNumber;
	}
	
    /**
     * A simple test driver
     * @throws UnknownHostException 
     * 
     */
	public static void main(String[] args) throws UnknownHostException {
		int window = 10; //segments
		int timeout = 100; // milli-seconds (don't change this value)
		
		String server = "localhost";
		String file_name = "file100KB";
		int server_port = 8888;
		/*
		// check for command line arguments
		if (args.length == 4) {
			// either provide 3 parameters
			server = args[0];
			server_port = Integer.parseInt(args[1]);
			file_name = args[2];
			window = Integer.parseInt(args[3]);
		}
		else {
			System.out.println("wrong number of arguments, try again.");
			System.out.println("usage: java FastClient server port file windowsize");
			System.exit(0);
		}
		*/
		
		FastClient fc = new FastClient(server, server_port, window, timeout);
		
		System.out.printf("sending file \'%s\' to server...\n", file_name);
		fc.send(file_name);
		System.out.println("file transfer completed.");
	}

}
