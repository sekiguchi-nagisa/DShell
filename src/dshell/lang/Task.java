package dshell.lang;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import dshell.annotation.Shared;
import dshell.annotation.SharedClass;
import dshell.internal.lib.Utils;
import dshell.internal.process.AbstractProcessContext;
import dshell.internal.process.PipeStreamHandler.EmptyErrorStreamHandler;
import dshell.internal.process.PipeStreamHandler.ErrorStreamHandler;
import dshell.internal.process.PipeStreamHandler.ErrorStreamHandlerImpl;
import dshell.internal.process.ShellExceptionBuilder;
import dshell.internal.process.TaskConfig;
import dshell.internal.process.PipeStreamHandler.EmptyOutputStreamHandler;
import dshell.internal.process.PipeStreamHandler.OutputStreamHandlerImpl;
import dshell.internal.process.PipeStreamHandler.OutputStreamHandler;
import static dshell.internal.process.TaskConfig.Behavior.background;
import static dshell.internal.process.TaskConfig.Behavior.printable;
import static dshell.internal.process.TaskConfig.Behavior.throwable;

@SharedClass
public class Task implements Serializable {
	private static final long serialVersionUID = 7531968866962967914L;

	transient private Thread stateMonitor;
	transient private final List<AbstractProcessContext> procContexts;
	transient private final TaskConfig config;
	transient private OutputStreamHandler stdoutHandler;
	transient private ErrorStreamHandler stderrHandler;

	private boolean terminated = false;
	private String stdoutMessage;
	private String stderrMessage;
	private List<Integer> exitStatusList;
	private DShellException exception = DShellException.createNullException("");

	public Task(List<AbstractProcessContext> procContexts, TaskConfig option) {
		this.procContexts = procContexts;
		this.config = option;
		// start task
		int size = this.procContexts.size();
		this.procContexts.get(0).setStreamBehavior(this.config).start();
		for(int i = 1; i < size; i++) {
			this.procContexts.get(i).setStreamBehavior(this.config)
									.start().pipe(this.procContexts.get(i - 1));
		}
		// start message handler
		// stdout
		this.stdoutHandler = this.createStdoutHandler();
		this.stdoutHandler.startHandler();
		// stderr
		this.stderrHandler = this.createStderrHandler();
		this.stderrHandler.startHandler();
		// start state monitor
		if(!option.is(background)) {
			return;
		}
		this.stateMonitor = new Thread() {
			@Override public void run() {
				if(timeoutIfEnable()) {
					return;
				}
				while(true) {
					if(checkTermination()) {
						System.err.println("Terminated Task: " + getRepresentString());
						// run exit handler
						return;
					}
					try {
						Thread.sleep(100); // sleep thread
					} catch (InterruptedException e) {
						System.err.println(e.getMessage());
						Utils.fatal(1, "interrupt problem");
					}
				}
			}
		};
		this.stateMonitor.start();
	}

	private OutputStreamHandler createStdoutHandler() {	//FIXME: refactoring
		if(!this.config.supportStdoutHandler()) {
			return EmptyOutputStreamHandler.getHandler();
		}
		OutputStream stdoutStream = null;
		if(this.config.is(printable) && this.config.getOutoutBuffer() == null) {
			stdoutStream = System.out;
		}
		AbstractProcessContext LastContext = this.procContexts.get(this.procContexts.size() - 1);
		return new OutputStreamHandlerImpl(LastContext.accessOutStream(), stdoutStream, this.config.getOutoutBuffer());
	}

	private ErrorStreamHandler createStderrHandler() {
		if(!this.config.supportStderrHandler()) {
			return EmptyErrorStreamHandler.getHandler();
		}
		int size = this.procContexts.size();
		InputStream[] srcErrorStreams = new InputStream[size];
		for(int i = 0; i < size; i++) {
			srcErrorStreams[i] = this.procContexts.get(i).accessErrorStream();
		}
		return new ErrorStreamHandlerImpl(srcErrorStreams);
	}

	private void joinAndSetException() {
		this.terminated = true;
		if(!config.is(background)) {
			if(!this.timeoutIfEnable()) {
				this.waitTermination();
			}
		} else {
			try {
				stateMonitor.join();
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		this.stdoutMessage = this.stdoutHandler.waitTermination();
		this.stderrMessage = this.stderrHandler.waitTermination();
		this.exitStatusList = new ArrayList<Integer>();
		for(AbstractProcessContext proc : this.procContexts) {
			this.exitStatusList.add(proc.getExitStatus());
		}
		// exception raising
		this.exception = ShellExceptionBuilder.getException(this.procContexts, 
				this.config, this.stderrHandler.getEachBuffers());
	}

	public void join() {
		if(this.terminated) {
			return;
		}
		this.joinAndSetException();
		if(this.config.is(throwable) && !(this.exception instanceof DShellException.NullException)) {
			throw this.exception;
		}
	}

	/**
	 * get standard output message
	 * @return
	 */
	@Shared
	public String getOutput() {
		this.join();
		return this.stdoutMessage;
	}

	/**
	 * get standard error message
	 * @return
	 */
	@Shared
	public String getErrorMessage() {
		this.join();
		return this.stderrMessage;
	}

	/**
	 * get exist status of last process
	 * @return
	 */
	@Shared
	public long getExitStatus() {
		this.join();
		return this.exitStatusList.get(this.exitStatusList.size() - 1);
	}

	@Shared
	@Override
	public String toString() {
		StringBuilder sBuilder = new StringBuilder();
		int count = 0;
		for(AbstractProcessContext proc : this.procContexts) {
			if(count++ > 0) {
				sBuilder.append(" | ");
			}
			sBuilder.append(proc.toString());
		}
		if(this.config.is(background)) {
			sBuilder.append(" &");
		}
		return sBuilder.toString();
	}

	private String getRepresentString() {
		return this.toString();
	}

	private boolean timeoutIfEnable() {
		long timeout = this.config.getTimeout();
		if(timeout > 0) { // timeout
			try {
				Thread.sleep(timeout);	// ms
				StringBuilder msgBuilder = new StringBuilder();
				msgBuilder.append("Timeout Task: " + this.toString());
				this.kill();
				System.err.println(msgBuilder.toString());
				// run exit handler
				return true;
			} catch (InterruptedException e) {
				e.printStackTrace();
				Utils.fatal(1, "interrupt problem");
			}
		}
		return false;
	}

	private void kill() {
		for(AbstractProcessContext proc : this.procContexts) {
			proc.kill();
		}
	}

	private void waitTermination() {
		for(AbstractProcessContext proc : this.procContexts) {
			proc.waitTermination();
		}
	}

	/**
	 * check termination of processes.
	 * @return
	 * - if all of processes have already terminated, return true.
	 */
	private boolean checkTermination() {
		for(AbstractProcessContext proc : this.procContexts) {
			if(!proc.checkTermination()) {
				return false;
			}
		}
		return true;
	}
}
