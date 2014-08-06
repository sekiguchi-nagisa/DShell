package dshell.internal.process;

import static dshell.internal.process.TaskConfig.Behavior.returnable;
import static dshell.internal.process.TaskConfig.Behavior.throwable;
import static dshell.internal.process.TaskConfig.Behavior.timeout;
import static dshell.internal.process.TaskConfig.RetType.TaskType;

import java.io.Serializable;
import java.util.EnumSet;

/**
 * represent task option.
 * @author skgchxngsxyz-opensuse
 *
 */
public class TaskConfig implements Serializable {
	private static final long serialVersionUID = 5651190312973095075L;

	public static enum Behavior {
		returnable,
		printable ,
		throwable ,
		background,
		timeout   ,
	}

	public static enum RetType {
		VoidType   ,
		IntType    ,
		TaskType   ,
	}

	/**
	 * return type of task.
	 */
	private RetType retType;

	/**
	 * set of behaivor flags
	 */
	private final EnumSet<Behavior> flagSet;
	private long time = -1;

	/**
	 * may be null
	 */
	private OutputBuffer buffer;

	public TaskConfig() {
		this.retType = RetType.IntType;
		this.flagSet = EnumSet.noneOf(Behavior.class);
	}

	public boolean isRetType(RetType type) {
		return this.retType == type;
	}

	public boolean is(Behavior optionFlag) {
		return this.flagSet.contains(optionFlag);
	}

	public TaskConfig setRetType(RetType type) {
		this.retType = type;
		return this;
	}

	public TaskConfig setFlag(Behavior optionFlag, boolean set) {
		if(set) {
			this.flagSet.add(optionFlag);
		} else {
			this.flagSet.remove(optionFlag);
		}
		return this;
	}

	public boolean supportStdoutHandler() {
		return this.is(returnable) && (this.isRetType(TaskType) || this.buffer != null);
	}

	public boolean supportStderrHandler() {
		return this.is(throwable) || this.isRetType(TaskType);
	}

	public void setTimeout(String timeSymbol) {
		this.setFlag(timeout, true);
		this.time = Long.parseLong(timeSymbol.toString());
	}

	public long getTimeout() {
		return this.time;
	}

	public void setOutputBuffer(OutputBuffer buffer) {
		this.buffer = buffer;
	}

	/**
	 * 
	 * @return
	 * mye be null
	 */
	public OutputBuffer getOutoutBuffer() {
		return this.buffer;
	}

	@Override
	public String toString() {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append("<");
		sBuilder.append(this.retType.name());
		for(Behavior flag : this.flagSet) {
			sBuilder.append("|");
			sBuilder.append(flag.name());
		}
		sBuilder.append(">");
		return sBuilder.toString();
	}
}
