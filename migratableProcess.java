//package mainfolder;
import java.io.Serializable;
import java.lang.Runnable;

public interface migratableProcess extends Runnable, Serializable{
	
	void suspend();
	void resume();
	void terminate();
	String toString();
}
