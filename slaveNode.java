//package mainfolder;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.io.Serializable;

public class slaveNode {

	private Socket sock;
	private ObjectInputStream objIn;
	private ObjectOutputStream objOut;

	private boolean isRun = true;

	//public Integer sid;
	//public Integer pid;

	private int load;
	private String masterHost;
	private int masterPort;

	//private Thread connectThread;
	
	private volatile LinkedList<Integer> suspendedProcess;

	private volatile HashMap<Integer, migratableProcess> pidToProcess;
	private volatile HashMap<Integer, Thread>  pidToThread;

	public slaveNode(String mhost, int mport) {
		this.load = 0;
		//this.pid = 0;
		this.masterHost=mhost;
		this.masterPort = mport;

		suspendedProcess = new LinkedList<Integer>();

		pidToProcess = new HashMap<Integer, migratableProcess>();
		pidToThread = new HashMap<Integer, Thread>();
	}


	public void writeToMaster(message msg) {
		try {
			synchronized(objOut)
			{
				objOut.writeObject(msg);
			}
		}catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	public int getPort() {
		return masterPort;
	}
	
	public void setPort(int port) {
		this.masterPort = port;
	}

	private void removeSuspended_ts(Integer pid) {
		synchronized(suspendedProcess){
			for(int i = 0; i < suspendedProcess.size(); i++) {
				if(pid.equals(suspendedProcess.get(i))) {
					suspendedProcess.remove(i);
					break;
				}
			}
		}
	}

	private void addSuspended_ts(Integer pid) {
		synchronized(suspendedProcess) {
			suspendedProcess.add(pid);
		}
	}

	private void removeProcess_ts(Integer pid) {
		synchronized(pidToProcess) {
			pidToProcess.remove(pid);
		}
	}

	private void addProcess_ts(Integer pid, migratableProcess p) {
		synchronized(pidToProcess) {
			pidToProcess.put(pid, p);
		}
	}

	private void updateProcess_ts(Integer pid, migratableProcess p) {
		addProcess_ts(pid, p);
	}

	private void addPidToThread_ts(Integer pid, Thread t) {
		synchronized(pidToThread) {
			pidToThread.put(pid, t);
		}
	}

	private void removePidToThread_ts(Integer pid) {
		synchronized(pidToThread) {
			pidToThread.remove(pid);
		}
	}


	private void increaseLoad_ts(Integer load) {
		synchronized(load){
			load++;
		}
	}
	
	private void decreaseLoad_ts(Integer load) {
		synchronized(load){
			load--;
		}
	}

	private void suspendProcess(Integer spid, migratableProcess p) {
		updateProcess_ts(spid, p);
		//decreaseLoad_ts(pidToSlave.get(spid));
		//removePidToSlave_ts(spid);
		//removePidToThread_ts(spid);
		addSuspended_ts(spid);
	}

	private boolean isSuspended(int pid) {
		return suspendedProcess.contains(new Integer(pid));
	}
	
	private boolean hasProcess(int pid) {
		return pidToProcess.containsKey(new Integer(pid));
	}






	public void doJob(message msg) {
	
		switch(msg.getCommand()) {
			case "E":
				
				int rpid=msg.getPid();

				if(!hasProcess(rpid)) {
					//System.out.println("Resume process %d failed: there's no process running with this pid!",rpid);
					if(msg.getProcess() != null) {
						Thread t = new Thread(msg.getProcess());
						t.start();
						addPidToThread_ts(msg.getPid(), t);
						addProcess_ts(msg.getPid(),msg.getProcess());
						increaseLoad_ts(this.load);
						this.writeToMaster(msg);
						System.out.println("Resume process done!");
					}

				}
				else if(!isSuspended(rpid)) {
					System.out.println("Resume process failed: this process is not suspended!");
				}
				else {
					migratableProcess p = pidToProcess.get(rpid);
					//if(p != null) {
					p.resume();
					//}
					removeSuspended_ts(rpid);
					increaseLoad_ts(this.load);
					this.writeToMaster(msg);
					System.out.println("Resume process done!");
				}
				break;

			case "R":
				if(msg.getProcess() != null) {
					Thread t = new Thread(msg.getProcess());
					t.start();
					addPidToThread_ts(msg.getPid(), t);
					addProcess_ts(msg.getPid(),msg.getProcess());
					increaseLoad_ts(this.load);
					this.writeToMaster(msg);
					System.out.println("Run process done!");
				}
				break;

			case "M":
				int mpid=msg.getPid();
				if(!hasProcess(mpid)) {
					System.out.println("Migrate process failed: there's no process running with this processID!");
				}
				else if(isSuspended(mpid)) {
					System.out.println("Migrate process failed: This process is suspended!");
				}
				else {
					migratableProcess p = pidToProcess.get(mpid);
					p.suspend();
					suspendProcess(mpid, p);
					decreaseLoad_ts(this.load);
					message newMsg=new message(msg.getSid(),msg.getPid(),p,"M");
					this.writeToMaster(newMsg);
					System.out.println("Migrate process done!");
				}
				break;

			case "S":
				int spid=msg.getPid();
				if(!hasProcess(spid)) {
					System.out.println("Suspend process failed: there's no process running with this processID!");
				}
				else if(isSuspended(spid)) {
					System.out.println("Suspend process failed: This process is suspended!");
				}
				else {
					migratableProcess p = pidToProcess.get(spid);
					p.suspend();
					suspendProcess(spid, p);
					decreaseLoad_ts(this.load);
					message newMsg=new message(msg.getSid(),msg.getPid(),p,"S");
					this.writeToMaster(newMsg);
					System.out.println("Suspend process done!");
				}
				break;

			case "T":
				int tpid=msg.getPid();
				if(!hasProcess(tpid)) {
					System.out.println("Terminate process failed: there's no process running with this processID!");
				}
				else{
					//Integer tsid = pidToSlave.get(tpid);
					migratableProcess p = pidToProcess.get(tpid);
					p.terminate();
					if(!isSuspended(tpid)) {
						decreaseLoad_ts(this.load);
					}
					removeSuspended_ts(tpid);
					removePidToThread_ts(tpid);
					this.writeToMaster(msg);
					System.out.println("Terminate process done!");
				}

				break;

			default:
				System.err.println("Unrecognizable command from master!\n");
		}
	
	}


	public static void main(String[] argv) {
		
		if (argv.length == 2) {
			String mhost=argv[0];
			int mport = Integer.parseInt(argv[1]);
			slaveNode sn= new slaveNode(argv[0], mport);

			try {
				sn.sock = new Socket(argv[0], mport);
			} catch (IOException e) {
				System.err.println("Failed to create socket!");
			}
			
			//OutputStream outStream  = sock.getOutputStream();
			try {
				sn.objOut = new ObjectOutputStream(sn.sock.getOutputStream());
				sn.objIn = new ObjectInputStream(sn.sock.getInputStream());
			} catch (IOException e) {
				e.printStackTrace();
				System.err.printf("Fail to create stream objects!\n");
			}
			

			message msgC = new message(null, null, null, "C");			
			sn.writeToMaster(msgC);
			System.out.println("Connecting to master: " + argv[0]);


			while(sn.isRun){

				message msg;
				try {
					Object o = sn.objIn.readObject();
					if(!(o instanceof message)) {
						//System.err.printf("ServiceForSlave: addr %s fail to recognize this message!\n", slaveAddr.toString());
						continue;
					}
				
					msg = (message)o;
					switch(msg.getCommand()) {
						case "C":
							break;
						case "R":
						case "M":
						case "S":
						case "T":
						case "E":
							sn.doJob(msg);
							break;
						default:
							System.err.printf("Unrecognized command from master!\n");
					
					}
				} catch (IOException e){ 
					System.err.printf("Failed to read or write object!\n");
				} catch (ClassNotFoundException e) {
					System.err.printf("Unrecoginzed object!\n");
					e.printStackTrace();
				}
			}
		
			try {
				sn.objIn.close();
				sn.objOut.close();
				sn.sock.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		else{
			System.err.printf("Usage: slaveNode masterAddress masterPort\n");
		}
	}

}
