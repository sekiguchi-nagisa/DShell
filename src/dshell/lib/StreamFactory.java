package dshell.lib;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class StreamFactory {
	public static InputStream createInputStream(java.io.InputStream sourceInput, String name, boolean closeable) {
		return new InputStream(sourceInput, name, closeable);
	}

	public static OutputStream createOutputStream(java.io.OutputStream sourceOutput, String name, boolean closeable) {
		return new OutputStream(sourceOutput, name, closeable);
	}

	public static InputStream createStdin() {
		return createInputStream(System.in, "standard input", false);
	}

	public static OutputStream createStdout() {
		return createOutputStream(System.out, "standard output", false);
	}

	public static OutputStream createStderr() {
		return createOutputStream(System.err, "standard error", false);
	}

	private static abstract class Stream {
		protected final boolean closeable;
		private final String streamName;

		protected Stream(String streamName, boolean closeable) {
			this.streamName = streamName;
			this.closeable = closeable;
		}

		@Override
		public String toString() {
			return this.streamName;
		}

		public abstract void close();
	}

	public static class InputStream extends Stream {
		private final BufferedReader reader;

		private InputStream(java.io.InputStream sourceInput, String name, boolean closeable) {
			super(name, closeable);
			this.reader = new BufferedReader(new InputStreamReader(sourceInput));
		}

		@Override
		public void close() {
			if(this.closeable) {
				try {
					this.reader.close();
				}
				catch(IOException e) {
					if(RuntimeContext.getContext().isDebugMode()) {
						e.printStackTrace();
					}
				}
			}
		}

		public String readLine() {
			try {
				return this.reader.readLine();
			}
			catch(IOException e) {
				if(RuntimeContext.getContext().isDebugMode()) {
					e.printStackTrace();
				}
			}
			return null;
		}
	}

	public static class OutputStream extends Stream {
		private final BufferedWriter writer;

		private OutputStream(java.io.OutputStream sourceOutput, String name, boolean closeable) {
			super(name, closeable);
			this.writer = new BufferedWriter(new OutputStreamWriter(sourceOutput));
		}

		@Override
		public void close() {
			if(this.closeable) {
				try {
					this.writer.close();
				}
				catch(IOException e) {
					if(RuntimeContext.getContext().isDebugMode()) {
						e.printStackTrace();
					}
				}
			}
		}

		public void writeLine(String line) {
			try {
				this.writer.write(line);
				this.writer.write("\n");
				this.writer.flush();
			}
			catch(IOException e) {
				if(RuntimeContext.getContext().isDebugMode()) {
					e.printStackTrace();
				}
			}
		}
	}
}
