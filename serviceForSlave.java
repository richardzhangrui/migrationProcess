

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;

public class serviceForSlave implements Runnable{
	private processManager pm;
	private ObjectInputStream in;
	private ObjectOutputStream out;
	private Integer sid;

	private Socket sock;
	private InetAddress slaveAddr;
	
	private volatile boolean isRun;
	
	public serviceForSlave(processManager pm, Socket sock) throws IOException {
		this.isRun = true;
		this.sock = sock;
		this.slaveAddr = sock.getInetAddress();
		this.pm = pm;
		try {
			this.in = new ObjectInputStream(this.sock.getInputStream());
			this.out = new ObjectOutputStream(this.sock.getOutputStream());
		} catch (IOException e) {
			e.printStackTrace();
			System.err.printf("ServiceForSlave: addr %s fail to create stream objects!\n", slaveAddr.toString());
		}
	}
	
	public ObjectOutputStream getOut() {
		return out;
	}
	
	public void stopService() {
		isRun = false;
	}
	

	public String getAddress() {
		return slaveAddr.toString(); 
	}	
	public void writeToClient(message msg) {
		try {
			synchronized(out)
			{
				out.writeObject(msg);
			}
		}catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		while (isRun) {
			message msg;
			try {
				Object o = in.readObject();
				if(!(o instanceof message)) {
					System.err.printf("ServiceForSlave: addr %s fail to recognize this message!\n", slaveAddr.toString());
					continue;
				}
				
				msg = (message)o;
				switch(msg.getCommand()) {
					case "C":
//						synchronized(pm.sid) {
//							msg.setSid(pm.sid);
//						}
						pm.addSlave(this);
						msg.setSid(this.sid);
						writeToClient(msg);
						break;
					case "R":
					case "M":
					case "S":
					case "T":
					case "E":
						pm.job(msg, this);
						break;
					default:
						System.err.printf("ServiceForSlave: addr %s unrecognized command!\n",slaveAddr.toString());
					
				}
			} catch (IOException e){ 
				System.err.printf("ServiceForSlave: addr %s fail to read or write object!\n", slaveAddr.toString());
				System.err.printf("ServiceFOrSlave: addr %s disconnected!\n", slaveAddr.toString());
				pm.removeSlave_ts(this.sid);
				this.stopService();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				System.err.printf("ServiceForSlave: addr %s Unrecoginzed object read!\n", slaveAddr.toString());
				e.printStackTrace();
			}
		}
		
		try {
			in.close();
			out.close();
			sock.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	public Integer getSid() {
		return sid;
	}

	public void setSid(Integer sid) {
		this.sid = sid;
	}

}
