import junit.framework.TestCase;

public class ThreadSpecTest extends TestCase {
	public void testParse() throws Exception {
		assertEquals(
				new ThreadSpec(Op.read, 1, new Size(1L<<20), new Size(1L<<10)),
				ThreadSpec.parse("read,1,1MB,1K"));
	}
}
