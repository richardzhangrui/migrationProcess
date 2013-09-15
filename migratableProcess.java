
import java.io.Serializable;
import java.lang.Runnable;

/**
 * MigratableProcess is the interface for process that can be migrated.
 * <p>
 * This interface mainly extends two other interfaces: java.lang.Runnable
 * and java.io.Serializable.
 * <p>
 * This interface will have some functions like suspend, resume, terminate,
 * toString, getInput and getOutput in addition to the functions of the 
 * interfaces it extends.
 * 
 * @author      Rui Zhang
 * @author      Jing Gao
 * @version     1.0, 09/15/2013
 * @since       1.0
 */
public interface migratableProcess extends Runnable, Serializable{
	
	/** 
     * suspend the current process
     * 
     * @since           1.0
     */
	void suspend();
	
	/** 
     * resume the current process
     * 
     * @since           1.0
     */
	void resume();
	
	/** 
     * terminate the current process
     * 
     * @since           1.0
     */
	void terminate();
	
	/** 
     * convert the process information to string
     * 
     * @return			returns the string converted
     * @since           1.0
     */
	String toString();
	
	/** 
     * get the process's transactionFileInputStream
     * 
     * @return			the input stream of the process
     * @see TransactionalFileInputStream
     * @since           1.0
     */
	TransactionalFileInputStream getInput();
	
	/** 
     * get the process's transactionFileOutputStream
     * 
     * @return			the output stream of the process
     * @see TransactionalFileOutputStream
     * @since           1.0
     */
	TransactionalFileOutputStream getOutput();
}
