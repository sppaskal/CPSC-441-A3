import java.util.TimerTask;

class TimeoutHandler extends TimerTask {
	private FastClient fastClient;
	// define constructor
	public TimeoutHandler(FastClient fastClient){
		this.fastClient = fastClient;
	}
	// The run method 
	public void run() { // call processTimeout () in the main class 
		this.fastClient.processTime(fastClient.getSeqNum());
	}
}