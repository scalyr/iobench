import java.io.DataOutput;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;

/**
 * Simple utilities for reading or writing blocks of data. This is the innermost step of the benchmark.
 */
public class IO {
	static int bufferMax = 1 << 25;
	long length;
	long fills;
	byte[] mainBuffer;
	byte[] lastBuffer;
	
	IO(long length) {
		this.length = length;
		fills = length / bufferMax;
		mainBuffer = new byte[fills > 0 ? bufferMax : 0];
		lastBuffer = new byte[(int) length % bufferMax];
	}
	void writeRandom(DataOutput stream, Random random) throws IOException {
		for (int i = 0; i < fills; ++i)
			writeRandom(stream, random, mainBuffer);
		writeRandom(stream, random, lastBuffer);
	}
	private void writeRandom(DataOutput stream, Random random, byte[] buffer) throws IOException {
		random.nextBytes(buffer);
		stream.write(buffer);
	}
	void read(RandomAccessFile file) throws IOException {
		for (int i = 0; i < fills; ++i)
			file.read(mainBuffer);
		file.read(lastBuffer);
	}
}
