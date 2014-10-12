package dshell.internal.lib;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Random;
import java.util.TreeSet;

import dshell.lang.Errno;
import dshell.lang.GenericArray;
import dshell.lang.TypeCastException;

/**
 * some utilities.
 * @author skgchxngsxyz-opensuse
 *
 */
public class Utils {
	/**
	 * get full path of command.
	 * @param cmd
	 * @return
	 * - return null, if has no executable command.
	 */
	public final static String getCommandFromPath(String cmd) {
		return getCommandFromPath(cmd, false);
	}

	/**
	 * get full path of command.
	 * @param cmd
	 * @param throwable
	 * @return
	 * if throwable true and command not found or not executable, throw exception.
	 */
	public final static String getCommandFromPath(String cmd, boolean throwable) {
		if(cmd.equals("")) {
			Utils.fatal(1, "empty command name");
			return null;
		}
		String[] paths = getEnv("PATH").split(":");
		for(String path : paths) {
			String fullPath = resolveHome(path + "/" + cmd);
			File file = new File(fullPath);
			if(file.isFile()) {
				if(new File(fullPath).canExecute()) {
					return fullPath;
				} else if(throwable){
					throw new Errno.NotPermittedException(fullPath);
				}
			}
		}
		if(throwable) {
			throw new Errno.FileNotFoundException(cmd);
		}
		return null;
	}

	public final static TreeSet<String> getCommandSetFromPath() {
		return getCommandSetFromPath(false);
	}

	public final static TreeSet<String> getCommandSetFromPath(boolean requireFullPath) {
		TreeSet<String> commandSet = new TreeSet<String>();
		String[] paths = getEnv("PATH").split(":");
		for(String path : paths) {
			path = resolveHome(path);
			File[] files = new File(path).listFiles();
			if(files == null) {
				continue;
			}
			for(File file : files) {
				if(!file.isDirectory() && file.canExecute()) {
					String fileName = file.getName();
					if(requireFullPath) {
						fileName = path + "/" + fileName;
					}
					commandSet.add(fileName);
				}
			}
		}
		return commandSet;
	}

	/**
	 * print message and stack trace before exit.
	 * @param status
	 * - if 0, exit success, otherwise exit failed.
	 * @param message
	 * - not null
	 */
	public final static void fatal(int status, String message) {
		StackTraceElement[] elements = Thread.currentThread().getStackTrace();
		System.err.println("fatal: " + message);
		for(int i = 2; i < elements.length; i++) {
			StackTraceElement element = elements[i];
			System.err.println("\tat " + element);
		}
		System.exit(status);
	}

	public final static void log(String value) {
		System.out.println(value);
		RuntimeContext.getInstance().getLogger().warn(value);
	}

	/**
	 * get environmental variable.
	 * @param key
	 * @return
	 * - if has no env, return empty string.
	 */
	public final static String getEnv(String key) {
		String env = RuntimeContext.getInstance().getenv(key);
		return env == null ? "" : env;
	}

	public final static void setValue(Object targetObject, String fieldName, Object value) {
		try {
			Field field = targetObject.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(targetObject, value);
			field.setAccessible(false);
		}
		catch (Exception e) {
			e.printStackTrace();
			Utils.fatal(1, "field access failed: " + fieldName);
		}
	}

	public final static Object getValue(Object targetObject, String fieldName) {
		try {
			Field field = targetObject.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			Object value = field.get(targetObject);
			field.setAccessible(false);
			return value;
		}
		catch (Exception e) {
			e.printStackTrace();
			Utils.fatal(1, "field access failed: " + fieldName);
		}
		return null;
	}

	/**
	 * replace ~ to home dir path.
	 * @param path
	 * - no null string
	 * @return
	 */
	public final static String resolveHome(String path) {
		if(path.equals("~")) {
			return Utils.getEnv("HOME");
		}
		else if(path.startsWith("~/")) {
			return Utils.getEnv("HOME") + path.substring(1);
		}
		return path;
	}

	public final static String[] splitWithDelim(String targetValue) {	//TODO: support IFS variable
		return targetValue.replaceAll("^[\t\n ]+", "").split("[\t\n ]+");
	}

	public final static String removeNewLine(String value) {
		int size = value.length();
		int endIndex = size;
		for(int i = size - 1; i > -1; i--) {
			char ch = value.charAt(i);
			if(ch != '\n') {
				endIndex = i + 1;
				break;
			}
		}
		return endIndex == size ? value : value.substring(0, endIndex);
	}

	public final static String getUserName() {
		return getEnv("USER");
	}

	/**
	 * print dshell style stack trace message
	 * @param e
	 */
	public final static void printException(InvocationTargetException e) {
		dshell.lang.Exception.wrapException(e.getCause()).printStackTrace();
	}

	public final static void appendStringifiedValue(StringBuilder sb, Object value) {
		if(value == null) {
			sb.append("$null$");
		} else if(value instanceof String) {
			sb.append('"');
			sb.append(value.toString());
			sb.append('"');
		} else {
			sb.append(value.toString());
		}
	}

	public static String readFromFile(String fileName, boolean forceExit) {
		try(FileInputStream input = new FileInputStream(fileName)){
			ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();
			byte[] buffer = new byte[512];
			int readSize = 0;
			while((readSize = input.read(buffer, 0, buffer.length)) > -1) {
				bufferStream.write(buffer, 0, readSize);
			}
			return bufferStream.toString();
		} catch (IOException e) {
			if(forceExit) {
				System.err.println(e.getMessage());
				Utils.fatal(1, "cannot read file: " + fileName);
			}
		}
		return null;
	}

	private final static Random rnd = new Random(System.currentTimeMillis());

	/**
	 * get integer random number
	 * @return
	 */
	public final static int getRandomNum() {
		return rnd.nextInt();
	}

	public final static GenericArray getArgs() {
		String varName = "$ARGS";
		if(!GlobalVariableTable.checkVarExistence(varName)) {
			return new GenericArray();
		}
		return GlobalVariableTable.getObjectVar(varName, GenericArray.class);
	}

	/**
	 * for CastNode
	 * @param value
	 * @param clazz
	 * @return
	 * casted object
	 */
	public final static Object cast(Object value, Class<?> clazz) {
		try {
			return clazz.cast(value);
		} catch(ClassCastException e) {
			throw new TypeCastException(e);
		}
	}
}
