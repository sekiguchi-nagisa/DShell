package dshell.internal.process;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.util.Calendar;

import dshell.internal.lib.Utils;

public class ProcessContext extends AbstractProcessContext {
	public final static int traceBackend_ltrace = 0;
	public static int traceBackendType = traceBackend_ltrace;

	private final static String logDirPath = "/tmp/dshell-trace-log";
	private static int logId = 0;

	private final ProcessBuilder procBuilder;
	private Process proc;
	public boolean isKilled = false;
	private boolean enableTrace = false;
	public String logFilePath = null;

	private static String createLogNameHeader() {
		Calendar cal = Calendar.getInstance();
		StringBuilder logNameHeader = new StringBuilder();
		logNameHeader.append(cal.get(Calendar.YEAR) + "-");
		logNameHeader.append((cal.get(Calendar.MONTH) + 1) + "-");
		logNameHeader.append(cal.get(Calendar.DATE) + "-");
		logNameHeader.append(cal.get((Calendar.HOUR) + 1) + ":");
		logNameHeader.append(cal.get(Calendar.MINUTE) + "-");
		logNameHeader.append(cal.get(Calendar.MILLISECOND));
		logNameHeader.append("-" + logId++);
		return logNameHeader.toString();
	}

	private static boolean checkTraceRequirements() {
		boolean tracable = false;
		if(System.getProperty("os.name").equals("Linux")) {
			ProcessContext.traceBackendType = ProcessContext.traceBackend_ltrace;
			tracable = Utils.getCommandFromPath("ltrace") != null;
		}
		if(!tracable) {
			System.err.println("Systemcall Trace is Not Supported");
		}
		return tracable;
	}

	public ProcessContext(String commandPath) {
		super(commandPath);
		this.procBuilder = new ProcessBuilder(this.argList);
		this.procBuilder.redirectError(Redirect.INHERIT);
	}

	@Override
	public AbstractProcessContext enableTrace() {
		this.enableTrace = checkTraceRequirements();
		if(this.enableTrace) {
			logFilePath = logDirPath + "/" + createLogNameHeader() + ".log";
			new File(logDirPath).mkdir();
			String[] traceCmds;
			if(traceBackendType == traceBackend_ltrace) {
				traceCmds = new String[] {"ltrace", "-f", "-S", "-o", logFilePath};
			}
			else {
				Utils.fatal(1, "invalid trace backend type");
				return null;	// unreachable
			}
			final int size = traceCmds.length;
			for(int i = 0 ; i < size; i++) {
				this.argList.add(i, traceCmds[i]);
			}
		}
		return this;
	}

	@Override
	public AbstractProcessContext start() {
		try {
			this.proc = this.procBuilder.start();
			this.stdin = this.proc.getOutputStream();
			this.stdout = this.proc.getInputStream();
			this.stderr = this.proc.getErrorStream();
		}
		catch(IOException e) {
			throw dshell.lang.Exception.wrapException(e);
		}
		return this;
	}

	@Override
	public AbstractProcessContext setStreamBehavior(TaskConfig config) {
		if(this.isFirstProc) {
			if(this.procBuilder.redirectInput().file() == null) {
				this.procBuilder.redirectInput(Redirect.INHERIT);
				this.stdinIsDirty = true;
			}
		}
		if(this.isLastProc) {
			if(this.procBuilder.redirectOutput().file() == null && !config.supportStdoutHandler()) {
				this.procBuilder.redirectOutput(Redirect.INHERIT);
				this.stdoutIsDirty = true;
			}
		}
		if(this.procBuilder.redirectError().file() == null && config.supportStderrHandler()) {
			this.procBuilder.redirectError(Redirect.PIPE);
			this.stderrIsDirty = false;
		}
		return this;
	}

	@Override
	protected AbstractProcessContext setInputRedirect(String readFileName) {
		this.stdinIsDirty = true;
		this.procBuilder.redirectInput(new File(readFileName.toString()));
		return this;
	}

	@Override
	protected AbstractProcessContext setOutputRedirect(int fd, String writeFileName, boolean append) {
		File file = new File(writeFileName.toString());
		Redirect redirDest = Redirect.to(file);
		if(append) {
			redirDest = Redirect.appendTo(file);
		}
		if(fd == STDOUT_FILENO) {
			this.stdoutIsDirty = true;
			this.procBuilder.redirectOutput(redirDest);
		} else if(fd == STDERR_FILENO) {
			this.stderrIsDirty = true;
			this.procBuilder.redirectError(redirDest);
		}
		return this;
	}

	@Override
	protected AbstractProcessContext mergeErrorToOut() {
		this.procBuilder.redirectErrorStream(true);
		this.stderrIsDirty = true;
		return this;
	}

	@Override
	public void waitTermination() {
		try {
			this.exitStatus = this.proc.waitFor();
		} catch(InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void kill() {
		if(System.getProperty("os.name").startsWith("Windows")) {
			this.proc.destroy();
			return;
		}
		try {
			int pid = (Integer) Utils.getValue(this.proc, "pid");
			String[] cmds = {"kill", "-9", Integer.toString(pid)};
			Process procKiller = new ProcessBuilder(cmds).start();
			procKiller.waitFor();
			this.isKilled = true;
		} catch(Exception e) {
			e.printStackTrace();
			Utils.fatal(1, "killing process problem");
		}
	}

	public boolean checkTermination() {
		try {
			this.exitStatus = this.proc.exitValue();
			return true;
		} catch(IllegalThreadStateException e) {
			return false;
		}
	}

	public String getLogFilePath() {
		return this.logFilePath;
	}

	public void deleteLogFile() {
		if(this.logFilePath != null) {
			new File(this.logFilePath).delete();
		}
	}

	@Override
	public boolean hasTraced() {
		return this.enableTrace;
	}
}