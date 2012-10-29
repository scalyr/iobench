import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

public class ThreadOptionsParserTest extends TestCase {

	//thread spec parsing
	
	public void testPhases1() throws Exception {
		String[][] phases = ThreadOptionsParser.phases(new String[]{"read,5,1K,1K", "write,2,1K,1K"});
		assertEquals(1, phases.length);
		assertEquals("read,5,1K,1K",  phases[0][0]);
		assertEquals("write,2,1K,1K", phases[0][1]);
	}
	public void testPhases2() throws Exception {
		String[][] phases = ThreadOptionsParser.phases(new String[]{"read,5,1K,1K:read,2,1K,1K", "write,2,1K,1K:write,3,1K,1K"});
		assertEquals(2, phases.length);
		assertEquals("read,5,1K,1K" , phases[0][0]);
		assertEquals("write,2,1K,1K", phases[0][1]);
		assertEquals("read,2,1K,1K" , phases[1][0]);
		assertEquals("write,3,1K,1K", phases[1][1]);
	}
	
	//thread count parsing

	public void test1() throws Exception {
		assertEqualValues(new int[]{1,2,3}, ThreadOptionsParser.parse("1..3"));
		assertEqualValues(new int[]{1,3}, ThreadOptionsParser.parse("1,3"));
		assertEqualValues(new int[]{1,2,3,5}, ThreadOptionsParser.parse("1..3,5"));
	}
	void assertEqualValues(int[] expected, int[] actual) {
		assertEquals(sortedCollection(expected), sortedCollection(actual));
	}
	Collection<Integer> sortedCollection(int[] xs) {
		Set<Integer> set = new HashSet<Integer>();
		for (int i = 0; i < xs.length; i++)
			set.add(xs[i]);
		List<Integer> results = new ArrayList<Integer>(set);
		Collections.sort(results);
		return results;
	}
	public void testVariableExpansion() throws Exception {
		assertEquals("read,3,1K,1M", ThreadOptionsParser.instantiateSpec("read,i,1K,1M", 3));
		assertEquals("read,4,1K,1M", ThreadOptionsParser.instantiateSpec("read,4,1K,1M", 3));
	}
	public void testVariableExpansion2() throws Exception {
		String[][] phases = new String[][]{{"read,i,1K,1K","read,i,2K,2K"},
										   {"write,i,1K,1K","write,i,2K,2K"}};
		String threadCounts="8,16";
		String[][] runs = new String[][]{{"read,8,1K,1K","read,8,2K,2K"},
										 {"write,8,1K,1K","write,8,2K,2K"},
										 {"read,16,1K,1K","read,16,2K,2K"},
										 {"write,16,1K,1K","write,16,2K,2K"}};
		assertEqualValues(runs, ThreadOptionsParser.runs(phases, threadCounts));
	}
	void assertEqualValues(String[][] expected, String[][] actual) {
		assertEquals(expected.length, actual.length);
		for(int i = 0; i < expected.length; ++i)
			assertTrue(contains(actual, expected[i]));
	}
	boolean contains(String[][] xs, String[] x) {
		for (int i = 0; i < xs.length; i++)
			if (equals(xs[i],x))
				return true;
		return false;
	}
	boolean equals(String[] x, String[] y) {
		return Arrays.asList(x).equals(Arrays.asList(y));
	}
}
