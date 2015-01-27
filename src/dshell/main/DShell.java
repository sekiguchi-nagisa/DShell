package dshell.main;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import dshell.internal.console.AbstractConsole;
import dshell.internal.console.DShellConsole;
import dshell.internal.exe.EngineFactory;
import dshell.internal.exe.ExecutionEngine;
import dshell.internal.exe.ExecutionEngine.EngineConfig;
import dshell.internal.exe.DShellEngineFactory;
import dshell.internal.lib.RuntimeContext;
import dshell.internal.lib.Utils;
import dshell.main.ArgsParser.OptionListener;
import static dshell.internal.lib.RuntimeContext.AppenderType;

public class DShell {
	public final static String progName  = "D-Shell";
	public final static String codeName  = "Reference Implementation of D-Script";
	public final static int majorVersion = 0;
	public final static int minerVersion = 4;
	public final static int patchLevel   = 4;
	public final static String version = majorVersion + "." + minerVersion + "." + patchLevel;
	public final static String copyright = "Copyright (c) 2013-2014, Konoha project authors";
	public final static String license = "BSD-Style Open Source";
	public final static String shellInfo = progName + ", version " + version + " (Java -" + System.getProperty("java.version") + ")";

	public static enum ExecutionMode {
		interactiveMode,
		scriptingMode,
		inputEvalMode,
	}

	protected ExecutionMode mode;
	private final boolean enablePseudoTerminal;
	private final EngineConfig config;
	private String specificArg = null;
	protected String[] scriptArgs;

	public DShell(String[] args) {
		this(args, false);
	}

	public DShell(String[] args, boolean enablePseudoTerminal) {
		this.enablePseudoTerminal = enablePseudoTerminal;
		this.config = new EngineConfig();
		this.resolveOption(args);
	}

	protected void resolveOption(String[] args) {
		this.mode = ExecutionMode.scriptingMode;

		final ArgsParser parser = new ArgsParser();

		// set option rule
		parser
		.addOption("--version", new OptionListener() {
			@Override public void invoke(String arg) {
				showVersionInfo();
				System.exit(0);
			}
		})
		.addOption("--help", new OptionListener() {
			@Override public void invoke(String arg) {
				showHelpAndExit(0, System.out, parser);
			}
		})
		.addOption("--debug", new OptionListener() {
			@Override public void invoke(String arg) {
				RuntimeContext.getInstance().setDebugMode(true);
			}
		})
		.addOption("--inspect-parser", new OptionListener() {
			@Override public void invoke(String arg) {
				config.enableParserInspect();
			}
		})
		.addOption("--trace-parser", new OptionListener() {
			@Override public void invoke(String arg) {
				config.enableParserTrace();
			}
		})
		.addOption("--dump-ast", new OptionListener() {
			@Override public void invoke(String arg) {
				config.enableASTDump();
			}
		})
		.addOption("--dump-bytecode", new OptionListener() {
			@Override public void invoke(String arg) {
				config.enableByteCodeDump();
			}
		})
		.addOption("--only-parsing", new OptionListener() {
			@Override public void invoke(String arg) {
				config.enableOnlyParsing();
			}
		})
		.addOption("--disable-assertion", new OptionListener() {
			@Override public void invoke(String arg) {
				config.disableAssertion();
			}
		})
		.addOption("--logging=file", true, new OptionListener() {
			@Override public void invoke(String arg) {
				RuntimeContext.getInstance().changeAppender(AppenderType.file, arg);
			}
		})
		.addOption("--logging=stdout", new OptionListener() {
			@Override public void invoke(String arg) {
				RuntimeContext.getInstance().changeAppender(AppenderType.stdout);
			}
		})
		.addOption("--logging=stderr", new OptionListener() {
			@Override public void invoke(String arg) {
				RuntimeContext.getInstance().changeAppender(AppenderType.stderr);
			}
		})
		.addOption("--logging=syslog", true, new OptionListener() {
			@Override public void invoke(String arg) {
				RuntimeContext.getInstance().changeAppender(AppenderType.syslog, arg);
			}
		})
		.addOption("-c", true, new OptionListener() {
			@Override public void invoke(String arg) {
				mode = ExecutionMode.inputEvalMode;
				specificArg = arg;
			}
		});

		try {
			this.scriptArgs = parser.parse(args).getRestArgs();
			if(!this.enablePseudoTerminal && !RuntimeContext.getInstance().isatty(0)) {
				this.mode = ExecutionMode.inputEvalMode;
			}
			if(this.mode == ExecutionMode.scriptingMode && this.scriptArgs.length == 0) {
				this.mode = ExecutionMode.interactiveMode;
			}
		} catch(IllegalArgumentException e) {
			System.err.println("dshell: " + e.getMessage());
			this.showHelpAndExit(1, System.err, parser);
		}
	}


	public void execute() {
		RuntimeContext.getInstance();
		EngineFactory factory = new DShellEngineFactory();
		ExecutionEngine engine = factory.getEngine();
		engine.setConfig(this.config);
		switch(this.mode) {
		case interactiveMode:
			this.runInteractiveMode(engine, new DShellConsole());	// never return
		case scriptingMode:
			this.runScriptingMode(engine);	// never return
		case inputEvalMode:
			this.runInputEvalMode(engine);	// never return
		}
	}

	protected void runInteractiveMode(ExecutionEngine engine, AbstractConsole console) {
		String line = null;
		this.showVersionInfo();
		engine.loadDShellRC();
		while((line = console.readLine()) != null) {
			if(line.equals("")) {
				continue;
			}
			engine.eval(line, console.getLineNumber());
			console.incrementLineNum(line);
		}
		System.out.println("");
		System.exit(0);
	}

	protected void runScriptingMode(ExecutionEngine engine) {
		engine.setArg(this.scriptArgs);
		boolean status = engine.eval(this.scriptArgs[0]);
		System.exit(status ? 0 : 1);
	}

	protected void runInputEvalMode(ExecutionEngine engine) {
		String[] actualArgs = new String[this.scriptArgs.length + 1];
		actualArgs[0] = this.specificArg == null ? "(stdin)" : "(command line)";
		System.arraycopy(this.scriptArgs, 0, actualArgs, 1, this.scriptArgs.length);
		engine.setArg(actualArgs);
		if(this.specificArg != null) {
			engine.eval(actualArgs[0], this.specificArg);
		}
		else {
			engine.eval(actualArgs[0], this.readFromInput());
		}
	}

	protected String readFromInput() {
		BufferedInputStream stream = new BufferedInputStream(System.in);
		ByteArrayOutputStream streamBuffer = new ByteArrayOutputStream();
		int bufferSize = 2048;
		int read = 0;
		byte[] buffer = new byte[bufferSize];
		try {
			while((read = stream.read(buffer, 0, bufferSize)) > -1) {
				streamBuffer.write(buffer, 0, read);
			}
			return streamBuffer.toString();
		}
		catch(IOException e) {
			e.printStackTrace();
			Utils.fatal(1, "IO problem");
		}
		return null;
	}

	protected void showVersionInfo() {
		System.out.println(shellInfo);
		System.out.println(copyright);
	}

	protected void showHelpAndExit(int status, PrintStream stream, ArgsParser parser) {
		stream.println(shellInfo);
		stream.println("Usage: dshell [option] ... [-c cmd | file] [arg] ...");
		parser.printHelp(stream);
		System.exit(status);
	}

	public static void main(String[] args) {
		new DShell(args).execute();
	}
}
