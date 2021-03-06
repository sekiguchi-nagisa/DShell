package dshell.internal.process;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dshell.internal.lib.CommandContext;
import dshell.internal.lib.CommandRunner;
import dshell.internal.lib.ExecutableAsCommand;
import dshell.internal.lib.RuntimeContext;

/**
 * contains builtin command object.
 * @author skgchxngsxyz-opensuse
 *
 */
public class BuiltinCommandHolder {
	private final Map<String, ExecutableAsCommand> builtinCommandMap;

	public BuiltinCommandHolder() {
		this.builtinCommandMap = new HashMap<>();
		this.builtinCommandMap.put(BuiltinSymbol.cd.name(), new Command_cd());
		this.builtinCommandMap.put(BuiltinSymbol.exit.name(), new Command_exit());
		this.builtinCommandMap.put(BuiltinSymbol.help.name(), new Command_help());
		this.builtinCommandMap.put(BuiltinSymbol.log.name(), new Command_log());
	}

	/**
	 * 
	 * @param commandName
	 * @return
	 * throw exception, if has no builtin command.
	 */
	public CommandRunner getCommand(String commandName) {
		ExecutableAsCommand executor = this.builtinCommandMap.get(commandName);
		if(executor == null) {
			return null;
		}
		CommandRunner runner = new CommandRunner(commandName, executor);
		return runner;
	}

	public static void printArgumentErrorAndSetStatus(BuiltinSymbol symbol, CommandContext context) {
		dshell.lang.OutputStream output = context.getStderr();
		output.writeLine("-dshell: " + symbol.name() + ": invalid argument");
		output.writeLine(symbol.name() + ": " + symbol.getUsage());
		context.setExistStatus(1);
	}

	// builtin command implementation.
	public static class Command_cd implements ExecutableAsCommand {
		@Override
		public void execute(CommandContext context, List<String> argList) {
			int size = argList.size();
			String path = "";
			if(size > 1) {
				path = argList.get(1);
			}
			context.setExistStatus(RuntimeContext.getInstance().changeDirectory(path));
		}
	}

	public static class Command_exit implements ExecutableAsCommand {
		@Override
		public void execute(CommandContext context, List<String> argList) {
			int status;
			int size = argList.size();
			if(size == 1) {
				status = 0;
			}
			else if(size == 2) {
				try {
					status = Integer.parseInt(argList.get(1));
				}
				catch(NumberFormatException e) {
					printArgumentErrorAndSetStatus(BuiltinSymbol.exit, context);
					return;
				}
			}
			else {
				printArgumentErrorAndSetStatus(BuiltinSymbol.exit, context);
				return;
			}
			System.exit(status);
		}
	}

	public static class Command_help implements ExecutableAsCommand {
		@Override
		public void execute(CommandContext context, List<String> argList) {
			dshell.lang.OutputStream stdout = context.getStdout();
			dshell.lang.OutputStream stderr = context.getStderr();
			int size = argList.size();
			boolean foundValidCommand = false;
			boolean isShortHelp = false;
			if(size == 1) {
				this.printAllCommandUsage(stdout);
				foundValidCommand = true;
			}
			for(int i = 1; i < size; i++) {
				String arg = argList.get(i);
				if(arg.equals("-s") && size == 2) {
					this.printAllCommandUsage(stdout);
					foundValidCommand = true;
				}
				else if(arg.equals("-s") && i == 1) {
					isShortHelp = true;
				}
				else {
					if(BuiltinSymbol.match(arg)) {
						foundValidCommand = true;
						BuiltinSymbol symbol = BuiltinSymbol.valueOf(arg);
						stdout.writeLine(arg + ": " + symbol.getUsage());
						if(!isShortHelp) {
							stdout.writeLine(symbol.getDetail());
						}
					}
				}
			}
			if(!foundValidCommand) {
				this.printNotMatchedMessage(argList.get(size - 1), stderr);
			}
			context.setExistStatus(foundValidCommand ? 0 : 1);
		}

		private void printAllCommandUsage(dshell.lang.OutputStream stdout) {
			BuiltinSymbol[] symbols = BuiltinSymbol.values();
			for(BuiltinSymbol symbol : symbols) {
				stdout.writeLine(symbol.getUsage());
			}
		}

		private void printNotMatchedMessage(String commandSymbol, dshell.lang.OutputStream stderr) {
			stderr.writeLine("-dshell: help: not no help topics match `" + commandSymbol + "'.  Try `help help'.");
		}
	}

	public static class Command_log implements ExecutableAsCommand {
		@Override
		public void execute(CommandContext context, List<String> argList) {
			int size = argList.size();
			if(size == 1) {
				this.log("", context.getStdout());
				context.setExistStatus(0);
				return;
			}
			if(size != 2) {
				printArgumentErrorAndSetStatus(BuiltinSymbol.log, context);
				return;
			}
			this.log(argList.get(1).toString(), context.getStdout());
			context.setExistStatus(0);
		}

		private void log(String value, dshell.lang.OutputStream stdout) {
			stdout.writeLine(value);
			RuntimeContext.getInstance().getLogger().warn(value);
		}
	}
}
