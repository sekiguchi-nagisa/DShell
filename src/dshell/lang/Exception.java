package dshell.lang;

import java.util.LinkedList;

import dshell.annotation.Shared;
import dshell.annotation.SharedClass;
import dshell.internal.lib.RuntimeContext;
import dshell.internal.type.TypePool;

/**
 * D-shell basis exception class
 * @author skgchxngsxyz-osx
 *
 */
@SharedClass
public class Exception extends RuntimeException {
	private static final long serialVersionUID = -8494693504521057747L;

	public static Exception wrapException(Throwable t) {
		if(t instanceof Exception) {
			return (Exception) t;
		}
		return new NativeException(t);
	}

	@Shared
	public Exception() {
		super("");
	}

	@Shared
	public Exception(String message) {
		super(message);
	}

	/**
	 * used for native exception.
	 * @param cause
	 */
	protected Exception(Throwable cause) {
		super(cause);
	}

	@Shared
	@Override
	public String toString() {
		return this.getClass().getSimpleName();
	}

	@Shared
	@Override
	public String getMessage() {
		return super.getMessage();
	}

	@Shared
	@Override
	public void printStackTrace() {
		StringBuilder sBuilder = new StringBuilder();
		this.createHeader(sBuilder);
		for(StackTraceElement element : this.getStackTrace()) {
			sBuilder.append("\tfrom ");
			sBuilder.append(element.getFileName());
			sBuilder.append(":");
			sBuilder.append(element.getLineNumber());
			sBuilder.append(" '");
			sBuilder.append(this.formateName(element));
			sBuilder.append("'\n");
		}
		System.err.print(sBuilder.toString());
	}

	@Override
	public Throwable fillInStackTrace() {
		super.fillInStackTrace();
		this.setStackTrace(this.recreateStackTrace(super.getStackTrace()));
		return this;
	}

	protected StackTraceElement[] recreateStackTrace(StackTraceElement[] originalElements) {
		LinkedList<StackTraceElement> elementStack = new LinkedList<StackTraceElement>();
		boolean foundNativeMethod = false;
		for(int i = originalElements.length - 1; i > -1; i--) {
			StackTraceElement element = originalElements[i];
			if(!foundNativeMethod && element.isNativeMethod()) {
				foundNativeMethod = true;
				continue;
			}
			if(foundNativeMethod && element.getClassName().startsWith(TypePool.generatedPackage.replace('/', '.'))) {
				elementStack.add(element);
			}
		}
		int size = elementStack.size();
		StackTraceElement[] elements = new StackTraceElement[size];
		for(int i = 0; i < size; i++) {
			elements[i] = elementStack.pollLast();
		}
		return elements;
	}

	protected void createHeader(StringBuilder sBuilder) {
		String message = this.getMessage();
		message = (message == null ? "" : message);
		sBuilder.append(this.toString() +  ": " + message + "\n");
	}

	private String formateName(StackTraceElement element) {
		String fullyQualifiedClassName = element.getClassName();
		int index = fullyQualifiedClassName.lastIndexOf('.');
		String className = fullyQualifiedClassName.substring(index + 1);
		if(fullyQualifiedClassName.replace('.', '/').startsWith(TypePool.toplevelClassName)) {
			return "<toplevel>()";
//		} else if(fullyQualifiedClassName.startsWith("dshell.defined.class")) { //TODO:
			
		} else if(fullyQualifiedClassName.replace('.', '/').startsWith(TypePool.generatedFuncPackage)) {
			int prefixIndex = className.indexOf('_');
			return "function " + className.substring(prefixIndex + 1) + "()";
		}
		return "unknown";
	}

	/**
	 * wrapper class for java native exception.
	 * @author skgchxngsxyz-opensuse
	 *
	 */
	private static final class NativeException extends Exception {
		private static final long serialVersionUID = 7696635521352542680L;

		private NativeException(Throwable cause) {
			super(cause);
			this.setStackTrace(this.recreateStackTrace(this.getCause().getStackTrace()));
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName() + " -> " + this.getCause().getClass().getCanonicalName();
		}

		@Override
		protected void createHeader(StringBuilder sBuilder) {
			String message = this.getCause().getMessage();
			message = (message == null ? "" : message);
			sBuilder.append(this.toString() +  ": " + message + "\n");
		}

		@Override
		public void printStackTrace() {
			super.printStackTrace();
			if(RuntimeContext.getInstance().isDebugMode()) {
				this.getCause().printStackTrace();
			}
		}

		@Override
		public String getMessage() {
			String message = this.getCause().getMessage();
			return message == null ? "" : message;
		}
	}
}
