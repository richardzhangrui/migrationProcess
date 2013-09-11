JFLAGS =
JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $(JFLAGS) $*.java

CLASSES = \
	migratableProcess.java \
	TransactionalFileInputStream.java \
	TransactionalFileOutputStream.java \
	slaveHost.java \
	message.java \
	serviceForSlave.java \
	managerServer.java \
	processManager.java \
	slaveNode.java \
	GrepProcess.java \

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class