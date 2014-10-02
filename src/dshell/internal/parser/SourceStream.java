package dshell.internal.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.Token;

import dshell.internal.lib.Utils;

public class SourceStream extends ANTLRInputStream {
	/**
	 * new stream from file
	 * @param fileName
	 */
	public SourceStream(String fileName) {
		this(fileName, load(fileName));
	}

	/**
	 * new stream from string
	 * @param sourceName
	 * @param source
	 */
	public SourceStream(String sourceName, String source) {
		this(sourceName, source.toCharArray());
	}

	protected SourceStream(String sourceName, char[] buffer) {
		super(buffer, buffer.length);
		this.name = sourceName;
	}

	protected char[] getBuffer() {
		return this.data;
	}

	public int bufferSize() {
		return this.data.length;
	}

	public char getCharAt(int index) {
		return this.data[index];
	}

	public String getLineText(Token token) {
		int tokenStartPos = token.getStartIndex() + this.getOffset();
		final int size = this.bufferSize();
		if(tokenStartPos >= size) {
			tokenStartPos = size - 1;
		}

		// look up line start pos
		int startIndex;	// include
		for(startIndex = tokenStartPos; startIndex > -1; startIndex--) {
			char ch = this.getCharAt(startIndex);
			if(ch == '\n' || ch == '\r') {
				++startIndex;
				break;
			}
		}
		if(startIndex < 0) {
			startIndex = 0;
		}
		if(startIndex >= size) {
			startIndex = size - 1;
		}

		// look up line end pos
		int stopIndex;
		for(stopIndex = tokenStartPos; stopIndex < size; stopIndex++) {
			char ch = this.getCharAt(stopIndex);
			if(ch == '\n' || ch == '\r') {
				break;
			}
		}
		int lineSize = stopIndex - startIndex;
		if(lineSize < 0) {
			lineSize = 0;
		}
		return new String(this.getBuffer(), startIndex, lineSize);
	}

	public int getOffset() {
		return 0;
	}

	private static char[] load(String fileName) {
		File file = new File(fileName);
		final int size = (int) file.length();
		char[] buffer = new char[size];
		try(InputStreamReader in = new InputStreamReader(new FileInputStream(file))){
			int readSize = in.read(buffer);
			if(readSize < size) {
				buffer = Arrays.copyOf(buffer, readSize);
			}
			return buffer;
		} catch (FileNotFoundException e) {
			System.err.println("file not found: " + fileName);
			System.exit(1);
		} catch (IOException e) {
			Utils.fatal(1, e.getMessage());
		}
		return null;
	}

	/**
	 * for nested parsing
	 * @author skgchxngsxyz-opensuse
	 *
	 */
	public static class ChildStream extends SourceStream {
		private final SourceStream parent;
		private final int startPosInParent;
		/**
		 * create stream from token
		 * @param token
		 * not null
		 * @param startOffset
		 * @param stopOffset
		 */
		public ChildStream(Token token, int startOffset, int stopOffset) {
			super(token.getInputStream().getSourceName(), 
					extractSource(token, startOffset, stopOffset));
			this.parent = (SourceStream) token.getInputStream();
			this.startPosInParent = token.getStartIndex() + startOffset;
		}

		private static String extractSource(Token token, int startOffset, int stopOffset) {
			String tokenText = token.getText();
			return tokenText.substring(startOffset, tokenText.length() - stopOffset);
		}

		@Override
		protected char[] getBuffer() {
			return this.parent.getBuffer();
		}

		@Override
		public int bufferSize() {
			return this.parent.bufferSize();
		}

		@Override
		public char getCharAt(int index) {
			return this.parent.getCharAt(index);
		}

		@Override
		public int getOffset() {
			return this.parent.getOffset() + this.startPosInParent;
		}
	}
}
