

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;

/**
 * serviceForSlave is the service class that responsible for receiving messages from
 * slaves and doing corresponding jobs
 * <p>
 * This class uses objectInputStream and objectOutputStream to transmit objects.
 * 
 * @author      Rui Zhang
 * @author      Jing Gao
 * @version     1.0, 09/15/2013
 * @since       1.0
 */
public class serviceForSlave implements Runnable{
	private processManager pm;
	private ObjectInputStream in;
	private ObjectOutputStream out;
	private Integer sid;

	private Socket sock;
	private InetAddress slaveAddr;
	
	private volatile boolean isRun;
	
	/** 
     * constructor of serviceForSlave class
     * 
     * @param pm		the processManager that doing jobs for the service and maintain 
     * 					information of slaves and processes
     * @param sock		socket that created by managerServer, input and output are through 
     * 					this socket
     * @see processManager
     * @since           1.0
     */
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
	
	/** 
     * get the output stream of the service
     * 
     * @return			return the ObjectOutputStream of the current service
     * @since           1.0
     */
	public ObjectOutputStream getOut() {
		return out;
	}
	
	/** 
     * stop the current service
     * 
     * @since           1.0
     */
	public void stopService() {
		isRun = false;
	}
	
	/** 
     * get the address of the slave connecting to this service
     * 
     * @return			return the address of the slave converted to string
     * @since           1.0
     */
	public String getAddress() {
		return slaveAddr.toString(); 
	}	
	
	/** 
     * write the message to the slave connecting to it
     * 
     * @since           1.0
     */
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
	
	/** 
     * The main service function of this class. It is responsible for message parse and forward
     * corresponding jobs to processManager
     * <p>
     * Message with command "C" is processed here. It means a new slave has connected to this server.
     * 
     * @since           1.0
     */
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
						pm.addSlave_ts(this);
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
	
	/** 
     * get the slave id of the service
     * 
     * @return			return slave id of the service
     * @since           1.0
     */
	public Integer getSid() {
		return sid;
	}
	
	/** 
     * set the slave id of the service
     * 
     * @param sid		slave id of slave that connect to this service
     * @since           1.0
     */
	public void setSid(Integer sid) {
		this.sid = sid;
	}

}
