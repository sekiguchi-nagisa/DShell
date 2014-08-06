package dshell.internal.process;

import static dshell.internal.process.TaskConfig.Behavior.throwable;
import static dshell.internal.process.TaskConfig.Behavior.timeout;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import dshell.internal.lib.Utils;
import dshell.lang.DShellException;
import dshell.lang.Errno;
import dshell.lang.MultipleException;

public class ShellExceptionBuilder {
	public static DShellException getException(final List<AbstractProcessContext> procs, 
			final TaskConfig config, final ByteArrayOutputStream[] eachBuffers) {
		if(!config.is(throwable) || config.is(timeout)) {
			return DShellException.createNullException("");
		}
		List<DShellException> exceptionList = new ArrayList<>();
		int procSize = procs.size();
		for(int i = 0; i < procSize; i++) {
			AbstractProcessContext proc = procs.get(i);
			String errorMessage = eachBuffers[i].toString();
			createAndAddException(exceptionList, proc, errorMessage);
		}
		int size = exceptionList.size();
		if(size == 1) {
			if(!(exceptionList.get(0) instanceof DShellException.NullException)) {
				return exceptionList.get(0);
			}
		}
		else if(size > 1) {
			int count = 0;
			for(DShellException exception : exceptionList) {
				if(!(exception instanceof DShellException.NullException)) {
					count++;
				}
			}
			if(count != 0) {
				return new MultipleException("", exceptionList.toArray(new DShellException[size]));
			}
		}
		return DShellException.createNullException("");
	}

	private static void createAndAddException(List<DShellException> exceptionList, AbstractProcessContext proc, String errorMessage) {
		CauseInferencer inferencer = CauseInferencer_ltrace.getInferencer();
		String message = proc.getCmdName();
		if(proc.hasTraced() || proc.getExitStatus() != 0) {
			DShellException exception;
			if(proc.hasTraced()) {
				List<String> infoList = inferencer.doInference((ProcessContext)proc);
				exception = createException(message, infoList.toArray(new String[infoList.size()]));
			}
			else {
				exception = new DShellException(message);
			}
			exception.setCommand(message);
			exception.setErrorMessage(errorMessage);
			exceptionList.add(exception);
		}
		else {
			exceptionList.add(DShellException.createNullException(message));
		}
		if(proc instanceof ProcessContext) {
			((ProcessContext)proc).deleteLogFile();
		}
	}

	private static DShellException createException(String message, String[] causeInfo) {
		// syscall: syscallName: 0, param: 1, errno: 2
		Class<?>[] types = {String.class};
		Object[] args = {message};
		String errnoString = causeInfo[2];
		if(Errno.SUCCESS.match(errnoString)) {
			return DShellException.createNullException(message);
		}
		if(Errno.LAST_ELEMENT.match(errnoString)) {
			return new DShellException(message);
		}
		Class<?> exceptionClass = Errno.getExceptionClass(errnoString);
		try {
			Constructor<?> constructor = exceptionClass.getConstructor(types);
			Errno.DerivedFromErrnoException exception = (Errno.DerivedFromErrnoException) constructor.newInstance(args);
			exception.setSyscallInfo(causeInfo);
			return exception;
		}
		catch(Throwable t) {
			t.printStackTrace();
			Utils.fatal(1, "Creating Exception failed");
		}
		return null;	// unreachable 
	}
}
