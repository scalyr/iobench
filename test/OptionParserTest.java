import junit.framework.TestCase;
import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/**
 * Tests for OptionParser.
 */
public class OptionParserTest extends TestCase {
  @Test public void test() {
    OptionParser parser;
    
    parser = new OptionParser(new String[]{});
    assertArrayEquals(new String[]{}, parser.args);
    
    parser = new OptionParser(new String[]{"-foo", "--bar=", "-baz=one two three"});
    assertArrayEquals(new String[]{}, parser.args);
    assertEquals(null, parser.options.get("foo"));
    assertEquals("", parser.options.get("bar"));
    assertEquals("one two three", parser.options.get("baz"));
    
    parser = new OptionParser(new String[]{"red", "green", "-blue", "--foo=7", "purple"});
    assertArrayEquals(new String[]{"red", "green", "purple"}, parser.args);
    assertEquals(null, parser.options.get("blue"));
    assertEquals("7", parser.options.get("foo"));
  }
}
