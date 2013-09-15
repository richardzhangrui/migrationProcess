import java.io.PrintStream;
import java.io.EOFException;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.Thread;
import java.lang.InterruptedException;


/**
 * quickSortProcess is a migratable class that read all the characters from a file
 * and use quick sort algorithm to reorder all the characters. Then write all of them
 * to the output file
 * <p>
 * This class implements the migratableProcess interface
 * 
 * @author      Rui Zhang
 * @author      Jing Gao
 * @version     1.0, 09/15/2013
 * @since       1.0
 */
public class quickSortProcess implements migratableProcess
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -1146704688960919863L;
	private TransactionalFileInputStream  inFile;
	private TransactionalFileOutputStream outFile;

	private volatile boolean suspending;
	private volatile boolean isTerminate;
	
	private String strBuffer;
	
	/** 
     * constructor of quickSortProcess class
     * 
     * @param args		arguments
     * @since           1.0
     */
	public quickSortProcess(String args[]) throws Exception
	{
		suspending = false;
		isTerminate = false;
		strBuffer = "";
		if (args.length != 3) {
			System.out.println("usage: quickSortProcess <inputFile> <outputFile>");
			throw new Exception("Invalid Arguments");
		}
		
		inFile = new TransactionalFileInputStream(args[1]);
		outFile = new TransactionalFileOutputStream(args[2], false);
	}
	
	/** 
     * quick sort main function
     * <p>
     * it first split the range and then quick sort the two ranges respectively
     * 
     * @param str		characters need to be sorted
     * @param start		start position need to be sorted
     * @param end		end position need to be sorted
     * @since           1.0
     */
	private void quickSort(char[] str, int start, int end) {
		if(start < end - 1) {
			int i = split(str,start,end);
			quickSort(str, start, i);
			quickSort(str, i+1, end);
		}
	}
	
	/** 
     * split the characters' array
     * <p>
     * After split, the left side of the return position is less than value on that position.
     * And the right side of the return position is greater than value on that
     * position
     * 
     * @param str		characters need to be split
     * @param start		start position need to be split
     * @param end		end position need to be split
     * @return			the position of the value on the previous start position
     * @since           1.0
     */
	private int split(char[] str, int start, int end) {
		int i = start;
		int value = str[i];
		
		for (int j = start + 1; j < end; j++) {
			if (str[j] <= value) {
				i++;
				if(i != j) {
					char tmp = str[i];
					str[i] = str[j];
					str[j] = tmp;
				}
			}
		}
		
		char tmp = str[start];
		str[start] = str[i];
		str[i] = tmp;
		
		return i;
	}
	
	/** 
     * main running function of the class
     * <p>
     * It reads the input file line by line, quick sort the characters read and output
     * them to the output file 
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
				
				strBuffer = new StringBuilder().append(strBuffer).append(line).toString();
				
				System.out.println(strBuffer);
				
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
				}
			}
			
			if (!isTerminate) {
				synchronized(this) {
					while(suspending){
						try {
							wait();
						} catch(InterruptedException e){
						}
					}
				}
				
				char arr[] = strBuffer.toCharArray();
				quickSort(arr,0 , arr.length);
				strBuffer = new String(arr);
				
				System.out.println(strBuffer);
				out.print(strBuffer);
				out.flush();
			}
			
		} catch (EOFException e) {
			//End of File
		} catch (IOException e) {
			System.out.println ("GrepProcess: Error: " + e);
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
