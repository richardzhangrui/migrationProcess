

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * ManagerServer is the server class that can accept upcoming slaves.
 * 
 * @author      Rui Zhang
 * @author      Jing Gao
 * @version     1.0, 09/15/2013
 * @since       1.0
 */
public class managerServer implements Runnable{

	private ServerSocket listener;
	private volatile boolean isRun = true;
	private processManager pm;
	
	/** 
     * constructor of managerServer class
     * 
     * @param pm		processManager that creates this server
     * @see processManager
     * @since           1.0
     */
	public managerServer(processManager pm) throws IOException {
		this.pm = pm;
		try {
			listener = new ServerSocket(pm.getPort());
		} catch (IOException e) {
			System.err.println("managerServer: Create Server Socket Error!");
		}
	}
	
	/** 
     * stop the listening for upcoming slaves
     * 
     * @since           1.0
     */
	public void stopServe() {
		isRun = false;
	}
	
	/** 
     * resume the listening for upcoming slaves
     * 
     * @since           1.0
     */
	public void resumeServe() {
		isRun = true;
	}
	
	/** 
     * main start method of the server
     * <p>
     * It creates a socket to listen to connection. Once there's a new connection, it 
     * creates a new serForSlave instance to serve the new slave. And this new instance
     * is running on a new thread.
     * 
     * @see serviceForSlave
     * @since           1.0
     */
	@Override
	public void run() {
		// TODO Auto-generated method stub
		while (isRun) {
			try {
				Socket sock = listener.accept();
                serviceForSlave s = new serviceForSlave(pm, sock);
                Thread t = new Thread(s);
                t.start();
            } catch (IOException e) {
                System.err.println("managerServer: Create new thread error!");
            }

		}
	}
	

}
