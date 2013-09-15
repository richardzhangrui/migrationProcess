import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * ProcessManager is the main class for the master program in process migration. 
 * It creates the managerServer to accept slaves' connections and allows serviceForSlave
 * to use its corresponding methods to serve for the slaves. 
 * It is responsible for the following jobs:
 * <ul>
 * <li>Commands input and parse
 * <li>Create new process
 * <li>Migrate process
 * <li>Suspend process
 * <li>Resume process
 * <li>Terminate process
 * <li>Run process
 * <li>Reap process
 * <li>Keep slaves load balance
 * <li>Main the process and slave information
 * <li>Print corresponding process and slave information
 * </ul>
 * <p>
 * A lot of work including migrate, suspend, resume and so on is done through a 
 * message mechanism. If some slave is chosen to be do this work, the processManager
 * sends a corresponding message to this slave and update the process and slave 
 * information when having received the feedback.
 * <p>
 * The processManager can run process on its own if there's no other slaves connected 
 * to it. Under such circumstances, it works like a single machine system. Or if all 
 * the other slaves' loads are too heavy. The processManager can share the work. The 
 * initial load of processManager is 10 and every process will increase the load by 1.
 * ProcessManger will always choose the slave with smallest load to run the process.
 * In order to conveniently use the master to run process, we have distributed it a fake slave
 * id which is -1.
 * <p>
 * When the processManager is running, users can migrate processes manually or leave
 * the work to load balance part of the manager.
 * <p>
 * Every process running on this system has a unique process id and is distributed 
 * by the processManager. Similarly, every slave connecting to the master also has 
 * a unique slave id. 
 * 
 * @author      Rui Zhang
 * @author      Jing Gao
 * @version     1.0, 09/15/2013
 * @since       1.0
 */
public class processManager {
	public Integer sid;
	public Integer pid;
	
	private int load;
	private static final int ID = -1;
	
	private String path;
	
	public static final int DEFAULT_PORT = 15640;
	private int port;
	
	private Thread connectThread;
	

	private volatile LinkedList<Integer> suspendedProcess;
	private volatile HashMap<Integer, migratableProcess> pidToProcess;
	private volatile HashMap<Integer, Integer> pidToSlave;
	private volatile HashMap<Integer, serviceForSlave> sidToSlave;
	private volatile HashMap<Integer, Integer> sidToLoad;
	private volatile HashMap<Integer, LinkedList<Integer>> sidToPids;
	
	private volatile HashMap<Integer, Thread>  pidToThread; //currently run on this node's process and its corresponding threads
	
	/** 
     * Initiate the process manager with the corresponding port number
     *
     * @param port      the prot the processManager will run on
     * @since           1.0
     */
	public processManager(int port) {
		this.load = 10;
		this.sid = 0;
		this.pid = 0;
		this.port = port;
		this.path = "ruiz1+jinggao";
		suspendedProcess = new LinkedList<Integer>();
		pidToProcess = new HashMap<Integer, migratableProcess>();
		pidToSlave = new HashMap<Integer, Integer>();
		sidToSlave = new HashMap<Integer, serviceForSlave>();
		sidToLoad = new HashMap<Integer, Integer>();
		sidToLoad.put(ID,this.load);
		sidToPids = new HashMap<Integer, LinkedList<Integer>>();
		sidToPids.put(ID, new LinkedList<Integer>());
		
		pidToThread = new HashMap<Integer, Thread>();
	}
	
	/** 
     * Printe the suspended processes' information
     * <p>
     * the information printed format and order is "ProcessID ProcessName ProcessStatus SlaveID SlaveIP"
     * 
     * @since           1.0
     */
	private void printSuspended() {
		System.out.println("Suspended Processes:");
		System.out.println("ProcessID\tProcessName\tProcessStatus\tSlaveID\tSlaveIP");
		for(Integer spid : suspendedProcess) {
		    Integer slaveid = pidToSlave.get(spid);
		    String address;
		    if(slaveid.equals(ID)) {
			address = "localhost";
		    }
		    else {
			address = sidToSlave.get(slaveid).getAddress();
		    }
			System.out.printf("[%d]\t[%s]\tsuspended\t(%d)\t%s\n",spid, pidToProcess.get(spid).toString(),slaveid,address);
		}
	}
	
	/** 
     * Print all the processes' information
     * <p>
     * the information printed format and order is "ProcessID ProcessName ProcessStatus SlaveID SlaveIP"
     *
     * @since           1.0
     */
	private void printProcesses() {
		System.out.println("ProcessID\tProcessName\tProcessStatus\tSlaveID\tSlaveIP");
		for (Map.Entry<Integer, migratableProcess> entry : pidToProcess.entrySet()) {
		    Integer key = entry.getKey();
		    migratableProcess value = entry.getValue();
		    String status;
		    if(suspendedProcess.contains(key)) {
			status = "suspended";
		    }
		    else {
			status = "running";
		    }
		    Integer slaveid = pidToSlave.get(key);
		    String address;
		    if(slaveid.equals(ID)) {
			address = "localhost";
		    }
		    else {
			address = sidToSlave.get(slaveid).getAddress();
		    }
       		    System.out.printf("[%d]\t[%s]\t%s\t(%s)\t%s\n",key, value.toString(), status, slaveid, address);
		}
	}
	
	/** 
     * Print all the slaves' load information
     * <p>
     * The print format and order should be "SlaveID SlaveIP SlaveLoad".
     *
     * @since           1.0
     */
	private void printSlaveLoad() {
		System.out.println("SlaveID\tSlaveIP\tSlaveLoad");
       		System.out.printf("[%d]\t%s\t%d\n",ID, "localhost", sidToLoad.get(ID));
		for (Map.Entry<Integer, serviceForSlave> entry : sidToSlave.entrySet()) {
		    Integer key = entry.getKey();
		    serviceForSlave value = entry.getValue();
		    
       		    System.out.printf("[%d]\t%s\t%d\n",key, value.getAddress(), sidToLoad.get(key));
		}
		
	}
	
	/** 
     * Print the information of one slave with a specific slave id
     * <p>
     * The print format and order is "SlaveID SlaveIP SlaveLoad" and its processes information
     * followed
     *
     * @param csid      the slave id of the slave whose information will be output
     * @since           1.0
     */
	private void printSlaveInfo(Integer csid) {
		String address;
		if(csid.equals(ID)) {
			address = "localhost";
		}
		else {
			address = sidToSlave.get(csid).getAddress();
		}
		System.out.printf("SlaveId:%d\tSlaveIP:%s\tSlaveLoad:%d\n",csid, address, sidToLoad.get(csid));
		System.out.println("Processes running on this slave:");
		System.out.println("ProcessID\tProcessName\tProcessStatus");
		for (Integer cpid : sidToPids.get(csid)) {
		    migratableProcess value = pidToProcess.get(cpid);
		    String status;
		    if(suspendedProcess.contains(cpid)) {
			status = "suspended";
		    }
		    else {
			status = "running";
		    }
       		    System.out.printf("[%d]\t[%s]\t%s\n",cpid, value.toString(), status);
		}	
	}
	
	/** 
     * Get the current port number of the processManager
     *
     * @since           1.0
     */
	public int getPort() {
		return port;
	}
	
	/** 
     * Set the current port number of the processManager
     *
     * @param port      the port number going to be set
     * @since           1.0
     */
	public void setPort(int port) {
		this.port = port;
	}
	
	/** 
     * When a slave is connected to the master, it will be distributed a 
     * slave id and the master will record the information in its scope.
     * <p>
     * This method will update three hashmaps including sidToSlave,
     * sidToLoad, sidToPid and all these updates are under thread safe
     * mode
     * 
     *
     * @param service   the serviceForSlave object that serve the slave    
     * @return          <code>true</code> if the slave is added successfully
     *                  <code>false</code> otherwise.
     * @see             serviceForSlave
     * @since           1.0
     */
	public boolean addSlave_ts(serviceForSlave service) {
		if(service == null)
			return false;
		
		
		synchronized(sid) {
			addSidToSlave_ts(sid, service);
			addSidToLoad_ts(sid, 0);
			addSidToPids_ts(sid,new LinkedList<Integer>());
			service.setSid(sid);
			sid++;
		}
		
		return true;
	}
	
	/** 
     * When a slave is disconnected from the master, its information will 
     * be removed from the master
     * <p>
     * This method will update five hashmaps including pidToSlave, sidToSlave,
     * sidToLoad, sidToPid, pidToProcess and all these updates are under thread 
     * safe mode
     * 
     *
     * @param sid       the slave id of the slave that will be deleted   
     * @since           1.0
     */
	public void removeSlave_ts(Integer sid) {
		
		ArrayList<Integer> tobedelete = new ArrayList<Integer>();
		
		for(Map.Entry<Integer, Integer> entry : pidToSlave.entrySet()) {
			Integer key = entry.getKey();
			Integer value = entry.getValue();
			if(value.equals(sid)){
				tobedelete.add(key);
			}
		}
		
		for(Integer i : tobedelete) {
			removeProcess_ts(i);
			removePidToSlave_ts(i);
			removeSuspended_ts(i);
		}
		
		
		removeSidToPids_ts(sid);
		removeSidToLoad_ts(sid);
		removeSidToSlave_ts(sid);
	}
	
	/** 
     * remove the the entry with slave id "sid" from the sidToLoad hashmap 
     * thread safely
     * 
     * @param sid       the slave id of the slave that will be deleted   
     * @since           1.0
     */
	private void removeSidToLoad_ts(Integer sid) {
		synchronized(sidToLoad) {
			sidToLoad.remove(sid);
		}
	}
	
	/** 
     * remove the the entry with slave id "sid" from the sidToSlave hashmap 
     * thread safely
     * 
     * @param sid       the slave id of the slave that will be deleted   
     * @since           1.0
     */
	private void removeSidToSlave_ts(Integer sid) {
		synchronized(sidToSlave) {
			sidToSlave.remove(sid);
		}
	}
	
	/** 
     * add a the entry with slave id "sid" to the sidToLoad hashmap 
     * thread safely
     * 
     * @param sid       the slave id of the slave that will be added
     * @param pids		the list recording the process running on this slave   
     * @since           1.0
     */
	private void addSidToPids_ts(Integer sid, LinkedList<Integer> pids) {
	
		synchronized(sidToPids) {
			sidToPids.put(sid, pids);
		}
	}
	
	/** 
     * remove the the entry with slave id "sid" from the sidToPids hashmap 
     * thread safely
     * 
     * @param sid       the slave id of the slave that will be deleted   
     * @since           1.0
     */
	private void removeSidToPids_ts(Integer sid) {
		synchronized(sidToPids) {
			sidToPids.remove(sid);
		}
	}
	
	/** 
     * add a entry with process id "pid" to the process list of the specific slave id "sid" 
     * thread safely
     * 
     * @param sid       the slave id of the slave whose list will be updated
     * @param pid		the process id of the process that will be added to the list 
     * @since           1.0
     */
	private void addPidToSidList_ts(Integer sid, Integer pid) {
		synchronized(sidToPids.get(sid)) {
			sidToPids.get(sid).add(pid);
		}
	}
	
	
	/** 
     * remove the entry with process id "pid" from the process list of the specific slave id "sid" 
     * thread safely
     * 
     * @param sid       the slave id of the slave whose list will be updated
     * @param pid		the process id of the process that will be removed from the list 
     * @since           1.0
     */
	private void removePidFromSidList_ts(Integer sid, Integer pid) {
		synchronized(sidToPids.get(sid)) {
			sidToPids.get(sid).remove(pid);
		}
	}
	
	/** 
     * add a entry with slave id "sid" to the sidToSlave hashmap thread safely
     * 
     * @param sid       the slave id of the slave whose hashmap will be updated
     * @param service	the serviceForSlave which serves the slave with slave id sid 
     * @since           1.0
     */
	private void addSidToSlave_ts(Integer sid, serviceForSlave service) {
		synchronized(sidToSlave) {
			sidToSlave.put(sid, service);
		}
	}
	
	/** 
     * add a entry with slave id "sid" to the sidToLoad hashmap thread safely
     * 
     * @param sid       the slave id of the slave whose hashmap will be updated
     * @param load 		the slave's initial load
     * @since           1.0
     */
	private void addSidToLoad_ts(Integer sid, Integer load) {
		synchronized(sidToLoad) {
			sidToLoad.put(sid, load);
		}
	}
	
	/** 
     * add a entry with process id "pid" to the pidToSlave hashmap thread safely
     * 
     * @param pid       the process id of the process whose hashmap will be updated
     * @param sid 		the slave's slave id
     * @since           1.0
     */
	private void addPidToSlave_ts(Integer pid, Integer sid) {
		synchronized(pidToSlave) {
			pidToSlave.put(pid, sid);
		}
	}
	
	/** 
     * remove a entry with process id "pid" from the suspendedProcess list thread safely
     * 
     * @param pid       the process id of the process whose list will be updated
     * @since           1.0
     */
	private void removeSuspended_ts(Integer pid) {
		synchronized(suspendedProcess){
			suspendedProcess.remove(pid);
		}
		return;
	}
	
	/** 
     * remove a entry with process id "pid" from the pidToSlave hashmap thread safely
     * 
     * @param pid       the process id of the process whose hashmap will be updated
     * @since           1.0
     */
	private void removePidToSlave_ts(Integer pid) {
		synchronized(pidToSlave) {
			pidToSlave.remove(pid);
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
     * increase the load of slave with slave id "sid" by 1
     * 
     * @param sid       the slave id of the slave whose load will increase
     * @since           1.0
     */
	private void increaseLoad_ts(Integer sid) {
		addSidToLoad_ts(sid, sidToLoad.get(sid) + 1);
	}
	
	/** 
     * decrease the load of slave with slave id "sid" by 1
     * 
     * @param sid       the slave id of the slave whose load will decrease
     * @since           1.0
     */
	private void decreaseLoad_ts(Integer sid) {
		addSidToLoad_ts(sid, sidToLoad.get(sid) - 1);
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
     * Do corresponding job according to the message received
     * <p>
     * When receiving a message, the master should update the information it maintains 
     * and maybe forward the message to slaves.
     * <p>
     * The definition of messages are listed below:
     * <ul>
     * <li>E:resume process
     * <li>R:run process
     * <li>M:migrate process
     * <li>S:suspend process
     * <li>T:terminate process
     * </ul>
     * 
     *
     * @param msg       the message received
     * @param service   the serviceForSlave which serves for the corresponding slave
     * @see             message
     * @see             serviceForSlave
     * @since           1.0
     */
	public void job(message msg, serviceForSlave service) {
		if(service == null)
		{
			System.err.println("processManager: unrecognizable slaveAddr!\n");
			return;
		}
		switch(msg.getCommand()) {
			case "E":
				removeSuspended_ts(msg.getPid());
				increaseLoad_ts(msg.getSid());
				addPidToSlave_ts(msg.getPid(), msg.getSid());
				addPidToSidList_ts(msg.getSid(), msg.getPid());
				System.out.printf("Process %d resumed on Slave %d!\n",msg.getPid(),msg.getSid());
				break;
			case "R":
				addPidToSlave_ts(msg.getPid(), msg.getSid());
				addPidToSidList_ts(msg.getSid(),msg.getPid());
				increaseLoad_ts(msg.getSid());
				System.out.printf("Process %d ran on Slave %d!\n",msg.getPid(),msg.getSid());
				break;
			case "M":
				removePidFromSidList_ts(pidToSlave.get(msg.getPid()), msg.getPid());
				decreaseLoad_ts(pidToSlave.get(msg.getPid()));
				removePidToSlave_ts(msg.getPid());
				migratableProcess p = msg.getProcess(); 
				
				if(msg.getSid().equals(ID)) {
					
					if(p != null) {
						Thread t = new Thread(p);
						t.start();
						p.resume();

						addPidToThread_ts(msg.getPid(), t);
						addPidToSlave_ts(msg.getPid(), ID);
						addPidToSidList_ts(ID, msg.getPid());
						increaseLoad_ts(ID);
					}
					
				}
				else {
					
					p.getInput().setMigrated(true);
					p.getOutput().setMigrated(true);
					addSuspended_ts(msg.getPid());
					updateProcess_ts(msg.getPid(), p);
					msg.setCommand("E");
					sidToSlave.get(msg.getSid()).writeToClient(msg);
				}
				System.out.printf("Process %d migrated to Slave %d!\n",msg.getPid(),msg.getSid());
				break;
			case "S":
				suspendProcess(msg.getPid(), msg.getProcess());
				System.out.printf("Process %d suspended on Slave %d!\n",msg.getPid(),msg.getSid());
				break;
			case "T":
				removePidFromSidList_ts(msg.getSid(), msg.getPid());
				removePidToSlave_ts(msg.getPid());
				removeProcess_ts(msg.getPid());
				if(!isSuspended(msg.getPid()))
					decreaseLoad_ts(msg.getSid());
				removeSuspended_ts(msg.getPid());
				System.out.printf("Process %d terminated on Slave %d!\n",msg.getPid(),msg.getSid());
				break;
			default:
				System.err.println("processManager: unrecognizable command!\n");
		}
	
	}
	
	/** 
     * Choose the best slave which means the slave has the smallest load
     * <p>
     * If the master is the best slave, the function return -1
     *
     * @return          the slave id of the slave node whose node is smallest
     * @since           1.0
     */
	public int chooseBestSlave() {
		int min = ID;
		int minLoad = sidToLoad.get(min);
		synchronized(sidToLoad) {
			for (Map.Entry<Integer, Integer> entry : sidToLoad.entrySet()) {
			    int key = entry.getKey();
			    int value = entry.getValue();
			    if (value < minLoad) {
			    	min = key;
				minLoad = value;
			    }
			}
		}
		
		return min;
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
		decreaseLoad_ts(pidToSlave.get(spid));
		addSuspended_ts(spid);
	}
	
	/** 
     * Migrate the process with process id mpid from current slave with slave id csid to
     * one other slave with slave id msid. In addition, relative information will be 
     * updated in this function
     *
     * @param csid		the slave id of the slave where the process currently running 
     * @param msid		the slave id of the slave where the process will be move
     * @param mpid		the process id of the process which will be moved
     * @see	migratableProcess
     * @see message
     * @since           1.0
     */
	private void migrateProcess(Integer csid, Integer msid, Integer mpid) {
		if(csid.equals(ID)) {
			removePidFromSidList_ts(csid, mpid);
			migratableProcess p = pidToProcess.get(mpid);
			p.suspend();
			
			p.getInput().setMigrated(true);
			p.getOutput().setMigrated(true);
			suspendProcess(mpid, p);
			removePidToSlave_ts(mpid);
			message m = new message(msid, mpid, p, "E");
			sidToSlave.get(msid).writeToClient(m);
		}
		else {
			message msg = new message(msid, mpid, null, "M");
			sidToSlave.get(csid).writeToClient(msg);
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
	private void startReapTimer() {
		//reap unalive theads
		Timer reapDeadThread = new Timer();
		reapDeadThread.scheduleAtFixedRate(new TimerTask(){

			@Override
			public void run() {
				
				ArrayList<Integer> toBeDelete = new ArrayList<Integer>();
				// TODO Auto-generated method stub
				for (Map.Entry<Integer, Thread> entry : pidToThread.entrySet()) {
				    Integer key = entry.getKey();
				    Thread value = entry.getValue();
				    if (!value.isAlive()) {
				    	toBeDelete.add(key);
				    }
				}
				
				for(Integer i : toBeDelete) {
					removePidToThread_ts(i);
			    	removeProcess_ts(i);
					Integer rsid = pidToSlave.get(i);
					removePidFromSidList_ts(rsid,i);
					decreaseLoad_ts(rsid);
			    	removePidToSlave_ts(i);
			    	System.out.printf("Removed dead process %d!",i);
				}
				
			}}, 5000, 5000);		
	}
	
	/** 
     * Start a timer which do load balance jobs
     * <p>
     * The function will first compute the average load of the whole system and if some slave's
     * load is greater than the average, the function will serially pick one process on this
     * slave to migrate to the optimal slave until the load is below the average or the best slave
     * becomes itself or all the processes have been migrated. Every time one process has been 
     * migrated, the function will sleep for 1s in order to wait for feedbacks from slaves to 
     * update corresponding information. By doing this, it ensures the possible migration back
     * and forth.
     *
     * @since           1.0
     */
	private void startBalanceTimer() {
		Timer balanceSlave = new Timer();
		balanceSlave.scheduleAtFixedRate(new TimerTask(){

			@Override
			public void run() {
				// TODO Auto-generated method stub
				int loads = 0;
				for (Map.Entry<Integer, Integer> entry : sidToLoad.entrySet()) {
				    Integer key = entry.getKey();
				    Integer value = entry.getValue();
				    if(key.equals(ID))
					loads += (value - 10);
				    else
				    	loads += value;
				}
				
				loads /= sidToLoad.size();
				loads++;
				
				for(Map.Entry<Integer, Integer> entry : sidToLoad.entrySet()) {
		       		    int key = entry.getKey();
				    int value = entry.getValue();
				    int bestsid = -2;
				    int index = 0;
				    while(value > loads && bestsid != key && index < sidToPids.get(key).size()) {
				    	bestsid = chooseBestSlave();
				    	
				    	if(bestsid != key) {
				    		Integer cpid = sidToPids.get(key).get(index);
				    		if(!isSuspended(cpid)) {
				    			migrateProcess(key,bestsid,cpid);
				    			System.out.printf("Migrate process %d from Slave %d to Slave %d\n", cpid, key, bestsid);
				    			try {
									Thread.sleep(1000);
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
				    		}
						index++;
				    	}
				    	
				    }
				}
				
			}}, 7000, 7000);	
	}

	/** 
     * This is the start function of processManager. It initiates the timers, listening for 
     * coming slaves, command input and parse and so on.
     * <p>
     * The function supports the commands below:
     * <ul>
     * <li>quit: quit the program
     * <li>ps: 
     * 		<ul>
     * 		<li>"-sp":print the suspended processes' information
     * 		<li>"-p":print all the processes' information
     * 		<li>"-sl":print the slaves' load information
     * 		<li>"-s <sid>":print the corresponding slave's information
     * 		</ul>
     * <li>suspend:suspend a process
     * <li>resume:resume a process
     * <li>migrate:migrate a process
     * <li>terminate:terminate a process
     * <li><a migratableProcess's name><corresponding params>: run a specific migratable process
     * </ul>
     *
     * @since           1.0
     */
	private void start() {
		managerServer ms = null;
		try {
			 ms = new managerServer(this);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.err.println("processManager: IOException while creating the manager server!\n");
			return;
		}
		
		this.startReapTimer();
		
		this.startBalanceTimer();
		
		
		//listen and accept slaves
		connectThread = new Thread(ms);
		connectThread.start();
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		
		while (true) {
			System.out.print(path + ":$ ");
			String input;
			String args[];
			try {
				input = reader.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.err.println("processManager: Read input error while creating the manager server!\n");
				e.printStackTrace();
				continue;
			}
			
			args = input.split(" ");
			
			if(args[0].equals("quit"))
				System.exit(0);
			else if (args[0].equals("ps")) {
				if(args.length == 1) {
					System.out.println("Usage: ps [-option]");
					System.out.println("-p: print processes information");
					System.out.println("-sp: print suspended processes information");
					System.out.println("-sl: print slaves loads information");
					System.out.println("-s [slaveID]: print slave with slaveID's information");
					continue;
				}	
				if(args.length >= 2) {
					switch(args[1]) {
						case "-p":
							printProcesses();	
							break;
						case "-sp":
							printSuspended();	
							break;
						case "-sl":
							printSlaveLoad();	
							break;
						case "-s":
							if(args.length == 2) {
								System.out.println("Please indicate the slaveID!");
								continue;
							}
							Integer slaveid = Integer.parseInt(args[2]);
							if(!hasSlave(slaveid)) {
								System.out.printf("There's no Slave with slaveID %d\n",slaveid);
							}
							else {
								printSlaveInfo(slaveid);	
							}
							break;
						default:
							System.out.println("Unrecognizable option!");
							break;
					}
				}
			}
			else if (args[0].equals("migrate")) {
				int msid;
				int mpid;
				if (args.length == 1) {
					System.out.println("migrate usage: migrate processID [slaveID]\n");
					continue;
				}
				else if (args.length == 2){
					mpid = Integer.parseInt(args[1]);
					msid = chooseBestSlave();
				}
				else {
					mpid = Integer.parseInt(args[1]);
					msid = Integer.parseInt(args[2]);
				}
				
				if(!hasProcess(mpid)) {
					System.out.println("There's no process running with this processID!");
					continue;
				}
				else if(isSuspended(mpid)) {
					System.out.println("This process is suspended!");
					continue;
				}
				else if(pidToSlave.get(mpid).equals(msid)) {
					System.out.println("This process is running on this slave!");
					continue;
				}
				else {
					if(!hasSlave(msid)) {
						System.out.println("There's no slave with this slaveID!");
						continue;
					}
					Integer csid = pidToSlave.get(mpid);
					migrateProcess(csid, msid, mpid);
					System.out.printf("Process %d migrated from Slave %d!\n",mpid,csid);
				}
			}
			else if (args[0].equals("suspend")){
				int spid;
				if (args.length == 1) {
					System.out.println("suspend usage: suspend processID");
					continue;
				}
				else {
					
					spid = Integer.parseInt(args[1]);
					
					if(!hasProcess(spid)) {
						System.out.println("There's no process running with this pid!");
						continue;
					}
					else if (isSuspended(spid)) {
						System.out.println("This process has already been suspended!");
						continue;
					}
					
					else {
						Integer ssid = pidToSlave.get(spid);
						if(ssid.equals(new Integer(ID))) {
							migratableProcess p = pidToProcess.get(spid);
							p.suspend();
							suspendProcess(spid, p);
						}
						else {
							message msg = new message(ssid, spid, null, "S");
							sidToSlave.get(ssid).writeToClient(msg);
						}
					}
				}
			}
			else if (args[0].equals("terminate")){
				int tpid;
				if(args.length == 1) {
					System.out.println("terminate usage: terminate processID");
					continue;
				}
				else {
					tpid = Integer.parseInt(args[1]);
					if(!hasProcess(tpid)) {
						System.out.println("There's no process running with this pid!");
						continue;
					}
					else {
						
						Integer tsid = pidToSlave.get(tpid);
						if(tsid.equals(new Integer(ID))) {
							migratableProcess p = pidToProcess.get(tpid);
							p.terminate();
							if(!isSuspended(tpid)) {
								decreaseLoad_ts(new Integer(ID));	
							}
							removePidToSlave_ts(tpid);
							removePidFromSidList_ts(tsid, tpid);
							removePidToThread_ts(tpid);
							removeProcess_ts(tpid);
							removeSuspended_ts(tpid);
						}
						else {
							message msg = new message(tsid, tpid, null, "T");
							sidToSlave.get(tsid).writeToClient(msg);
						}
						
					}
				}
			}
			else if (args[0].equals("resume")) {
				int rpid;
				if(args.length == 1) {
					System.out.println("resume usage: resume processID");
					continue;
				}
				else {
					rpid = Integer.parseInt(args[1]);
					if(!hasProcess(rpid)) {
						System.out.println("There's no process running with this pid!");
						continue;
					}
					else if(!isSuspended(rpid)) {
						System.out.println("This process is not suspended!");
						continue;
					}
					else {
						Integer rsid = pidToSlave.get(rpid);
						migratableProcess p = pidToProcess.get(rpid);
						if(rsid.equals(ID)) { 
							if(p != null) {
								p.resume();
							}
							//remove the process from the suspendedProcess list
							removeSuspended_ts(rpid);
							increaseLoad_ts(ID);
						}
						else {
							message msg = new message(rsid, rpid, pidToProcess.get(rpid), "E");
							sidToSlave.get(rsid).writeToClient(msg);
						}
					}
				}			
			}
			else  {
			
				migratableProcess newProcess;
				try {
					
					Class<migratableProcess> processClass = (Class<migratableProcess>)(Class.forName(args[0]));
					Constructor<migratableProcess> processConstructor = processClass.getConstructor(String[].class);
	                Object[] obj = new Object[1];
	                obj[0] = (Object[])args;
					newProcess = processConstructor.newInstance(obj);

	            } catch (ClassNotFoundException e) {
					System.out.println("Run: Could not find class or no such command" + args[0]);
					continue;
				} catch (SecurityException e) {
					System.out.println("Run: Security Exception getting constructor for " + args[0]);
					continue;
				} catch (NoSuchMethodException e) {
					System.out.println("Run: Could not find proper constructor for " + args[0]);
					continue;
				} catch (IllegalArgumentException e) {
					System.out.println("Run: Illegal arguments for " + args[0]);
					continue;
				} catch (InstantiationException e) {
					System.out.println("Run: Instantiation Exception for " + args[0]);
					continue;
				} catch (IllegalAccessException e) {
					System.out.println("Run: IIlegal access exception for " + args[0]);
					continue;
				} catch (InvocationTargetException e) {
					System.out.println("Run: Invocation target exception for " + args[0]);
					continue;
				}
				
				Integer sid = chooseBestSlave();
				addProcess_ts(this.pid, newProcess);
				if(sid.equals(ID)) {
					if(newProcess != null) {
						Thread t = new Thread(newProcess);
						t.start();
						addPidToThread_ts(this.pid, t);
						addPidToSlave_ts(this.pid, ID);
						addPidToSidList_ts(ID, this.pid);
						increaseLoad_ts(ID);
					}
				}
				else {
					newProcess.getInput().setMigrated(true);
					newProcess.getOutput().setMigrated(true);
					message msg = new message(sid, this.pid, newProcess, "R");
					sidToSlave.get(sid).writeToClient(msg);
				}
				
				this.pid++;
			}
			
		}
		
		
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
     * check whether the slave with slave id sid exist
     *
     * @param pid       the slave id of the slave that will be checked
     * @since           1.0
     */
	private boolean hasSlave(int sid) {
		return (sidToSlave.containsKey(new Integer(sid)) || sid == ID);
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		processManager pm;
		
		if(args.length >= 2) {
			pm = new processManager(Integer.parseInt(args[1]));
		}
		else {
			pm = new processManager(DEFAULT_PORT);
		}
		
		pm.start();

	}
}
