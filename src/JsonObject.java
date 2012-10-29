import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class JsonObject extends HashMap<String, Object> {
  public JsonObject() {
  }
  
  public JsonObject(String key, Object value) {
    put(key, value);
  }
  
  public JsonObject(String key1, Object value1, String key2, Object value2) {
    put(key1, value1);
    put(key2, value2);
  }
  
  public JsonObject(String key1, Object value1, String key2, Object value2, String key3, Object value3) {
    put(key1, value1);
    put(key2, value2);
    put(key3, value3);
  }
  
  public JsonObject(String key1, Object value1, String key2, Object value2, String key3, Object value3,
      String key4, Object value4) {
    put(key1, value1);
    put(key2, value2);
    put(key3, value3);
    put(key4, value4);
  }
  
  public JsonObject(String key1, Object value1, String key2, Object value2, String key3, Object value3,
      String key4, Object value4, String key5, Object value5) {
    put(key1, value1);
    put(key2, value2);
    put(key3, value3);
    put(key4, value4);
    put(key5, value5);
  }
  
  public JsonObject(String key1, Object value1, String key2, Object value2, String key3, Object value3,
      String key4, Object value4, String key5, Object value5, String key6, Object value6) {
    put(key1, value1);
    put(key2, value2);
    put(key3, value3);
    put(key4, value4);
    put(key5, value5);
    put(key6, value6);
  }
  
  public JsonObject(String key1, Object value1, String key2, Object value2, String key3, Object value3,
      String key4, Object value4, String key5, Object value5, String key6, Object value6, String key7, Object value7) {
    put(key1, value1);
    put(key2, value2);
    put(key3, value3);
    put(key4, value4);
    put(key5, value5);
    put(key6, value6);
    put(key7, value7);
  }
  
  @Override public String toString() {
    return serialize(false);
  }
  
  public String toJson() {
    return serialize();
  }
  
  /**
   * Return a shallow copy of this object.
   */
  public JsonObject shallowCopy() {
    JsonObject copy = new JsonObject();
    copy.shallowCopyFrom(this);
    return copy;
  }
  
  /**
   * Copy all attributes from src to this object. Any attributes of this
   * object which did not collide with attributes of src will be retained.
   */
  public void shallowCopyFrom(JsonObject src) {
    if (src != null)
      for (String key : src.keySet())
        set(key, src.get(key));
  }
  
  /**
   * Return a deep copy of this object.
   */
  public JsonObject deepCopy() {
    JsonObject copy = new JsonObject();
    
    for (Map.Entry<String, Object> entry : entrySet())
      copy.put(entry.getKey(), deepCopy(entry.getValue()));
    
    return copy;
  }
  
  static Object deepCopy(Object value) {
    if (value instanceof JsonObject)
      return ((JsonObject)value).deepCopy();
    else if (value instanceof JsonArray)
      return ((JsonArray)value).deepCopy();
    else
      return value;
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
  
  /**
   * Set the specified key to the specified value, and return this object.  Useful for builder-style coding, i.e.
   * 
   *   JsonObject object = new JsonObject().set("foo", 1).set("bar", 2);
   */
  public JsonObject set(String key, Object value) {
    super.put(key, value);
    return this;
  }
  
  public JsonObject setIfNotNull(String key, Object value) {
    if (value != null)
      super.put(key, value);
    return this;
  }
  
  @Override public Object put(String key, Object value) {
    if (value == null) {
      Object oldValue = get(key);
      remove(key);
      return oldValue;
    } else {
      return super.put(key, value);
    }
  }
  
  /**
   * Return the specified field.  The field must be either a boolean, or a string; if a string,
   * we return false if the value was empty, "f", or "false", otherwise true.  For numeric types,
   * we return false or true for 0 or 1 respectively; for other values, we throw an exception.
   * For fields of other types, we always throw an exception.
   * 
   * If this object does not have the specified field, return defaultValue.
   */
  public boolean getBool(String field, boolean defaultValue) {
    if (!containsKey(field))
      return defaultValue;
    
    Object value = get(field);
    if (value instanceof Boolean)
      return (Boolean)value;
    else if (value instanceof Integer)
      return numToBool((double)(Integer)value);
    else if (value instanceof Long)
      return numToBool((double)(Long)value);
    else if (value instanceof Double)
      return numToBool((double)(Double)value);
    else {
      String s = (String) value;
      return !s.equals("") && !s.equals("f") && !s.equals("false");
    }
  }

  private boolean numToBool(double d) {
    if (Math.abs(d) < 1E-10)
      return false;
    
    if (Math.abs(1 - d) < 1E-10)
      return true;
    
    throw new RuntimeException("Can't convert numeric value [" + d + "] to boolean");
  }

  /**
   * Like getBool(field, defaultValue), but if the field is not present, we throw an exception instead of returning
   * a default value.
   */
  public boolean getBool(String field) {
    if (!containsKey(field))
      throw new RuntimeException("missing field " + field);
    
    return getBool(field, false);
  }

  /**
   * Return the specified field.  The field must be either a number, or a string; if a string,
   * we invoke Integer.parseInt.  For fields of other types, or strings which fail parseInt, we throw an exception.
   * Long or Double values are coerced to int (i.e. truncated / rounded) without error checking.
   * 
   * If this object does not have the specified field, return defaultValue.
   */
  public Integer getInt(String field, Integer defaultValue) {
    if (!containsKey(field))
      return defaultValue;
    
    Object value = get(field);
    if (value instanceof Integer)
      return (Integer)value;
    else if (value instanceof Long)
      return (int)(long)(Long)value;
    else if (value instanceof Double)
      return (int)(double)(Double)value;
    else {
      String s = (String) value;
      return Integer.parseInt(s);
    }
  }

  /**
   * Like getInt(field, defaultValue), but if the field is not present, we throw an exception instead of returning
   * a default value.
   */
  public int getInt(String field) {
    if (!containsKey(field))
      throw new RuntimeException("missing field " + field);
    
    return getInt(field, 0);
  }

  /**
   * Return the specified field. The field must be either a number, or a string; if a string,
   * we invoke Long.parseLong. For fields of other types, or strings which fail parseLong, we throw
   * an exception. Double values are coerced to long (i.e. truncated / rounded) without error checking.
   * 
   * If this object does not have the specified field, return defaultValue.
   */
  public Long getLong(String field, Long defaultValue) {
    if (!containsKey(field))
      return defaultValue;
    
    Object value = get(field);
    if (value instanceof Integer)
      return (long)(int)(Integer)value;
    else if (value instanceof Long)
      return (Long)value;
    else if (value instanceof Double)
      return (long)(double)(Double)value;
    else {
      String s = (String) value;
      return Long.parseLong(s);
    }
  }

  /**
   * Like getLong(field, defaultValue), but if the field is not present, we throw an exception instead of returning
   * a default value.
   */
  public long getLong(String field) {
    if (!containsKey(field))
      throw new RuntimeException("missing field " + field);
    
    return getLong(field, 0L);
  }

  /**
   * Return the specified field.  The field must be either a number, or a string; if a string,
   * we invoke Double.parseDouble.  For fields of other types, or strings which fail parseDouble, we throw an exception.
   * 
   * If this object does not have the specified field, return defaultValue.
   */
  public double getDouble(String field, double defaultValue) {
    if (!containsKey(field))
      return defaultValue;
    
    Object value = get(field);
    if (value instanceof Double)
      return (Double)value;
    else if (value instanceof Long)
      return (Long)value;
    else {
      String s = (String) value;
      return Double.parseDouble(s);
    }
  }

  /**
   * Like getDouble(field, defaultValue), but if the field is not present, we throw an exception instead of returning
   * a default value.
   */
  public double getDouble(String field) {
    if (!containsKey(field))
      throw new RuntimeException("missing field " + field);
    
    return getDouble(field, 0);
  }
  
  /**
   * Return the specified field.  If the field is not a string, we throw an exception.
   * 
   * If this object does not have the specified field, return defaultValue.
   */
  public String getString(String field, String defaultValue) {
    if (!containsKey(field))
      return defaultValue;
    
    Object value = get(field);
    if (value == null || value instanceof String)
      return (String) value;
    else {
      throw new RuntimeException("Attempt to cast [" + value + "] to String");
    }
  }
  
  /**
   * Like getString(field, defaultValue), but if the field is not present, we throw an exception instead of returning
   * a default value.
   */
  public String getString(String field) {
    if (!containsKey(field))
      throw new RuntimeException("missing field " + field);
    
    return (String) get(field);
  }
  
  /**
   * Return the specified field.  If the field is not present, or is not a sub-object, throw an
   * exception.
   */
  public JsonObject getJson(String field) {
    if (!containsKey(field))
      throw new RuntimeException("missing field " + field);
    
    return (JsonObject) get(field);
  }
  
  /**
   * Return the specified field.  If the field is not present, return null.  If it is present
   * but is not a sub-object, throw an exception.
   */
  public JsonObject getJsonOrNull(String field) {
    return (JsonObject) get(field);
  }
  
  /**
   * Return the specified field.  If the field is not present, or is not an array, throw an
   * exception.
   */
  public JsonArray getArray(String field) {
    if (!containsKey(field))
      throw new RuntimeException("missing field " + field);
    
    return (JsonArray) get(field);
  }

  /**
   * Return the specified field.  If the field is not present, return null.  
   */
  public JsonArray getArrayOrNull(String field) {
    return (JsonArray) get(field);
  }
}
