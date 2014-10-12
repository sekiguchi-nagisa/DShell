package dshell.internal.process;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;

import dshell.internal.lib.Utils;
import dshell.lang.GenericArray;

public class OutputBuffer extends ByteArrayOutputStream implements Serializable {
	private static final long serialVersionUID = -8973407987894592102L;

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
