import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses thread specifications from the command line.
 */
public class ThreadOptionsParser {
	/**
	 * {"read,1,1K,1K.read,2,1K,1K", "write,1,1K,1K.write,2,1K,1K"}
	 * becomes
	 * {{"read,1,1K,1K","write,1,1K,1K"},{"read,2,1K,1K","write,2,1K,1K"}}
	 */
	static String[][] phases(String[] specs) {
		return transpose(splitAll(specs, ":"));
	}
	static String[][] splitAll(String[] specs, String separator) {
		String[][] results = new String[specs.length][];
		for(int i = 0; i < specs.length; ++i)
			results[i] = specs[i].split(separator);
		return results;
	}
	static String[][] transpose(String[][] strings) {
		String[][] results = new String[maxLength(strings)][strings.length];
		for(int i = 0; i < strings.length; ++i)
			for(int j = 0; j < strings[i].length; ++j)
				results[j][i] = strings[i][j];
		return results;
	}
	static int maxLength(String[][] strings) {
		int max = 0;
		for(int i = 0; i < strings.length; ++i)
			max = strings[i].length > max ? strings[i].length : max;
		return max;
	}
	
	/** each string spec "read,1,1K,1K" becomes a ThreadSpec object */
	static ThreadSpec[] specs(String[] specs) {
		ThreadSpec[] results = new ThreadSpec[specs.length];
		for(int i = 0; i < specs.length; ++i)
			results[i] = ThreadSpec.parse(specs[i]);
		return results;
	}
	
	// thread count parsing
	
	static Pattern range  = Pattern.compile("([0-9]+)\\.\\.([0-9]+)");
	static Pattern number = Pattern.compile("([0-9]+)");
	/** "1..4,8,12" becomes {1,2,3,4,8,12} */
	static int[] parse(String s) {
		Set<Integer> results = new HashSet<Integer>();
		String[] components = s.split(",");
		for (int i = 0; i < components.length; i++) {
			Matcher m;
			if ((m=range.matcher(components[i])).matches())
				results.addAll(range(number(m.group(1)),number(m.group(2))));
			else if ((m=number.matcher(components[i])).matches())
				results.add(number(m.group(1)));
		}
		return intArray(results);
	}
	static int number(String s) {return Integer.parseInt(s);}
	static Collection<Integer> range(int begin, int end) {
		Collection<Integer> results = new HashSet<Integer>();
		for(int i = begin; i <= end; ++i)
			results.add(i);
		return results;
	}
	static int[] intArray(Collection<Integer> xs) {
		int[] results = new int[xs.size()];
		int i = 0;
		for(Integer each : xs)
			results[i++] = each.intValue();
		return results;
	}
	
	//runs

	/**
	 * two phases,
	 * {{"read,i,1K,1K","write,i,1K,1K","writeFlush,i,1K,1K"},
	 *  {"read,i,2K,2K","write,i,2K,2K","writeFlush,i,2K,2K"}}
	 * expanded by threadCounts "1,2" become,
	 * {{"read,1,2K,2K","write,1,2K,2K","writeFlush,1,2K,2K"},
	 *  {"read,2,1K,1K","write,2,1K,1K","writeFlush,2,1K,1K"}}
	 */
	static String[][] runs(String[][] phases, String threadCounts) {
		return expand(phases, parse(threadCounts));
	}
//	(defun expand (phases threadcounts)
//		(defun instantiate (count)
//			(mapcar #'(lambda (phase)
//						(mapcar #'(lambda (spec)
//									(if (equal (cadr spec) 'i)
//										(list (car spec) count (caddr spec) (cadddr spec))
//										spec))
//								phase))
//					phases))
//		(flat1 (mapcar #'instantiate threadcounts)))
	static String[][] expand(String[][] phases, int[] threadCounts) {
		return flat1(collectInstantiatedPhases(threadCounts, phases));
	}
	static String[][][] collectInstantiatedPhases(int[] threadCounts, String[][] phases) {
		String[][][] results = new String[threadCounts.length][phases.length][];
		for(int i = 0; i < threadCounts.length; ++i)
			results[i] = instantiatePhases(phases, threadCounts[i]);
		return results;
	}
	static String[][] flat1(String[][][] xs) {
		List<String[]> results = new LinkedList<String[]>();
		for (int i = 0; i < xs.length; ++i)
			results.addAll(Arrays.asList(xs[i]));
		return results.toArray(new String[0][]);
	}
	static String[][] instantiatePhases(String[][] phases, int threadCount) {
		String[][] results = new String[phases.length][];
		for(int i = 0; i < phases.length; ++i)
			results[i] = instantiatePhase(phases[i], threadCount);
		return results;
	}
	static String[] instantiatePhase(String[] phase, int threadCount) {
		String[] results = new String[phase.length];
		for (int i = 0; i < phase.length; i++)
			results[i] = instantiateSpec(phase[i], threadCount);
		return results;
	}
	static String instantiateSpec(String spec, int threadCount) {
		return spec == null ? null : spec.replace(",i,", ","+threadCount+",");
	}
	static String[][] shuffle(String[][] xs) {
		List<String[]> results = Arrays.asList(xs);
		Collections.shuffle(results);
		return results.toArray(new String[0][]);
	}
}
