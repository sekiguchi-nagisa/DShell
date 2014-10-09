package dshell.lang;

import dshell.annotation.SharedClass;

@SharedClass("Exception")
public class TypeCastException extends Exception {
	private static final long serialVersionUID = -338271634612816218L;

	public TypeCastException(ClassCastException e) {	// not directly call it 
		super(e.getMessage());
		this.setStackTrace(this.recreateStackTrace(e.getStackTrace()));
	}
}
