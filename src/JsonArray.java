import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;

public class JsonArray extends ArrayList<Object> {
  public JsonArray() {
  }
  
  public JsonArray(Object ... values) {
    for (Object value : values)
      add(value);
  }
  
  /**
   * Return an Iterable over this array, assuming that all entries in the array are JsonObjects.
   * If the array contains any objects of other type, an exception will eventually be thrown.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Iterable<JsonObject> getObjectIterable() {
    return (Iterable<JsonObject>)(Iterable)this;
  }
  
  public JsonObject getObject(int index) {
    return (JsonObject) get(index);
  }
  
  /**
   * Append the specified object to the array, and return the array.  Named for its use in builder-style
   * construction.
   */
  public JsonArray build(Object x) {
    add(x);
    return this;
  }
  
  /**
   * Return a deep copy of this object.
   */
  public JsonArray deepCopy() {
    JsonArray copy = new JsonArray();
    
    for (Object value : this)
      copy.add(JsonObject.deepCopy(value));
    
    return copy;
  }
  
  /**
   * Create a JsonArray with the specified values.
   */
  public static JsonArray create(Object ... values) {
    JsonArray ar = new JsonArray();
    for (Object value : values)
      ar.add(value);
    return ar;
  }
  
  /**
   * Return a JSON representation of this object.
   */
  public String serialize() {
    return serialize(true);
  }
  
  public String serialize(boolean strict) {
    StringWriter writer = new StringWriter();
    serialize(writer, strict);
    return writer.toString();
  }
  
  /**
   * Emit a JSON representation of this object.
   */
  public void serialize(Writer writer, boolean strict) {
    JsonWriter.write(this, writer, strict);
  }
}
