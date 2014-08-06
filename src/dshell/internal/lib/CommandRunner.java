package dshell.internal.lib;

import dshell.internal.process.AbstractProcessContext;
import dshell.internal.process.TaskConfig;

public class CommandRunner extends AbstractProcessContext {
	private final ExecutableAsCommand executor;
	private CommandContext context;
	private Thread runner;
	private boolean isTerminated;

	public CommandRunner(String commandName, ExecutableAsCommand executor) {
		super(commandName);
		this.executor = executor;
		this.isTerminated = false;
	}

	@Override
	protected AbstractProcessContext mergeErrorToOut() {
		return this;
	}

	@Override
	protected AbstractProcessContext setInputRedirect(String readFileName) {
		return this;
	}

	@Override
	protected AbstractProcessContext setOutputRedirect(int fd, String writeFileName, boolean append) {
		return this;
	}

	@Override
	public AbstractProcessContext start() {
		this.context = new CommandContext(dshell.lang.InputStream.createStdin(), dshell.lang.OutputStream.createStdout(), dshell.lang.OutputStream.createStderr());
		this.runner = new Thread() {
			@Override public void run() {
				executor.execute(context, argList);
			}
		};
		this.runner.start();
		return this;
	}

	@Override
	public void kill() {	// do nothing
	}

	@Override
	public void waitTermination() {
		try {
			this.runner.join();
			this.exitStatus = this.context.getExitStatus();
			this.isTerminated = true;
		}
		catch(InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean checkTermination() {
		return this.isTerminated;
	}

	@Override
	public AbstractProcessContext setStreamBehavior(TaskConfig option) {
		return this;
	}
}
