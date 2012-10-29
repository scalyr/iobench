import java.io.IOException;
import java.io.Writer;
import java.util.Map;

public class JsonWriter {
  /**
   * Emit a JSON encoding of the specified object.
   */
  public static void write(Object value, Writer out, boolean strict) {
    try {
      if (value == null) {
        out.write("null");
      } else if (value instanceof String) {
        out.write('"');
        writeEscaped((String)value, out);
        out.write('"');
      } else if (value instanceof JsonObject) {
        out.write('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : ((JsonObject)value).entrySet()) {
          if (first)
            first = false;
          else
            out.write(',');
          
          out.write('"');
          writeEscaped(entry.getKey(), out);
          out.write("\":");
          write(entry.getValue(), out, strict);
        }
        out.write('}');
      } else if (value instanceof JsonArray) {
        out.write('[');
        boolean first = true;
        for (Object entry : (JsonArray)value) {
          if (first)
            first = false;
          else
            out.write(',');
          
          write(entry, out, strict);
        }
        out.write(']');
      } else if (value instanceof Integer || value instanceof Long) {
        out.write(value.toString());
      } else if (value instanceof Boolean) {
        out.write(value.toString());
      } else if (value instanceof Double) {
        double d = (Double) value;
        if (Double.isInfinite(d) || Double.isNaN(d))
          throw new RuntimeException("Can't serialize infinite or NaN values in JSON");
        out.write(value.toString());
      } else if (value instanceof Float) {
        float f = (Float) value;
        if (Float.isInfinite(f) || Float.isNaN(f))
          throw new RuntimeException("Can't serialize infinite or NaN values in JSON");
        out.write(value.toString());
      } else {
        if (strict)
          throw new RuntimeException("Can't serialize value in JSON format: " + value);
        else
          out.write(value.toString());
      }
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static void writeEscaped(String value, Appendable out) {
    try {
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        switch (c) {
          case '"':  out.append("\\\""); break;
          case '\\': out.append("\\\\"); break;
          case '\b': out.append("\\b"); break;
          case '\f': out.append("\\f"); break;
          case '\n': out.append("\\n"); break;
          case '\r': out.append("\\r"); break;
          case '\t': out.append("\\t"); break;
          case '/':  out.append("\\/"); break;
          default:
            // Reference: http://www.unicode.org/versions/Unicode5.1.0/
            if ((c >= '\u0000' && c <= '\u001F') || (c >= '\u007F' && c <= '\u009F') || (c >= '\u2000' && c <= '\u20FF')) {
              String ss = Integer.toHexString(c);
              out.append("\\u");
              for (int k = 0; k < 4 - ss.length(); k++)
                out.append('0');
              out.append(ss.toUpperCase());
            } else {
              out.append(c);
            }
        }
      }
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }
}
