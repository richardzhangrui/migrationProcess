

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;



public class processManager {
	public Integer sid;
	public Integer pid;
	
	private int load;
	private static final int ID = -1;
	
	private String path;
	
	public static final int DEFAULT_PORT = 15640;
	private int port;
	
	private Thread connectThread;
	
	//private LinkedList<Thread> threads;
	private volatile LinkedList<Integer> suspendedProcess;
	//private LinkedList<Integer> terminatedProcess;
	//private LinkedList<serviceForSlave> slaves;
	private volatile HashMap<Integer, migratableProcess> pidToProcess;
	private volatile HashMap<Integer, Integer> pidToSlave;
	private volatile HashMap<Integer, serviceForSlave> sidToSlave;
	private volatile HashMap<Integer, Integer> sidToLoad;
	private volatile HashMap<Integer, LinkedList<Integer>> sidToPids;
	
	private volatile HashMap<Integer, Thread>  pidToThread; //currently run on this node's process and its corresponding threads
	
	public processManager(int port) {
		this.load = 10;
		this.sid = 0;
		this.pid = 0;
		this.port = port;
		//threads = new LinkedList<Thread>();
		suspendedProcess = new LinkedList<Integer>();
		//slaves = new LinkedList<serviceForSlave>();
		//terminatedProcess = new LinkedList<Integer>();
		pidToProcess = new HashMap<Integer, migratableProcess>();
		pidToSlave = new HashMap<Integer, Integer>();
		sidToSlave = new HashMap<Integer, serviceForSlave>();
		sidToLoad = new HashMap<Integer, Integer>();
		sidToLoad.put(ID,this.load);
		sidToPids = new HashMap<Integer, LinkedList<Integer>>();
		sidToPids.put(ID, new LinkedList<Integer>());
		
		pidToThread = new HashMap<Integer, Thread>();
	}
	
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

	private void printSlaveLoad() {
		System.out.println("SlaveID\tSlaveIP\tSlaveLoad");
       		System.out.printf("[%d]\t%s\t%d\n",this.ID, "localhost", sidToLoad.get(ID));
		for (Map.Entry<Integer, serviceForSlave> entry : sidToSlave.entrySet()) {
		    Integer key = entry.getKey();
		    serviceForSlave value = entry.getValue();
		    
       		    System.out.printf("[%d]\t%s\t%d\n",key, value.getAddress(), sidToLoad.get(key));
		}
		
	}

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
	
	public int getPort() {
		// TODO Auto-generated method stub
		return port;
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	public boolean addSlave(serviceForSlave service) {
		if(service == null)
			return false;
		
//		slaveHost tmpslave = new slaveHost();
//		tmpslave.setAddr(service);
		
		//synchronized(slaves) {
		//	slaves.add(service);
		//}
		
		synchronized(sid) {
//			synchronized(sidToSlave) {
//				sidToSlave.put(sid, service);
//			}
			addSidToSlave_ts(sid, service);
//			synchronized(sidToLoad) {
//				sidToLoad.put(sid, 0);
//			}
			addSidToLoad_ts(sid, 0);
			addSidToPids_ts(sid,new LinkedList<Integer>());
			service.setSid(sid);
			sid++;
		}
		
		return true;
	}
	
	private void addSidToPids_ts(Integer sid, LinkedList<Integer> pids) {
		synchronized(sidToPids) {
			sidToPids.put(sid, pids);
		}
	}
	
	private void removeSidToPids_ts(Integer sid) {
		synchronized(sidToPids) {
			sidToPids.remove(sid);
		}
	}
	
	private void addPidToSidList_ts(Integer sid, Integer pid) {
		synchronized(sidToPids.get(sid)) {
			sidToPids.get(sid).add(pid);
		}
	}
	
	private void removePidFromSidList_ts(Integer sid, Integer pid) {
		synchronized(sidToPids.get(sid)) {
			sidToPids.get(sid).remove(pid);
		}
	}
	
	private void addSidToSlave_ts(Integer sid, serviceForSlave service) {
		synchronized(sidToSlave) {
			sidToSlave.put(sid, service);
		}
	}
	
	private void addSidToLoad_ts(Integer sid, Integer load) {
		synchronized(sidToLoad) {
			sidToLoad.put(sid, load);
		}
	}
	
	private void addPidToSlave_ts(Integer pid, Integer sid) {
		synchronized(pidToSlave) {
			pidToSlave.put(pid, sid);
		}
	}
	
	private void removeSuspended_ts(Integer pid) {
		synchronized(suspendedProcess){
			//for(int i = 0; i < suspendedProcess.size(); i++) {
			//	if(pid.equals(suspendedProcess.get(i))) {
			//		suspendedProcess.remove(i);
			//		break;
			//	}
			//}
			suspendedProcess.remove(pid);
		}
		return;
	}
	
	private void removePidToSlave_ts(Integer pid) {
		synchronized(pidToSlave) {
			pidToSlave.remove(pid);
		}
	}
	
	private void addSuspended_ts(Integer pid) {
		synchronized(suspendedProcess) {
			suspendedProcess.add(pid);
		}
	}
	
	private void increaseLoad_ts(Integer sid) {
		addSidToLoad_ts(sid, sidToLoad.get(sid) + 1);
	}
	
	private void decreaseLoad_ts(Integer sid) {
		addSidToLoad_ts(sid, sidToLoad.get(sid) - 1);
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
				//slaveHost tmpslave = new slaveHost();
				//tmpslave.setAddr(slaveAddr);
//				synchronized(pidToSlave) {
//					pidToSlave.put(msg.getPid(), service.getSid());
//				}
				addPidToSlave_ts(msg.getPid(), msg.getSid());
				addPidToSidList_ts(msg.getSid(),msg.getPid());
//				synchronized(suspendedProcess) {
//					//remove from suspendedProcess list
//					removeSuspended(msg.getPid());
//				}
				//removeSuspended_ts(msg.getPid());
				increaseLoad_ts(msg.getSid());
				System.out.printf("Process %d ran on Slave %d!\n",msg.getPid(),msg.getSid());
				break;
//			case "FR":
//				break;
			case "M":
				removePidFromSidList_ts(pidToSlave.get(msg.getPid()), msg.getPid());
				decreaseLoad_ts(pidToSlave.get(msg.getPid()));
				removePidToSlave_ts(msg.getPid());
				migratableProcess p = msg.getProcess(); 
				
//				synchronized(pidToSlave) {
//					pidToSlave.remove(msg.getPid());
//				}
//				synchronized(suspendedProcess) {
//					suspendedProcess.add(msg.getPid());
//				}
				
				if(msg.getSid().equals(ID)) {
					
					if(p != null) {
						Thread t = new Thread(p);
						t.start();
						p.resume();
//						synchronized(pidToThread) {
//							pidToThread.put(msg.getPid(),t);
//						}
//						synchronized(pidToSlave) {
//						pidToSlave.put(msg.getPid(), ID);
//					}

						addPidToThread_ts(msg.getPid(), t);
						addPidToSlave_ts(msg.getPid(), ID);
						addPidToSidList_ts(ID, msg.getPid());
						increaseLoad_ts(ID);
					}
					
					//remove the process from the suspendedProcess list
				}
				else {
					addSuspended_ts(msg.getPid());
					updateProcess_ts(msg.getPid(), p);
					msg.setCommand("E");
					sidToSlave.get(msg.getSid()).writeToClient(msg);
				}
				System.out.printf("Process %d migrated to Slave %d!\n",msg.getPid(),msg.getSid());
				break;
			case "S":
//				synchronized(pidToSlave) {
//				pidToSlave.remove(msg.getPid());
//			}
//			synchronized(suspendedProcess) {
//				suspendedProcess.add(msg.getPid());
//			}
				suspendProcess(msg.getPid(), msg.getProcess());
				//removePidToSlave_ts(msg.getPid());
				//addSuspended_ts(msg.getPid());
				//decreaseLoad_ts(msg.getSid());
				System.out.printf("Process %d suspended on Slave %d!\n",msg.getPid(),msg.getSid());
				break;
			case "T":
				removePidFromSidList_ts(msg.getSid(), msg.getPid());
				removePidToSlave_ts(msg.getPid());
				removeProcess_ts(msg.getPid());
				if(!isSuspended(msg.getPid()))
					decreaseLoad_ts(msg.getSid());
				removeSuspended_ts(msg.getPid());
				//service.stopService();
//				synchronized(pidToSlave) {
//					pidToSlave.remove(msg.getPid());
//				}
//				synchronized(pidToProcess) {
//					pidToProcess.remove(msg.getPid());
//				}
				System.out.printf("Process %d terminated on Slave %d!\n",msg.getPid(),msg.getSid());
				break;
			default:
				System.err.println("processManager: unrecognizable command!\n");
		}
	
	}

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
	
	private void suspendProcess(Integer spid, migratableProcess p) {
		updateProcess_ts(spid, p);
		decreaseLoad_ts(pidToSlave.get(spid));
		//removePidToSlave_ts(spid);
		//removePidToThread_ts(spid);
		addSuspended_ts(spid);
	}
	
	private void migrateProcess(Integer csid, Integer msid, Integer mpid) {
		if(csid.equals(ID)) {
			removePidFromSidList_ts(csid, mpid);
			migratableProcess p = pidToProcess.get(mpid);
			p.suspend();
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
	
	private void startReapTimer() {
		//reap unalive theads
		Timer reapDeadThread = new Timer();
		reapDeadThread.scheduleAtFixedRate(new TimerTask(){

			@Override
			public void run() {
				// TODO Auto-generated method stub
				for (Map.Entry<Integer, Thread> entry : pidToThread.entrySet()) {
				    Integer key = entry.getKey();
				    Thread value = entry.getValue();
				    if (!value.isAlive()) {
				    	removePidToThread_ts(key);
				    	removeProcess_ts(key);
					Integer rsid = pidToSlave.get(key);
					removePidFromSidList_ts(rsid,key);
					decreaseLoad_ts(rsid);
				    	removePidToSlave_ts(key);
					System.out.printf("Removed dead process %d!",key);
				    }
				}
			}}, 5000, 5000);		
	}
	

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
				    		}
						index++;
				    	}
				    	
				    }
				}
				
			}}, 7000, 7000);	
	}

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
//					if(csid.equals(ID)) {
//						migratableProcess p = pidToProcess.get(mpid);
//						p.suspend();
//						removePidToSlave_ts(mpid);
//						suspendProcess(mpid, p);
//						message m = new message(msid, mpid, p, "E");
//						sidToSlave.get(msid).writeToClient(m);
//					}
//					else {
//						message msg = new message(msid, mpid, null, "M");
//						sidToSlave.get(csid).writeToClient(msg);
//					}
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
//							updateProcess_ts(spid, p);
//							removePidToSlave_ts(spid);
//							//removePidToThread_ts(spid);
//							addSuspended_ts(spid);
//							decreaseLoad_ts(new Integer(ID));
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
//								pidToThread.put(rpid,t);
							}
							//remove the process from the suspendedProcess list
							removeSuspended_ts(rpid);
							increaseLoad_ts(ID);
							//synchronized(suspendedProcess) {
//								removeSuspended(rpid);
//							}
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
					//Couldn't link find that class. stupid user.
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
				//pidToProcess.put(this.pid, newProcess);
				if(sid.equals(ID)) {
					if(newProcess != null) {
						Thread t = new Thread(newProcess);
						t.start();
						addPidToThread_ts(this.pid, t);
						addPidToSlave_ts(this.pid, ID);
						addPidToSidList_ts(ID, this.pid);
						increaseLoad_ts(ID);
						//pidToThread.put(this.pid,t);
						//pidToSlave.put(this.pid, sid);
					}
				}
				else {
					message msg = new message(sid, this.pid, newProcess, "R");
					sidToSlave.get(sid).writeToClient(msg);
				}
				
				this.pid++;
			}
			
		}
		
		
	}
	
	private boolean isSuspended(int pid) {
		return suspendedProcess.contains(new Integer(pid));
	}
	
	private boolean hasProcess(int pid) {
		return pidToProcess.containsKey(new Integer(pid));
	}
	
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
