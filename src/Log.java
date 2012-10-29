import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Emits a log message for each individual I/O operation.
 */
public class Log {
	static void start(String[] args) {
		log("date", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()));
		log("arguments", printArgs(args));
	}
	static String printArgs(String[] args) {
		StringBuilder result = new StringBuilder();
		result.append(args[0]);
		for(int i = 1; i < args.length; ++i) {
			result.append(' ');
			result.append(args[i]);
		}
		return result.toString();
	}
	static void fileSize(String file) {
		size(new File(file).length());
	}
	static void size(long bytes) {
		log("filesize", bytes);
	}
	static void run(ThreadSpec[] specs) {
		log("threadSpecs", printSeparated(specs, " "));
	}
	static String printSeparated(Object[] xs, String separator) {
		if (xs.length == 0) return "";
		StringBuilder s = new StringBuilder();
		s.append(xs[0]);
		for(int i=1; i<xs.length; ++i) {
			s.append(separator);
			s.append(xs[i]);
		}
		return s.toString();
	}
	static void log(String key, Object s) {
		System.out.println("# " + key + ": " + s);
	}

	static void operation(String operation, long position, long length, long beginNanos, long endNanos, IOException ex) {
		StringBuilder entry = new StringBuilder();
		entry.append("op: ");
		entry.append(operation);
		addField(entry, position);
		addField(entry, length);
		addField(entry, (endNanos-beginNanos)/1000);
		addField(entry, endNanos/1000);
		if (ex != null) addField(entry, "ERROR[" + ex + "]");
		log(entry.toString());
	}
	static void addField(StringBuilder s, Object o) {
		s.append(" ");
		s.append(o);
	}
	static void log(String entry) {
		System.out.println(entry);
	}
}
