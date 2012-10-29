import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implements the specific I/O operations to be benchmarked.
 */
interface Operation {
	String fileMode();
	void perform(IO io, RandomAccessFile file, Random rand) throws IOException;
}

class WriteNN implements Operation {
	int n;
	
	public WriteNN(int n) {this.n = n;}
	
  @Override
	public String fileMode() {return "rw";}
	
  @Override
	public void perform(IO io, RandomAccessFile file, Random rand) throws IOException {
		io.writeRandom(file, rand);
		if (rand.nextInt(n) == 0) file.getFD().sync();
	}
	
  @Override
	public String toString() {
		return "write"+n;
	}
}
enum Op implements Operation {
	read("r") {
		@Override
    public void perform(IO io, RandomAccessFile file, Random rand) throws IOException {
			io.read(file);
		}},
	write("rw") {
	    @Override
		public void perform(IO io, RandomAccessFile file, Random rand) throws IOException {
			io.writeRandom(file, rand);
		}},
	writeFlush("rwd") {
	    @Override
		public void perform(IO io, RandomAccessFile file, Random rand) throws IOException {
			io.writeRandom(file, rand);
		}};
	String fileMode;
	Op(String fileMode) {this.fileMode = fileMode;}
	
  @Override
	public String fileMode() {return fileMode;}

	static Pattern pattern = Pattern.compile("write([0-9]+)");
	public static Operation parse(String s) {
		Matcher m = pattern.matcher(s);
		return m.matches() ? new WriteNN(Integer.parseInt(m.group(1))) : valueOf(s);
	}
}
