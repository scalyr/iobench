import java.io.*;
import java.util.*;
import java.lang.reflect.InvocationTargetException;

/*
Usage examples:

1. java Main create file1 1K

2. java Main run file1 1 read,5,25,64 write,2,100,16 >logfile
run args are: FILENAME RUNTIME THREADSPEC ...
RUNTIME is in seconds
THREADSPEC is of the form: "<op>,<threadcount>,<size>,<align>"
where <op> : read | write | writeFlush | writeNN
NN is an integer specifying roughly how many operations to sync after

update 1: "." for phase separator
read,1,25,64.read,1,25,64
specifies two sequential phases, as though the program were run twice.
read,1,1K,1K.write,1,1K,1K read,1,2K,2K.write,1,2K,2K
specifies a purely read phase followed by a purely write phase
update 2: vary thread counts
-threadCounts=1..4,8,16 read,i,25,64
creates 6 phases with 1,2,3,4,8,16 threads respectively.
update 3: -shuffle to randomize the phases
*/

/**
 * Parses the command line, determines which benchmarks to run in which order, and invokes the benchmarks.
 */
public class Main {
	public static Random random = new Random(System.currentTimeMillis());
	
	public static OptionParser options;
	
	public static void main(String[] args) throws Exception {
		options = new OptionParser(args);
		if (options.args.length == 0) {
			System.err.println("error: missing arguments");
			System.exit(1);
		}
		Log.start(args);
		invoke(options.args[0], options);
	}
	
	static void invoke(String fn, OptionParser options_)
			throws IllegalArgumentException, SecurityException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException {
		Main.class.getMethod(fn, new Class[]{OptionParser.class}).invoke(null, options_);
	}
	
	public static void create(OptionParser options_) throws IOException {
		long size = Size.parse(options_.args[2]).bytes;
		Log.size(size);
		DataOutputStream stream = new DataOutputStream(new FileOutputStream(options_.args[1]));
		new IO(size).writeRandom(stream, random);
		stream.close();
	}
	
	public static void run(OptionParser options_) {
		String file = options_.args[1];
		Log.fileSize(file);
		int durationSecs = Integer.parseInt(options_.args[2]);
		
		String[] specs = new String[options_.args.length - 3];
		for (int i = 0; i < specs.length; ++i)
			specs[i] = options_.args[i+3];
		
		String[][] phases = ThreadOptionsParser.phases(specs);
		String[][] runs = !options_.options.containsKey("threadCounts")
								? phases
								: ThreadOptionsParser.runs(phases, options_.options.get("threadCounts"));
		String[][] shuffledRuns = !options_.options.containsKey("shuffle")
									? runs
									: ThreadOptionsParser.shuffle(runs);
		for (String[] each : shuffledRuns)
			new Run().run(file, durationSecs, options_, ThreadOptionsParser.specs(each));
	}
}
