import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quick-and-dirty utility for parsing command-line options.
 * 
 * This class identifies all arguments beginning with a dash, and stores them in
 * a table indexed by name. Single or double dashes are accepted. The remaining
 * (non-dash) arguments are placed in an "args" array. For instance, given the
 * following input arguments:
 * 
 *   red green -blue --foo=7 purple
 *   
 * args would be ["red", "green", "purple"], and the parameter map would be
 * {blue -> null, foo -> "7"}.
 */
public class OptionParser {
  /**
   * Command-line arguments, *excluding* arguments that begin with a dash (those
   * wind up in the options table).
   */
  public final String[] args;
  
  public final Map<String, String> options;
  
  /**
   * Regular expression for parsing dash options. The first match group is
   * the parameter name, and the second parameter group is the parameter value,
   * or null if the parameter does not specify a value (no '=').
   */
  private static final Pattern paramPattern = Pattern.compile(
      "^" +
      "[-]{1,2}+" +            // 1 or 2 dashes
      "([a-zA-Z0-9._-]+)" +    // parameter name
      "(?:=(.*))?" +           // optional parameter value
      "$"
      );
  
  public OptionParser(String[] rawArgs) {
    options = new HashMap<String, String>();
    List<String> argsList = new ArrayList<String>();
    
    for (String rawArg : rawArgs) {
      Matcher matcher = paramPattern.matcher(rawArg);
      if (matcher.matches()) {
        String name = matcher.group(1);
        String value = matcher.group(2);
        options.put(name, value);
      } else {
        argsList.add(rawArg);
      }
    }
    
    args = argsList.toArray(new String[0]);
  }
  
  /**
   * If we have an option of the given name, parse it as an integer and return
   * the value. If we have no option of this name, return null.
   * 
   * If we have an option of the given name, but it does not have a parseable
   * integer value, we print a message to stderr and throw an exception.
   */
  public Integer parseInt(String name) {
    String rawValue = options.get(name);
    if (rawValue == null)
      return null;
    
    try {
      return Integer.parseInt(rawValue);
    } catch (NumberFormatException ex) {
      String message = "Value [" + rawValue + "] for option [" + name + "] must be an integer";
      System.err.println(message);
      throw new RuntimeException(message, ex);
    }
  }
}
