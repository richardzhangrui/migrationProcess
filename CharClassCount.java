import java.io.PrintStream;
import java.io.EOFException;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.Thread;
import java.lang.InterruptedException;

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
