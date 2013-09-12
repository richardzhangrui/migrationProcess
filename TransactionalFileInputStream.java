

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;

public class TransactionalFileInputStream extends InputStream implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -8592574375810366902L;
	private String fileName;
	private long position;
	
	private transient FileInputStream file;
	private boolean migrated;
	

	public TransactionalFileInputStream(String _fileName) throws IOException {
		this.migrated = false;
		this.fileName = _fileName;
		this.position = 0;
		file = openFile();
	}


	private FileInputStream openFile() throws IOException {
		FileInputStream fs = new FileInputStream(fileName);
		fs.skip(position);
		return fs;
	}
	
	public void setMigrated(boolean value) {
		migrated = value;
	}
	
	public void closeFile() throws IOException {
		file.close();
	}

	/*Reads a single byte from the input stream
	*/
	@Override
	public int read() throws IOException {

		if(migrated) {
			file = openFile();
			migrated = false;
		}
		int retVal = file.read();
		//file.close();

		if (retVal != -1)
			position += 1;

		return retVal;
	}

	@Override
	public int read(byte[] b) throws IOException  {
		if(migrated) {
			file = openFile();
			migrated = false;
		}
		int retVal = file.read(b);
		//file.close();

		if (retVal != -1)
			position += retVal;

		return retVal;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException  {
		if(migrated) {
			file = openFile();
			migrated = false;
		}
		int retVal = file.read(b, off, len);
		//file.close();

		if (retVal != -1)
			position += retVal;

		return retVal;
	}

	/**
	 * skip (long n)
	 * Skips ahead in the stream by N bytes.
	 * 
	 * NOTE:Does not actually read from the file so may actually 
	 * seek past the end of the file.
	 */
	@Override
	public long skip(long n) {
		if ( n <= 0 ) return 0;
		position += n;
		return n;
	}

}
