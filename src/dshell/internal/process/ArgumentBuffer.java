package dshell.internal.process;

import dshell.internal.lib.Utils;
import dshell.lang.GenericArray;

public class ArgumentBuffer {
	private final StringBuilder sBuilder = new StringBuilder();

	public static ArgumentBuffer newBuffer() {
		return new ArgumentBuffer();
	}

	public ArgumentBuffer append(String value) {
		this.sBuilder.append(value);
		return this;
	}

	public String getAsString() {
		return Utils.removeNewLine(this.sBuilder.toString());
	}

	public GenericArray getAsArray() {
		return new GenericArray(Utils.splitWithDelim(this.getAsString()));
	}
}
