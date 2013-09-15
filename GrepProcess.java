import java.io.PrintStream;
import java.io.EOFException;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.Thread;
import java.lang.InterruptedException;

public class GrepProcess implements migratableProcess
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -1216123251987660620L;
	private TransactionalFileInputStream  inFile;
	private TransactionalFileOutputStream outFile;
	private String query;

	private volatile boolean suspending;
	private volatile boolean isTerminate;

	public GrepProcess(String args[]) throws Exception
	{
		suspending = false;
		isTerminate = false;
		if (args.length != 3) {
			System.out.println("usage: GrepProcess <queryString> <inputFile> <outputFile>");
			throw new Exception("Invalid Arguments");
		}
		
		query = args[0];
		inFile = new TransactionalFileInputStream(args[1]);
		outFile = new TransactionalFileOutputStream(args[2], false);
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
				
				System.out.println(line);
				out.println(line);
				
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
