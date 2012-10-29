import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/**
 * Kicks off a fleet of IOThreads to perform a benchmark.
 */
public class Run {
	Collection<IOThread> workers;
	{initializeWorkers();}
	void initializeWorkers() {workers = new HashSet<IOThread>();}
	public void run(String file, int durationSeconds, OptionParser options, ThreadSpec[] threadSpecs) {
		Log.run(threadSpecs);
		ResultEmitter.initialize(options, durationSeconds);
		CountDownLatch latch = new CountDownLatch(threadCount(threadSpecs));
		for(ThreadSpec each : threadSpecs)
			runThreads(each, file, latch);
		latch.countDown();
		try {Thread.sleep(durationSeconds*1000L);} catch (InterruptedException e) {e.printStackTrace();}
		stop();
		ResultEmitter.emitFinalStats();
		if (options.options.containsKey("json"))
			ResultEmitter.emitJsonDump(durationSeconds, threadSpecs, new File(file).length());
	}
	int threadCount(ThreadSpec[] threadSpecs) {
		int sum = 0;
		for (ThreadSpec each : threadSpecs)
			sum += each == null ? 0 : each.physicalThreads;
		return sum;
	}
	void runThreads(ThreadSpec spec, String file, CountDownLatch latch) {
		if (spec == null) return;
		spec.startThreadsFor(this, file, latch);
	}
	void stop() {
		for(IOThread each : workers)
			each.shutdown();
		for(IOThread each : workers)
			try {each.join(60000);} catch (InterruptedException e) {e.printStackTrace();}
		initializeWorkers();
	}
	//called on the main thread by workers, before they actually start
	IOThread register(IOThread worker) {
		workers.add(worker);
		return worker;
	}
}
