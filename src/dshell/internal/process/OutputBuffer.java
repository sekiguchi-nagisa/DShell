package dshell.internal.process;

import java.io.ByteArrayOutputStream;

import dshell.internal.lib.Utils;
import dshell.lang.GenericArray;

public class OutputBuffer extends ByteArrayOutputStream {
	public static OutputBuffer newBuffer() {
		return new OutputBuffer();
	}

	public String getMessageString() {
		return Utils.removeNewLine(this.toString());
	}

	public GenericArray getMessageArray() {
		return new GenericArray(Utils.splitWithDelim(this.getMessageString()));
	}
}
