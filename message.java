

import java.io.Serializable;

/**
 * Message is the carrier of information that transmitted between master and slaves. 
 * <p>
 * Message mainly contains four information:
 * <ul>
 * <li> slave id relative to this message
 * <li> process id relative to this message
 * <li> migrateableProcess that should be migrated
 * <li> command that used to identify the subsequent action
 * </ul>
 * <p>
 * This class can be serialized since it contains serializable objects
 * 
 * @author      Rui Zhang
 * @author      Jing Gao
 * @version     1.0, 09/15/2013
 * @since       1.0
 */
public class message implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = -657037575866053325L;
	
	private Integer sid;
	private Integer pid;
	private migratableProcess process;
	private String command;
	
	/** 
     * constructor of message class
     * 
     * @param sid		slave id included in the message
     * @param pid		process id included in the message
     * @param process	migratableProcess included in the message
     * @param command	command string included in the message	
     * @see migratableProcess
     * @since           1.0
     */
	public message(Integer sid, Integer pid, migratableProcess process, String command) {
		this.setSid(sid);
		this.setPid(pid);
		this.setProcess(process);
		this.setCommand(command);
	}
	
	/** 
     * get the process id of the message
     * 
     * @return 			return the process id of the message
     * @since           1.0
     */
	public Integer getPid() {
		return pid;
	}
	
	/** 
     * set the process id of the message
     * 
     * @param pid 		the process id included in the message
     * @since           1.0
     */
	public void setPid(Integer pid) {
		this.pid = pid;
	}
	
	/** 
     * get the migratable process of the message
     * 
     * @return  		return the migratableProcess included in the message
     * @since           1.0
     */
	public migratableProcess getProcess() {
		return process;
	}
	
	/** 
     * set the process of the message
     * 
     * @param process 	the process included in the message
     * @since           1.0
     */
	public void setProcess(migratableProcess process) {
		this.process = process;
	}
	
	/** 
     * get the command string of the message
     * 
     * @return  		return the command string included in the message
     * @since           1.0
     */
	public String getCommand() {
		return command;
	}
	
	/** 
     * set the command of the message
     * 
     * @param command 	the command string included in the message
     * @since           1.0
     */
	public void setCommand(String command) {
		this.command = command;
	}
	
	/** 
     * get the slave id of the message
     * 
     * @return  		return the slave id included in the message
     * @since           1.0
     */
	public Integer getSid() {
		return sid;
	}
	
	/** 
     * set the slave id of the message
     * 
     * @param sid 		the slave id included in the message
     * @since           1.0
     */
	public void setSid(Integer sid) {
		this.sid = sid;
	}
	
}
