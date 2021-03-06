package dshell.internal.process;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import dshell.internal.lib.Utils;
import dshell.internal.parser.ParserUtils.RedirOption;

/**
 * represents process.
 * @author skgchxngsxyz-opensuse
 *
 */
public abstract class AbstractProcessContext {
	public static final int STDOUT_FILENO = 1;
	public static final int STDERR_FILENO = 2;

	/**
	 * reference of standard input of created process.
	 */
	protected OutputStream stdin = null;

	/**
	 * reference of standard output of created process.
	 */
	protected InputStream stdout = null;

	/**
	 * reference of standard error of created process.
	 */
	protected InputStream stderr = null;

	/**
	 * also contains command path. actually, it is LinkedList.
	 */
	protected final List<String> argList;

	/**
	 * if true, standard input has already used.
	 */
	protected boolean stdinIsDirty = false;

	/**
	 * if true, standard output has already used.
	 */
	protected boolean stdoutIsDirty = false;

	/**
	 * if true, standard error has already used.
	 */
	protected boolean stderrIsDirty = false;

	/**
	 * if true, this context behaves as first process.
	 */
	protected boolean isFirstProc = false;

	/**
	 * if true, this context behaves as last process.
	 */
	protected boolean isLastProc = false;

	/**
	 * exit status of process launched by this context.
	 */
	protected int exitStatus = 0;

	/**
	 * for represent string
	 */
	protected final StringBuilder cmdBuilder;

	protected AbstractProcessContext(String commandPath) {
		this.argList = new LinkedList<>();
		this.argList.add(commandPath);
		this.cmdBuilder = new StringBuilder();
		this.cmdBuilder.append(commandPath);
	}

	/**
	 * add argument string. if argument string is empty string, skip it.
	 * @param argBuilder
	 * @return
	 */
	public AbstractProcessContext addArg(ArgumentBuilder argBuilder) {
		LinkedList<String> argList = argBuilder.getArgList();
		for(String arg : argList) {
			if(arg.equals("")) {	// skip empty string
				continue;
			}
			String resolvedArg = Utils.resolveHome(arg);
			this.argList.add(resolvedArg);
			this.cmdBuilder.append(' ');
			this.cmdBuilder.append(resolvedArg);
		}
		return this;
	}

	public abstract AbstractProcessContext setStreamBehavior(TaskConfig option);

	protected abstract AbstractProcessContext setInputRedirect(String readFileName);
	protected abstract AbstractProcessContext setOutputRedirect(int fd, String writeFileName, boolean append);
	protected abstract AbstractProcessContext mergeErrorToOut();

	/**
	 * 
	 * @param option
	 * - must be RedirectOption's element
	 * @param targetFileName
	 * - not null. if option is not require target file name, contains empty string
	 * @return
	 * - this
	 */
	public final AbstractProcessContext setRedirOption(int option, ArgumentBuilder targetArg) {
		LinkedList<String> argList = targetArg.getArgList();
		String targetFileName = argList.removeFirst();
		for(String arg : argList) {
			this.argList.add(arg);
		}

		switch(option) {
		case RedirOption.FromFile:
			this.setInputRedirect(targetFileName);
			this.cmdBuilder.append(" < ");
			this.cmdBuilder.append(targetFileName);
			break;
		case RedirOption.To1File:
			this.setOutputRedirect(STDOUT_FILENO, targetFileName, false);
			this.cmdBuilder.append(" > ");
			this.cmdBuilder.append(targetFileName);
			break;
		case RedirOption.To1FileAppend:
			this.setOutputRedirect(STDOUT_FILENO, targetFileName, true);
			this.cmdBuilder.append(" >> ");
			this.cmdBuilder.append(targetFileName);
			break;
		case RedirOption.To2File:
			this.setOutputRedirect(STDERR_FILENO, targetFileName, false);
			this.cmdBuilder.append(" 2> ");
			this.cmdBuilder.append(targetFileName);
			break;
		case RedirOption.To2FileAppend:
			this.setOutputRedirect(STDERR_FILENO, targetFileName, true);
			this.cmdBuilder.append(" 2>> ");
			this.cmdBuilder.append(targetFileName);
			break;
		case RedirOption.Merge2To1:
			this.mergeErrorToOut();
			this.cmdBuilder.append("2>&1");
			break;
		case RedirOption.ToFileAnd:
			this.mergeErrorToOut().setOutputRedirect(STDOUT_FILENO, targetFileName, false);
			this.cmdBuilder.append(" &> ");
			this.cmdBuilder.append(targetFileName);
			break;
		case RedirOption.AndToFileAppend:
			this.mergeErrorToOut().setOutputRedirect(STDOUT_FILENO, targetFileName, true);
			this.cmdBuilder.append(" &>> ");
			this.cmdBuilder.append(targetFileName);
			break;
		}
		return this;
	}

	/**
	 * enable system call trace.
	 * @return
	 * - this.
	 */
	public AbstractProcessContext enableTrace() {
		return this;
	}

	/**
	 * create and start new process.
	 * must call it only once.
	 * @return
	 * - this.
	 */
	public abstract AbstractProcessContext start();

	/**
	 * kill created prcoess.
	 */
	public abstract void kill();

	public abstract void waitTermination();

	/**
	 * 
	 * @return
	 * return true, if created process has already terminated.
	 */
	public abstract boolean checkTermination();

	/**
	 * redirect srcContex's standard output to this context's standard input.
	 * @param srcConext
	 */
	public void pipe(AbstractProcessContext srcConext) {
		new PipeStreamHandler(srcConext.accessOutStream(), this.accessInStream(), true).start();
	}

	public void setAsFirstProc(boolean isFirstProc) {
		this.isFirstProc = isFirstProc;
	}

	public void setAsLastProc(boolean isLastProc) {
		this.isLastProc = isLastProc;
	}

	/**
	 * get standard input of created process.
	 * @return
	 * - return NullOutputStream, if cannot access.
	 */
	public OutputStream accessInStream() {
		if(!this.stdinIsDirty) {
			this.stdinIsDirty = true;
			return this.stdin;
		}
		return new PipeStreamHandler.NullOutputStream();
	}

	/**
	 * get standard output of created process.
	 * @return
	 * - return NullInoutStream, if cannot access.
	 */
	public InputStream accessOutStream() {
		if(!this.stdoutIsDirty) {
			this.stdoutIsDirty = true;
			return this.stdout;
		}
		return new PipeStreamHandler.NullInputStream();
	}

	/**
	 * get standard error of created process.
	 * @return
	 * - return NullInoutStream, if cannot access.
	 */
	public InputStream accessErrorStream() {
		if(!this.stderrIsDirty) {
			this.stderrIsDirty = true;
			return this.stderr;
		}
		return new PipeStreamHandler.NullInputStream();
	}

	/**
	 * get exit status of created process.
	 * @return
	 */
	public int getExitStatus() {
		return this.exitStatus;
	}

	public String getCmdName() {
		return this.toString();
	}

	@Override
	public String toString() {
		return this.cmdBuilder.toString();
	}

	/**
	 * 
	 * @return
	 * - if true, system call trace is enabled.
	 */
	public boolean hasTraced() {
		return false;
	}
}
