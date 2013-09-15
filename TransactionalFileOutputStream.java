

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;


/**
 * TransactionalFileOutputStream is output stream responsible for read serializable objects.
 * <p>
 * This class extends outputStream and implements Serializable.
 * <p>
 * This class has a position to record the place to which it has written a file. Thus, when 
 * migrating, the other slave can use this information to skip the already written bytes.
 * <p>
 * The migrated flag is used for optimization of process that has not been migrated.
 * If this flag is set to true, it means this process has been migrated and thus the file
 * should be reopened. Otherwise, every function won't reopen the file and save the time 
 * of write. 
 * <p>
 * The transient keyword of file is to ensure the file won't be migrated through objectStream
 * because it is not a serializable object.
 * 
 * @author      Rui Zhang
 * @author      Jing Gao
 * @version     1.0, 09/15/2013
 * @since       1.0
 */
public class TransactionalFileOutputStream extends OutputStream implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4967310387767452678L;
	private String fileName;
	private long position;
	
	private transient RandomAccessFile file;
	boolean migrated;
	
	/** 
     * open the file and skip to the write position
     * 
     * @return			returns the RandomAccessFile to write file	
     * @since           1.0
     */
	private RandomAccessFile openFile() throws IOException {
		RandomAccessFile file = new RandomAccessFile(fileName, "rws");
		file.seek(position);
		return file;
	}
	
	/** 
     * constructor of TransactionalFileOutputStream class
     * 
     * @param _fileName	filename to identify the file to open	
     * @param temp		the initial migrated flag
     * @since           1.0
     */
	public TransactionalFileOutputStream(String _fileName, boolean temp) throws IOException {
		this.fileName = _fileName;
		this.position = 0;
		this.migrated = temp;
		this.file = openFile();
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
     * Writes a single byte to the output stream
     * 
     * @since           1.0
     */
	@Override
	public void write(int b) throws IOException {
		if(migrated) {
			file = openFile();
			migrated = false;
		}
		file.write(b);

		position++;
	}
	
	/** 
     * Writes multiple bytes to the output stream
     * 
     * @since           1.0
     */
	@Override
	public void write(byte[] b) throws IOException {
		if(migrated) {
			file = openFile();
			migrated = false;
		}
		file.write(b);

		position += b.length;
	}

	/** 
     * Writes multiple bytes to the output stream with a length limit and from
     * the preset offset 
     * 
     * @since           1.0
     */
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if(migrated) {
			file = openFile();
			migrated = false;
		}
		file.write(b, off, len);

		position += len;
	}

}
