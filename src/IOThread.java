import java.io.*;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * Implements the inner loop of the benchmark, repeatedly invoking a specified I/O operation at random positions.
 */
class IOThread extends Thread {
	Operation operation;
	ThreadSpec threadSpec;
	String filename;
	CountDownLatch latch;
	RandomAccessFile file;
	volatile boolean stopped = false;
	long fileLength;
	Random rand;
	IO io;
	
	IOThread(ThreadSpec threadSpec, Operation operation, String filename, CountDownLatch latch) {
		this.threadSpec = threadSpec;
		this.operation = operation;
		this.filename = filename;
		this.latch = latch;
		this.io = new IO(operationLength());
	}
	
	@Override
  public void run() {
		try {
			ready();
			steady();
			go();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	void ready() {
		try {
			file = new RandomAccessFile(filename, operation.fileMode());
			fileLength = file.length();
			rand = new Random(Main.random.nextLong());
			//TODO log these exceptions properly
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	void steady() throws InterruptedException {
		latch.countDown();
		latch.await();
		latch = null;
	}
	void go() {
		long maxOffset = fileLength - operationLength();
		String signature = threadSpec.getSignature();
		
		while (!stopped) {
			long offset = (rand.nextLong() & 0x7FFFFFFFFFFFFFFFL) % maxOffset;
			offset -= (offset % threadSpec.align.bytes);
			long begin = System.nanoTime();
			IOException ex = null;
			try {
				file.seek(offset);
				operation.perform(io, file, rand);
			} catch (IOException e) {
				ex = e;
			}
			long end = System.nanoTime();

			long durationNs = end - begin;
			
			if (ResultEmitter.outputRawResults)
				Log.operation(operation.toString(), offset, operationLength(), begin, end, ex);

			ResultEmitter.recordOperation(signature, begin, durationNs, ex != null);
			
			// After each operation, sleep by an amount of time proportional to the
			// operation's runtime. This allows us to control the rate at which operations
			// are issued in increments smaller than an entire thread.
			if (threadSpec.delayFactor > 0) {
				double msToSleep = durationNs * threadSpec.delayFactor / 1000000;
				try {
					Thread.sleep((int) Math.floor(msToSleep));
				} catch (InterruptedException ex2) {
					throw new RuntimeException(ex2);
				}
			}
		}
		closeFile();
	}

	long operationLength() {
		return threadSpec.size.bytes;
	}
	void closeFile() {
		try {file.close();} catch (IOException e) {}
	}
	void shutdown() {
		stopped = true;
	}
}
