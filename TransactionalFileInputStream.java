

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;

/**
 * TransactionFileInputStream is input stream responsible for read serializable objects.
 * <p>
 * This class extends InputStream and implements Serializable.
 * <p>
 * This class has a position to record the place to which it has read a file. Thus, when 
 * migrating, the other slave can use this information to skip the already read bytes.
 * <p>
 * The migrated flag is used for optimization of process that has not been migrated.
 * If this flag is set to true, it means this process has been migrated and thus the file
 * should be reopened. Otherwise, every function won't reopen the file and save the time 
 * of read. 
 * <p>
 * The transient keyword of file is to ensure the file won't be migrated through objectStream
 * because it is not a serializable object.
 * 
 * @author      Rui Zhang
 * @author      Jing Gao
 * @version     1.0, 09/15/2013
 * @since       1.0
 */
public class TransactionalFileInputStream extends InputStream implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -8592574375810366902L;
	private String fileName;
	private long position;
	
	private transient FileInputStream file;
	private boolean migrated;
	
	/** 
     * constructor of TransactionalFileInputStream class
     * 
     * @param _fileName	filename to identify the file to open	
     * @since           1.0
     */
	public TransactionalFileInputStream(String _fileName) throws IOException {
		this.migrated = false;
		this.fileName = _fileName;
		this.position = 0;
		file = openFile();
	}

	/** 
     * open the file and skip to the read position
     * 
     * @return			returns the FileInputStream to read file	
     * @since           1.0
     */
	private FileInputStream openFile() throws IOException {
		FileInputStream fs = new FileInputStream(fileName);
		fs.skip(position);
		return fs;
	}
	
	/** 
     * set the migrated flag
     * 
     * @param value		the value whether migrated
     * @since           1.0
     */
	public void setMigrated(boolean value) {
		
		migrated = value;
	}
	
	/** 
     * close the file
     * 
     * @since           1.0
     */
	public void closeFile() throws IOException {
		file.close();
	}
	
	/** 
     * Reads a single byte from the input stream
     * 
     * @return			return the number of bytes read
     * @since           1.0
     */
	@Override
	public int read() throws IOException {

		if(migrated) {
			file = openFile();
			migrated = false;
		}
		int retVal = file.read();

		if (retVal != -1)
			position += 1;

		return retVal;
	}
	
	/** 
     * Reads multiple bytes from the input stream
     * 
     * @return			return the number of bytes read
     * @since           1.0
     */
	@Override
	public int read(byte[] b) throws IOException  {
		if(migrated) {
			file = openFile();
			migrated = false;
		}
		int retVal = file.read(b);

		if (retVal != -1)
			position += retVal;

		return retVal;
	}
	
	/** 
     * Reads multiple bytes from the input stream with a length limit and from
     * the preset offset 
     * 
     * @return			return the number of bytes read
     * @since           1.0
     */
	@Override
	public int read(byte[] b, int off, int len) throws IOException  {
		if(migrated) {
			file = openFile();
			migrated = false;
		}
		int retVal = file.read(b, off, len);

		if (retVal != -1)
			position += retVal;

		return retVal;
	}

	/**
	 * Skips ahead in the stream by n bytes.
	 * 
	 * @return			return the position already read to
	 * @since			1.0
	 */
	@Override
	public long skip(long n) {
		if ( n <= 0 ) return 0;
		position += n;
		return n;
	}

}
