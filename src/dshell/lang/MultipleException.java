package dshell.lang;

import dshell.annotation.Shared;
import dshell.annotation.SharedClass;
import dshell.annotation.TypeAlias;

@SharedClass("DShellException")
public class MultipleException extends DShellException {	//TODO:
	private static final long serialVersionUID = 164898266354483402L;
	private DShellException[] exceptions;
	transient private GenericArray exceptionArray;

	public MultipleException(String message, DShellException[] exceptions) {
		super(message);
		int size = exceptions.length;
		this.exceptions = new DShellException[size];
		for(int i = 0; i < size; i++) {
			this.exceptions[i] = exceptions[i];
		}
	}

	@Shared
	@TypeAlias("Array<DShellException>")
	public GenericArray getExceptions() {
		if(this.exceptionArray == null) {
			this.exceptionArray = new GenericArray(exceptions);
		}
		return this.exceptionArray;
	}

	@Shared
	@Override
	public String toString() {
		return this.getClass().getSimpleName() + ": " + this.getExceptions().toString();
	}
}
