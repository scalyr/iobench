import junit.framework.TestCase;

public class WriteNNParseTest extends TestCase {
	public void testWriteNN() throws Exception {
		Operation op = Op.parse("write32");
		assertEquals(32, ((WriteNN) op).n);
		
		assertTrue(Op.parse("write") instanceof Op);
		assertTrue(Op.parse("read")  instanceof Op);
	}
}
