package mainfolder;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class managerServer implements Runnable{

	private ServerSocket listener;
	private boolean isRun = true;
	private processManager pm;
	
	public managerServer(processManager pm) throws IOException {
		this.pm = pm;
		try {
			listener = new ServerSocket(pm.getPort());
		} catch (IOException e) {
			System.err.println("managerServer: Create Server Socket Error!");
		}
	}
	
	public void stopServe() {
		isRun = false;
	}
	
	public void resumeServe() {
		isRun = true;
	}
	
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
