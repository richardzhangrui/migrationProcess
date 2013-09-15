

import java.net.Socket;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * slaveNode is the class running on slave nodes, responsible for connecting to
 * master node, receiving messages from master node, doing corresponding jobs, 
 * replying to the master node, and periodically reaping local failures
 * 
 * @author      Rui Zhang
 * @author      Jing Gao
 * @version     1.0, 09/15/2013
 * @since       1.0
 */
public class slaveNode {

	private Socket sock;
	private ObjectInputStream objIn;
	private ObjectOutputStream objOut;

	private boolean isRun = true;

	private Integer sid;

	private int load;
	private String masterHost;
	private int masterPort;

	
	private volatile LinkedList<Integer> suspendedProcess;

	private volatile HashMap<Integer, migratableProcess> pidToProcess;
	private volatile HashMap<Integer, Thread>  pidToThread;

	/** 
     * constructor of slaveNode class
     * 
     * @param mhost		host name of master node
     * @param mport		listening/accepting port of master node
     * @since           1.0
     */
	public slaveNode(String mhost, int mport) {
		this.load = 0;
		//this.pid = 0;
		this.masterHost=mhost;
		this.masterPort = mport;

		suspendedProcess = new LinkedList<Integer>();

		pidToProcess = new HashMap<Integer, migratableProcess>();
		pidToThread = new HashMap<Integer, Thread>();
	}

	/** 
     * write the message to the master node
     * 
     * @since           1.0
     */
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

	/** 
     * get the port number of the master node
     * 
     * @since           1.0
     */
	public int getPort() {
		return masterPort;
	}
	
	/** 
     * get the port number of the master node
     * 
     * @since           1.0
     */
	public void setPort(int port) {
		this.masterPort = port;
	}

	/** 
     * remove a entry with process id "pid" from the suspendedProcess list thread safely
     * 
     * @param pid       the process id of the process whose list will be updated
     * @since           1.0
     */
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

	/** 
     * add a entry with process id "pid" to the suspendedProcess list thread safely
     * 
     * @param pid       the process id of the process whose list will be updated
     * @since           1.0
     */
	private void addSuspended_ts(Integer pid) {
		synchronized(suspendedProcess) {
			suspendedProcess.add(pid);
		}
	}

	/** 
     * remove a entry with process id "pid" from the pidToProcess hashmap thread safely
     * 
     * @param pid       the process id of the process whose hashmap will be updated
     * @since           1.0
     */
	private void removeProcess_ts(Integer pid) {
		synchronized(pidToProcess) {
			pidToProcess.remove(pid);
		}
	}

	/** 
     * add a entry with process id "pid" to the pidToProcess hashmap thread safely
     * 
     * @param pid       the process id of the process whose hashmap will be updated
     * @since           1.0
     */
	private void addProcess_ts(Integer pid, migratableProcess p) {
		synchronized(pidToProcess) {
			pidToProcess.put(pid, p);
		}
	}

	/** 
     * update a entry with process id "pid" in the pidToProcess hashmap thread safely
     * 
     * @param pid       the process id of the process whose hashmap will be updated
     * @since           1.0
     */
	private void updateProcess_ts(Integer pid, migratableProcess p) {
		addProcess_ts(pid, p);
	}

	/** 
     * add a entry with process id "pid" in the pidToThread hashmap thread safely
     * 
     * @param pid       the process id of the process whose hashmap will be updated
     * @since           1.0
     */
	private void addPidToThread_ts(Integer pid, Thread t) {
		synchronized(pidToThread) {
			pidToThread.put(pid, t);
		}
	}

	/** 
     * remove a entry with process id "pid" from the pidToThread hashmap thread safely
     * 
     * @param pid       the process id of the process whose hashmap will be updated
     * @since           1.0
     */
	private void removePidToThread_ts(Integer pid) {
		synchronized(pidToThread) {
			pidToThread.remove(pid);
		}
	}

	/** 
     * decrease the load of the slave thread safely
     * 
     * @param load      local load, i. e., the number of running processes on this slave
     * @since           1.0
     */
	private void increaseLoad_ts(Integer load) {
		synchronized(load){
			load++;
		}
	}
	
	/** 
     * increase the load of the slave thread safely
     * 
     * @param load      local load, i. e., the number of running processes on this slave
     * @since           1.0
     */
	private void decreaseLoad_ts(Integer load) {
		synchronized(load){
			load--;
		}
	}

	/** 
     * Suspend the process with process id spid and update the corresponding information
     * <p>
     * Because process may be migrated to other machines if suspended the process state 
     * should be updated once suspending. Suspending a process will decrease the load of
     * the current slave, so there's no reason to migrate a suspended process. That is, the
     * suspended processes cannot be migrated
     *
     * @param spid		process id of the process that will be suspended
     * @param p			process that will be suspended and updated
     * @see	migratableProcess
     * @since           1.0
     */
	private void suspendProcess(Integer spid, migratableProcess p) {
		updateProcess_ts(spid, p);
		addSuspended_ts(spid);
	}

	/** 
     * check whether the process with the prcocess id pid is suspended
     *
     * @param pid       the process id of the process that will be checked
     * @since           1.0
     */
	private boolean isSuspended(int pid) {
		return suspendedProcess.contains(new Integer(pid));
	}

	/** 
     * check whether the process with process id pid exist
     *
     * @param pid       the process id of the process that will be checked
     * @since           1.0
     */	
	private boolean hasProcess(int pid) {
		return pidToProcess.containsKey(new Integer(pid));
	}

    /** 
     * This is where the slave node deals with master's messages and does corresponding jobs.
     * It parses the message and get the command and associated information such as process,
     * sid and pid, then run/migrate/suspend/resume/terminate the specified process accordin
     * to the command.
     * 
     * @param msg       the message from master node
     * @since           1.0
     */
	public void doJob(message msg) {
	
		switch(msg.getCommand()) {
			case "E":
				
				int rpid=msg.getPid();

				if(!hasProcess(rpid)) {
					if(msg.getProcess() != null) {
						migratableProcess p = msg.getProcess();
						Thread t = new Thread(p);
						t.start();
						p.resume();
						
						addPidToThread_ts(rpid, t);
						addProcess_ts(rpid,p);
						increaseLoad_ts(this.load);
						this.writeToMaster(msg);
						System.out.println("Resume process "+rpid+" done!");
					}

				}
				else if(!isSuspended(rpid)) {
					System.out.println("Resume process failed: process "+rpid+" is not suspended!");
				}
				else {
					migratableProcess p = pidToProcess.get(rpid);
					p.resume();
					removeSuspended_ts(rpid);
					increaseLoad_ts(this.load);
					this.writeToMaster(msg);
					System.out.println("Resume process "+rpid+" done!");
				}
				break;

			case "R":
				int runpid=msg.getPid();
				if(msg.getProcess() != null) {
					migratableProcess p = msg.getProcess();
					Thread t = new Thread(p);
					t.start();
					addPidToThread_ts(runpid, t);
					addProcess_ts(runpid,p);
					increaseLoad_ts(this.load);
					this.writeToMaster(msg);
					System.out.println("Run process "+runpid+" done!");
				}
				break;

			case "M":
				int mpid=msg.getPid();
				if(!hasProcess(mpid)) {
					System.out.println("Migrate process failed: there's no process running with processID: "+mpid);
				}
				else if(isSuspended(mpid)) {
					System.out.println("Migrate process failed: process "+mpid+" is suspended!");
				}
				else {
					migratableProcess p = pidToProcess.get(mpid);
					p.suspend();
					suspendProcess(mpid, p);
					decreaseLoad_ts(this.load);
					message newMsg=new message(msg.getSid(),mpid,p,"M");
					this.writeToMaster(newMsg);
					System.out.println("Migrate process "+mpid+" done!");
				}
				break;

			case "S":
				int spid=msg.getPid();
				if(!hasProcess(spid)) {
					System.out.println("Suspend process failed: there's no process running with this processID: "+spid);
				}
				else if(isSuspended(spid)) {
					System.out.println("Suspend process failed: process "+spid+" is suspended!");
				}
				else {
					migratableProcess p = pidToProcess.get(spid);
					p.suspend();
					suspendProcess(spid, p);
					decreaseLoad_ts(this.load);
					message newMsg=new message(msg.getSid(),spid,p,"S");
					this.writeToMaster(newMsg);
					System.out.println("Suspend process "+spid+" done!");
				}
				break;

			case "T":
				int tpid=msg.getPid();
				if(!hasProcess(tpid)) {
					System.out.println("Terminate process failed: there's no process running with this processID: "+tpid);
				}
				else{
					migratableProcess p = pidToProcess.get(tpid);
					p.terminate();
					if(!isSuspended(tpid)) {
						decreaseLoad_ts(this.load);
					}
					removeSuspended_ts(tpid);
					removePidToThread_ts(tpid);
					removeProcess_ts(tpid);
					this.writeToMaster(msg);
					System.out.println("Terminate process "+tpid+" done!");
				}

				break;

			default:
				System.err.println("Unrecognizable command from master!\n");
		}
	
	}

	/** 
     * Start a timer which do jobs of reaping dead process
     * <p>
     * In order to decide whether the process is dead or not, the function will check
     * if the thread responsible for running process is alive. If it is not alive, then
     * add this process into a list, and after checking all threads, the dead processes
     * will be removed. This delay to delete processes is to ensure the CoccurentModification
     * Exception won't happen 
     *
     * @since           1.0
     */	
	public void startReapTimer() {
		if(this.isRun) {
				//reap unalive theads
				Timer reapDeadThread = new Timer();
				reapDeadThread.scheduleAtFixedRate(new TimerTask(){

				@Override
				public void run() {
					// TODO Auto-generated method stub
					
					ArrayList<Integer> ToBeDelete = new ArrayList<Integer>();
					
					for (Map.Entry<Integer, Thread> entry : pidToThread.entrySet()) {
					    Integer key = entry.getKey();
					    Thread value = entry.getValue();
					    if (!value.isAlive()) {
					    	ToBeDelete.add(key);
					    }
					}
					
					for (Integer i : ToBeDelete) {
						removePidToThread_ts(i);
				    	removeProcess_ts(i);
						decreaseLoad_ts(load);
						message msg = new message(sid, i, null, "T");
						writeToMaster(msg);
						System.out.printf("Removed dead process %d!\n",i);
					}
					
				}}, 5000, 5000);			
			}
	
	}

	/** 
     * main function of the slave node, responsible of establishing socket connenction and I/O, 
     * reading message from the master, and forwarding the jobs to doJob() function.
     * 
     * @param argv      user specify hostname and port in arguments
     * @since           1.0
     */
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
			
			sn.startReapTimer();

			while(sn.isRun){

				message msg;
				try {
					Object o = sn.objIn.readObject();
					if(!(o instanceof message)) {
						continue;
					}
				
					msg = (message)o;
					switch(msg.getCommand()) {
						case "C":
							sn.sid = msg.getSid();
							System.out.println("Connected to master!");
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
					System.err.println("Disconnected from master!");
					System.exit(0);
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
