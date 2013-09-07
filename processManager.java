package mainfolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;



public class processManager {
	public Integer sid;
	public Integer pid;
	
	private int load;
	private static final int ID = -1;
	
	private String path;
	
	public static final int DEFAULT_PORT = 15640;
	private int port;
	
	private Thread connectThread;
	
	private LinkedList<Thread> threads;
	private LinkedList<Integer> suspendedProcess;
	//private LinkedList<serviceForSlave> slaves;
	private HashMap<Integer, migratableProcess> pidToProcess;
	private HashMap<Integer, Integer> pidToSlave;
	private HashMap<Integer, serviceForSlave> sidToSlave;
	private HashMap<Integer, Integer> sidToLoad;
	private HashMap<Integer, Thread>  pidToThread; //currently run on this node's process and its corresponding threads
	
	public processManager(int port) {
		this.load = 10;
		this.sid = 0;
		this.pid = 0;
		this.port = port;
		threads = new LinkedList<Thread>();
		suspendedProcess = new LinkedList<Integer>();
		//slaves = new LinkedList<serviceForSlave>();
		pidToProcess = new HashMap<Integer, migratableProcess>();
		pidToSlave = new HashMap<Integer, Integer>();
		sidToSlave = new HashMap<Integer, serviceForSlave>();
		sidToLoad = new HashMap<Integer, Integer>();
		sidToLoad.put(-1,this.load);
		pidToThread = new HashMap<Integer, Thread>();
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
			synchronized(sidToSlave) {
				sidToSlave.put(sid, service);
			}
			synchronized(sidToLoad) {
				sidToLoad.put(sid, 0);
			}
			service.setSid(sid);
			sid++;
		}
		
		return true;
	}
	
	public void job(message msg, serviceForSlave service) {
		if(service == null)
		{
			System.err.println("processManager: unrecognizable slaveAddr!\n");
			return;
		}
		switch(msg.getCommand()) {
			case "R":
				//slaveHost tmpslave = new slaveHost();
				//tmpslave.setAddr(slaveAddr);
				synchronized(pidToSlave) {
					pidToSlave.put(msg.getPid(), service.getSid());
				}
				synchronized(suspendedProcess) {
					//remove from suspendedProcess list
					removeSuspended(msg.getPid());
				}
				break;
			case "FR":
				break;
			case "M":
				synchronized(pidToSlave) {
					pidToSlave.remove(msg.getPid());
				}
				synchronized(suspendedProcess) {
					suspendedProcess.add(msg.getPid());
				}
				
				if(msg.getSid().equals(-1)) {
					migratableProcess p = msg.getProcess(); 
					if(p != null) {
						Thread t = new Thread(p);
						t.start();
						synchronized(pidToThread) {
							pidToThread.put(msg.getPid(),t);
						}
						synchronized(pidToSlave) {
							pidToSlave.put(msg.getPid(), msg.getSid());
						}
					}
					
					//remove the process from the suspendedProcess list
				}
				else {
					msg.setCommand("R");
					sidToSlave.get(msg.getSid()).writeToClient(msg);
				}
				break;
			case "S":
				synchronized(pidToSlave) {
					pidToSlave.remove(msg.getPid());
				}
				synchronized(suspendedProcess) {
					suspendedProcess.add(msg.getPid());
				}
				break;
			case "T":
				synchronized(pidToSlave) {
					pidToSlave.remove(msg.getPid());
				}
				synchronized(pidToProcess) {
					pidToProcess.remove(msg.getPid());
				}
				break;
			default:
				System.err.println("processManager: unrecognizable command!\n");
		}
	
	}

	public int chooseBestSlave() {
		int min = -1;
		int minLoad = sidToLoad.get(min);
		synchronized(sidToLoad) {
			for (Map.Entry<Integer, Integer> entry : sidToLoad.entrySet()) {
			    Integer key = entry.getKey();
			    Integer value = entry.getValue();
			    if (value < minLoad) {
			    	min = key;
			    }
			}
		}
		
		return min;
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
				break;
			else if (args[0].equals("ps")) {
				
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
				
				if(pidToSlave.get(pid).equals(msid)) {
					continue;
				}
				else {
					Integer csid = pidToSlave.get(mpid);
					if(csid.equals(-1)) {
						migratableProcess p = pidToProcess.get(mpid);
						p.suspend();
						pidToThread.remove(mpid);
						pidToSlave.remove(mpid);
						message m = new message(msid, mpid, p, "R");
						sidToSlave.get(msid).writeToClient(m);
					}
					else {
						message msg = new message(msid, mpid, null, "M");
						sidToSlave.get(csid).writeToClient(msg);
					}
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
					Integer csid = pidToSlave.get(spid);
					message msg = new message(csid, spid, null, "S");
					sidToSlave.get(csid).writeToClient(msg);
				}
			}
			else if (args[0].equals("terminate")){
				int spid;
				if(args.length == 1) {
					System.out.println("terminate usage: terminate processID");
					continue;
				}
				else {
					spid = Integer.parseInt(args[1]);
					Integer csid = pidToSlave.get(spid);
					message msg = new message(csid, spid, null, "T");
					sidToSlave.get(csid).writeToClient(msg);
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
					if(!isSuspended(rpid)) {
						System.out.println("This process is not suspended!");
						continue;
					}
					Integer csid = chooseBestSlave();
					migratableProcess p = pidToProcess.get(rpid);
					if(csid.equals(-1)) { 
						if(p != null) {
							Thread t = new Thread(p);
							t.start();
							pidToThread.put(rpid,t);
						}
						//remove the process from the suspendedProcess list
						synchronized(suspendedProcess) {
							removeSuspended(rpid);
						}
					}
					else {
						message msg = new message(-1, rpid, pidToProcess.get(rpid), "R");
						sidToSlave.get(csid).writeToClient(msg);
					}
				}			
			}
			else if (args[0].equals("run")) {
				if(args.length == 1) {
					System.out.println("run usage: run processName");
					continue;
				}
				migratableProcess newProcess;
				try {
					
					Class<migratableProcess> processClass = (Class<migratableProcess>)(Class.forName(args[0]));
					Constructor<migratableProcess> processConstructor = processClass.getConstructor(String[].class);
	                Object[] obj = new Object[1];
	                obj[0] = (Object[])args;
					newProcess = processConstructor.newInstance(obj);

	            } catch (ClassNotFoundException e) {
					//Couldn't link find that class. stupid user.
					System.out.println("Run: Could not find class " + args[1]);
					continue;
				} catch (SecurityException e) {
					System.out.println("Run: Security Exception getting constructor for " + args[1]);
					continue;
				} catch (NoSuchMethodException e) {
					System.out.println("Run: Could not find proper constructor for " + args[1]);
					continue;
				} catch (IllegalArgumentException e) {
					System.out.println("Run: Illegal arguments for " + args[1]);
					continue;
				} catch (InstantiationException e) {
					System.out.println("Run: Instantiation Exception for " + args[1]);
					continue;
				} catch (IllegalAccessException e) {
					System.out.println("Run: IIlegal access exception for " + args[1]);
					continue;
				} catch (InvocationTargetException e) {
					System.out.println("Run: Invocation target exception for " + args[1]);
					continue;
				}
				
				Integer sid = chooseBestSlave();
				pidToProcess.put(this.pid, newProcess);
				if(sid.equals(-1)) {
					if(newProcess != null) {
						Thread t = new Thread(newProcess);
						t.start();
						pidToThread.put(this.pid,t);
						pidToSlave.put(this.pid, sid);
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
		for(Integer id : suspendedProcess) {
			if(id.equals(pid)) {
				return true;
			}
		}
		return false;
	}
	
	private void removeSuspended(Integer pid) {
		for(int i = 0; i < suspendedProcess.size(); i++) {
			if(pid.equals(suspendedProcess.get(i))) {
				suspendedProcess.remove(i);
				return;
			}
		}
		return;
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
