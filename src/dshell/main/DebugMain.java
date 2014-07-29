package dshell.main;

/**
 * main function for debugger (eclipse, etc...)
 * @author skgchxngsxyz-opensuse
 *
 */
public class DebugMain {
	public static void main(String[] args) {
		new DShell(args, true).execute();
	}
}

