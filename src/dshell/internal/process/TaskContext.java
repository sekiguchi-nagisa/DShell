package dshell.internal.process;

import static dshell.internal.process.TaskConfig.Behavior.background;
import static dshell.internal.process.TaskConfig.Behavior.printable;
import static dshell.internal.process.TaskConfig.Behavior.returnable;
import static dshell.internal.process.TaskConfig.Behavior.throwable;
import static dshell.internal.process.TaskConfig.RetType.IntType;
import static dshell.internal.process.TaskConfig.RetType.TaskType;
import static dshell.internal.process.TaskConfig.RetType.VoidType;

import java.util.ArrayList;
import java.util.List;

import dshell.internal.lib.RuntimeContext;
import dshell.internal.lib.Utils;
import dshell.lang.Task;

public class TaskContext {
	private final List<AbstractProcessContext> procContexts;
	private final TaskConfig config;

	public TaskContext(boolean isBackGround) {
		this.procContexts = new ArrayList<>();
		this.config = new TaskConfig().setFlag(background, isBackGround);
	}

	public TaskContext addContext(AbstractProcessContext context) {
		this.procContexts.add(context);
		return this;
	}

	public TaskContext setOutputBuffer(OutputBuffer buffer) {
		this.config.setOutputBuffer(buffer);
		return this;
	}

	public static AbstractProcessContext createProcessContext(String commandName) {
		if(commandName.indexOf('/') != -1) {	// qualify name
			return new ProcessContext(commandName);
		}
		AbstractProcessContext context = RuntimeContext.getInstance().getBuiltinCommand(commandName);
		if(context != null) {
			return context;
		}
		return new ProcessContext(Utils.getCommandFromPath(commandName, true));	// get qualify name from path
	}

	private Object execTask() {
		/**
		 * launch task.
		 */
		this.procContexts.get(0).setAsFirstProc(true);
		this.procContexts.get(this.procContexts.size() - 1).setAsLastProc(true);
		Task task = new Task(this.procContexts, this.config);
		if(this.config.is(background)) {
			return (this.config.isRetType(TaskType) && this.config.is(returnable)) ? task : null;
		}
		task.join();
		if(this.config.is(returnable)) {
			if(this.config.isRetType(IntType)) {
				return new Long(task.getExitStatus());
			} else if(this.config.isRetType(TaskType)) {
				return task;
			}
		}
		return null;
	}

	// launch task.
	public void execAsVoid() {
		this.config.setRetType(VoidType).setFlag(printable, true).setFlag(throwable, true);
		this.execTask();
	}

	public long execAsInt() {
		this.config.setRetType(IntType).setFlag(printable, true).
		setFlag(returnable, true).setFlag(background, false);
		return ((Long)this.execTask()).longValue();
	}

	public boolean execAsBoolean() {
		return this.execAsInt() == 0;
	}

	public Task execAsTask() {
		this.config.setRetType(TaskType).
		setFlag(printable, true).setFlag(returnable, true).setFlag(throwable, true);
		return (Task) this.execTask();
	}
}
