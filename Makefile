JAVAC     = javac
JAVA      = java
JVM_FLAGS = -Xmx2g
SOURCES   = MatrixUtils.java ProcessWorker.java ThreadMultiplier.java \
            ProcessMultiplier.java MainBenchmark.java

.PHONY: all compile run clean

all: compile

compile:
	$(JAVAC) $(SOURCES)

run: compile
	$(JAVA) $(JVM_FLAGS) MainBenchmark

clean:
	rm -f *.class
