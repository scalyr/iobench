import junit.framework.TestCase;

public class SizeTest extends TestCase {
	public void testParse() throws Exception {
		Object[][] tests = new Object[][] {
				{0L, "0"},
				{10L, "10"},
				{10L, "10B"},
				{1024L, "1K"},
				{2048L, "2K"},
				{1024*1024L, "1M"},
				{1024*1024*1024L, "1G"},
				{5L<<30, "5G"},
				{1L<<40, "1T"},
				{1L<<50, "1P"},
				{1L<<33, "8G"},
				{1024L, "1KB"}};
		for(int i = 0; i < tests.length; ++i)
			test(i, (Long) tests[i][0], (String) tests[i][1]);
	}
	void test(int number, long expectedByteSize, String s) {
		assertEquals(""+number, expectedByteSize, Size.parse(s).bytes);
	}
	
	public void testToString() {
		assertEquals("4K", new Size(4096).toString());
		assertEquals("4095", new Size(4095).toString());
		assertEquals("1M", new Size(1L<<20).toString());
		assertEquals("2G", new Size(1L<<31).toString());
		assertEquals("3T", new Size((1L<<40) * 3).toString());
		assertEquals("4P", new Size((1L<<50) * 4).toString());
	}
	
	public static void main(String[] args) {
		System.out.println(Integer.MAX_VALUE);
		System.out.println(Long.MAX_VALUE);
		System.out.println(Math.pow(1024l, 5));
		System.out.println(Integer.MAX_VALUE);
		System.out.println(5<<30); //overflow because int
		System.out.println(5L<<30);
		System.out.println(Math.pow(1024l, 5) < Long.MAX_VALUE);
	}
}
