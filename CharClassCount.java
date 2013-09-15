import java.io.PrintStream;
import java.io.EOFException;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.Thread;
import java.lang.InterruptedException;

/**
 * CharClassCount is a migratable class that read all the characters from a each line
 * in a file, and count the numbers of consonants, vowels, digits, spaces and punctuations
 * of each line, and print the result to the output file.
 * <p>
 * This class implements the migratableProcess interface
 * 
 * @author      Rui Zhang
 * @author      Jing Gao
 * @version     1.0, 09/15/2013
 * @since       1.0
 */
public class CharClassCount implements migratableProcess
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 8473402089878761238L;
	private TransactionalFileInputStream  inFile;
	private TransactionalFileOutputStream outFile;

	private volatile boolean suspending;
	private volatile boolean isTerminate;

	/** 
     * constructor of CharClassCount class
     * 
     * @param args		arguments
     * @since           1.0
     */
	public CharClassCount(String args[]) throws Exception
	{
		suspending = false;
		isTerminate = false;
		if (args.length != 3) {
			System.out.println("usage: CharClassCount  <inputFile> <outputFile>");
			throw new Exception("Invalid Arguments");
		}
		
		inFile = new TransactionalFileInputStream(args[1]);
		outFile = new TransactionalFileOutputStream(args[2], false);
	}

	/** 
     * main running function of the class
     * <p>
     * It reads the input file line by line, counting the numbers of different characters
     * and print to the output file 
     * 
     * @since           1.0
     */
	public void run()
	{
		PrintStream out = new PrintStream(outFile);
		DataInputStream in = new DataInputStream(inFile);
		
		try {
			while (!isTerminate) {
				synchronized(this) {
					while(suspending){
						try {
							wait();
						} catch(InterruptedException e){
						}
					}
				}
				
				String line = in.readLine();
				if (line == null) break;

				String cSet = new String ("bcdfghjklmnpqrstvwxyz");
				String vSet = new String ("aeiou");
				String dSet = new String ("0123456789");
				String sSet = new String (" 	");
				String pSet = new String (",.;:!?&-'");

				int cCount = 0, vCount = 0, dCount = 0, sCount = 0, pCount = 0, ind = 0;
				while (ind <= line.length()-1) {
					char aChar = line.charAt(ind);
					if (cSet.indexOf(aChar) != -1) cCount++;
					else
					if (vSet.indexOf(aChar) != -1) vCount++;
					else
					if (dSet.indexOf(aChar) != -1) dCount++;
					else
					if (sSet.indexOf(aChar) != -1) sCount++;
					else
					if (pSet.indexOf(aChar) != -1) pCount++;
					ind++;
				}
				
				System.out.println("consonants:" + cCount + "; vowels:" +vCount + "; digits:" + dCount + "; spaces:" + sCount + "; punctuations:" + pCount);
				out.println("consonants:" + cCount + "; vowels:" +vCount + "; digits:" + dCount + "; spaces:" + sCount + "; punctuations:" + pCount);
				

				// Make grep take longer so that we don't require extremely large files for interesting results
				try {
					Thread.sleep(20000);
				} catch (InterruptedException e) {
					// ignore it
				}
			}
		} catch (EOFException e) {
			//End of File
		} catch (IOException e) {
			System.out.println ("CharClassCount: Error: " + e);
		}
		try {
			getInput().closeFile();
			getOutput().closeFile();
		} catch (IOException e) {
		}

	}

	/** 
     * suspend the current process
     * 
     * @since           1.0
     */
	public void suspend()
	{
		suspending = true;
	}

	/** 
     * convert the process information to string
     * 
     * @return			returns the string converted
     * @since           1.0
     */
	public String toString()
	{
		String tmp = this.getClass().getName(); 
		return tmp;
	}

	/** 
     * resume the current process
     * 
     * @since           1.0
     */
	public synchronized void resume()
	{
		suspending = false;
		this.notify();
	}

	/** 
     * terminate the current process
     * 
     * @since           1.0
     */
	public void terminate()
	{
		isTerminate = true;	
	}

	/** 
     * get the process's transactionFileInputStream
     * 
     * @return			the input stream of the process
     * @see TransactionalFileInputStream
     * @since           1.0
     */
	@Override
	public TransactionalFileInputStream getInput() {
		// TODO Auto-generated method stub
		return this.inFile;
	}

	/** 
     * get the process's transactionFileOutputStream
     * 
     * @return			the output stream of the process
     * @see TransactionalFileOutputStream
     * @since           1.0
     */
	@Override
	public TransactionalFileOutputStream getOutput() {
		// TODO Auto-generated method stub
		return this.outFile;
	}

}
