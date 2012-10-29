import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for describing file sizes.
 */
public class Size {
	static String multipliers = "_KMGTP";
	static Pattern pattern = Pattern.compile("([0-9]+)(["+multipliers+"]?)B?");
	public static Size parse(String spec) {
		Matcher m = pattern.matcher(spec);
		if (!m.matches())
			throw new IllegalArgumentException("unrecognised size " + spec);
		return new Size(Long.parseLong(m.group(1)) << (multipliers.indexOf(m.group(2))*10));
	}

	public final long bytes;
	public Size(long bytes) {
		this.bytes = bytes;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (bytes ^ (bytes >>> 32));
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Size other = (Size) obj;
		if (bytes != other.bytes)
			return false;
		return true;
	}
	
	/**
	 * Return this value in the most compact precise terminology,
	 * e.g. 32768 is returned as "32K", but 32767 is simply "32767". 
	 */
	@Override public String toString() {
	  for (int i = multipliers.length() - 1; i > 0; i--) {
	    long unitBytes = 1L << (10 * i);
	    if (bytes % unitBytes == 0)
	      return bytes / unitBytes + multipliers.substring(i, i+1);
	  }
	  
	  return Long.toString(bytes);
	}
}
