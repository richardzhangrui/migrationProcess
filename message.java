package mainfolder;

import java.io.Serializable;

public class message implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = -657037575866053325L;
	
	private Integer sid;
	private Integer pid;
	private migratableProcess process;
	private String command;
	
	public message(Integer sid, Integer pid, migratableProcess process, String command) {
		this.setSid(sid);
		this.setPid(pid);
		this.setProcess(process);
		this.setCommand(command);
	}

	public Integer getPid() {
		return pid;
	}

	public void setPid(Integer pid) {
		this.pid = pid;
	}

	public migratableProcess getProcess() {
		return process;
	}

	public void setProcess(migratableProcess process) {
		this.process = process;
	}

	public String getCommand() {
		return command;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public Integer getSid() {
		return sid;
	}

	public void setSid(Integer sid) {
		this.sid = sid;
	}
	
	
	
}
