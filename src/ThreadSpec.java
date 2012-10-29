import java.util.concurrent.CountDownLatch;

/**
 * Represents a parsed thread specification.
 */
public class ThreadSpec {
	public static ThreadSpec parse(String spec) {
		if (spec == null) return null;
		String[] s = spec.split(",");
		return new ThreadSpec(Op.parse(s[0]), Double.parseDouble(s[1]), Size.parse(s[2]), Size.parse(s[3]));
	}
	
	final Operation operation;
	final Size size;
	final Size align;
	
	final double threadCount;
	final int physicalThreads;
	final double delayFactor;
	
	public ThreadSpec(Operation operation, double threadCount, Size size, Size align) {
		this.operation = operation;
		this.threadCount = threadCount;
		this.size = size;
		this.align = align;
		
		// To support "fractional" threads, we round up to the next integer and then
		// tell each thread to space out its operations slightly. For instance, if
		// 2.5 threads are requested, we run launch 3 threads and tell each thread
		// to add a 20% delay factor. (Meaning, after an operation that takes T
		// seconds, the thread will sleep for 0.2T seconds.)
		
		physicalThreads = (int) Math.ceil(threadCount);
		delayFactor = (physicalThreads / threadCount) - 1;
	}
	void startThreadsFor(Run runner, String file, CountDownLatch latch) {
		for(int i = 0; i < physicalThreads; ++i)
			runner.register(new IOThread(this, operation, file, latch)).start();
	}
	
	/**
	 * Return a compact string describing the operation we perform, e.g. "read,64K,4K"
	 * for 64K reads aligned at 4K boundaries.
	 */
	public String getSignature() {
		return operation + "," + size + "," + align;
	}

	/**
	 * return a complete description of my constructor parameters
	 */
	@Override
  public String toString() {
		return operation + "," + threadCount + "," + size + "," + align;
	}
	
	//generated code
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((align == null) ? 0 : align.hashCode());
		result = prime * result
				+ ((operation == null) ? 0 : operation.hashCode());
		result = prime * result + ((size == null) ? 0 : size.hashCode());
		result = prime * result + new Double(threadCount).hashCode();
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ThreadSpec other = (ThreadSpec) obj;
		if (align == null) {
			if (other.align != null)
				return false;
		} else if (!align.equals(other.align))
			return false;
		if (operation != other.operation)
			return false;
		if (size == null) {
			if (other.size != null)
				return false;
		} else if (!size.equals(other.size))
			return false;
		if (threadCount != other.threadCount)
			return false;
		return true;
	}
}
