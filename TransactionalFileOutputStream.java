

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;

public class TransactionalFileOutputStream extends OutputStream implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -4967310387767452678L;
	private String fileName;
	private long position;
	
	private transient RandomAccessFile file;
	boolean migrated;

	private RandomAccessFile openFile() throws IOException {
		RandomAccessFile file = new RandomAccessFile(fileName, "rws");
		file.seek(position);
		return file;
	}

	public TransactionalFileOutputStream(String _fileName, boolean temp) throws IOException {
		this.fileName = _fileName;
		this.position = 0;
		this.migrated = temp;
		this.file = openFile();
	}
	
	public void setMigrated(boolean value) {
		migrated = value;
	}
	
	public void closeFile() throws IOException {
		file.close();
	}
	
	@Override
	public void write(int b) throws IOException {
		if(migrated) {
			file = openFile();
			migrated = false;
		}
		file.write(b);
		//file.close();

		position++;
	}

	@Override
	public void write(byte[] b) throws IOException {
		if(migrated) {
			file = openFile();
			migrated = false;
		}
		file.write(b);
		//f.close();

		position += b.length;
	}

	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if(migrated) {
			file = openFile();
			migrated = false;
		}
		file.write(b, off, len);
		//f.close();

		position += len;
	}

}
