package dshell.internal.process;

import java.util.LinkedList;

import dshell.lang.BooleanArray;
import dshell.lang.FloatArray;
import dshell.lang.GenericArray;
import dshell.lang.IntArray;

public class ArgumentBuilder {	//TODO:
	private final LinkedList<String> argList = new LinkedList<>();

	public static ArgumentBuilder newArgumentBuilder() {
		return new ArgumentBuilder();
	}

	public ArgumentBuilder append(String value) {
		if(this.argList.size() == 0) {
			this.argList.add(value);
		} else {
			String argSeg = this.argList.removeLast();
			this.argList.add(argSeg + value);
		}
		return this;
	}

	public ArgumentBuilder append(GenericArray value) {
		final int size = (int) value.size();
		int startIndex = 0;
		if(this.argList.size() > 0 && size > 0) {
			String argSeg = this.argList.removeLast();
			this.argList.add(argSeg + value.get(startIndex++));
		}
		for(int i = startIndex; i < size; i++) {
			this.argList.add(value.get(i).toString());
		}
		return this;
	}

	public ArgumentBuilder append(IntArray value) {
		final int size = (int) value.size();
		int startIndex = 0;
		if(this.argList.size() > 0 && size > 0) {
			String argSeg = this.argList.removeLast();
			this.argList.add(argSeg + value.get(startIndex++));
		}
		for(int i = startIndex; i < size; i++) {
			this.argList.add(Long.toString(value.get(i)));
		}
		return this;
	}

	public ArgumentBuilder append(FloatArray value) {
		final int size = (int) value.size();
		int startIndex = 0;
		if(this.argList.size() > 0 && size > 0) {
			String argSeg = this.argList.removeLast();
			this.argList.add(argSeg + value.get(startIndex++));
		}
		for(int i = startIndex; i < size; i++) {
			this.argList.add(Double.toString(value.get(i)));
		}
		return this;
	}

	public ArgumentBuilder append(BooleanArray value) {
		final int size = (int) value.size();
		int startIndex = 0;
		if(this.argList.size() > 0 && size > 0) {
			String argSeg = this.argList.removeLast();
			this.argList.add(argSeg + value.get(startIndex++));
		}
		for(int i = startIndex; i < size; i++) {
			this.argList.add(Boolean.toString(value.get(i)));
		}
		return this;
	}

	public LinkedList<String> getArgList() {
		return this.argList;
	}
}
