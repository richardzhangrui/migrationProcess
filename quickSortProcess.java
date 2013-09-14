import java.io.PrintStream;
import java.io.EOFException;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.Thread;
import java.lang.InterruptedException;

public class quickSortProcess implements migratableProcess
{
	private TransactionalFileInputStream  inFile;
	private TransactionalFileOutputStream outFile;

	private volatile boolean suspending;
	private volatile boolean isTerminate;
	
	private String strBuffer;
	
	public quickSortProcess(String args[]) throws Exception
	{
		suspending = false;
		isTerminate = false;
		strBuffer = "";
		if (args.length != 3) {
			System.out.println("usage: inverseFileProcess <queryString> <inputFile> <outputFile>");
			throw new Exception("Invalid Arguments");
		}
		
		inFile = new TransactionalFileInputStream(args[1]);
		outFile = new TransactionalFileOutputStream(args[2], false);
	}
	
	private void quickSort(char[] str, int start, int end) {
		if(start < end - 1) {
			int i = split(str,start,end);
			quickSort(str, start, i);
			quickSort(str, i+1, end);
		}
	}
	
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
				
				//strBuffer = new StringBuilder().append(strBuffer).append(line.substring(0, line.length()-1)).toString();
				strBuffer = new StringBuilder().append(strBuffer).append(line).toString();
				
				System.out.println(strBuffer);
				
				// Make grep take longer so that we don't require extremely large files for interesting results
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					// ignore it
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

	public void suspend()
	{
		suspending = true;
	}

	public String toString()
	{
		String tmp = this.getClass().getName(); 
		return tmp;
	}

	public synchronized void resume()
	{
		suspending = false;
		this.notify();
	}

	public void terminate()
	{
		isTerminate = true;	
	}

	@Override
	public TransactionalFileInputStream getInput() {
		// TODO Auto-generated method stub
		return this.inFile;
	}

	@Override
	public TransactionalFileOutputStream getOutput() {
		// TODO Auto-generated method stub
		return this.outFile;
	}

}
