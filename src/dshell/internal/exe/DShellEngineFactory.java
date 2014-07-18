package dshell.internal.exe;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.TreeSet;

import org.antlr.v4.runtime.ANTLRFileStream;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import dshell.internal.codegen.JavaByteCodeGen;
import dshell.internal.lib.DShellClassLoader;
import dshell.internal.lib.RuntimeContext;
import dshell.internal.lib.Utils;
import dshell.internal.parser.ASTDumper;
import dshell.internal.parser.CommandScope;
import dshell.internal.parser.Node;
import dshell.internal.parser.TypeChecker;
import dshell.internal.parser.dshellLexer;
import dshell.internal.parser.dshellParser;
import dshell.internal.parser.Node.RootNode;
import dshell.internal.parser.dshellParser.ToplevelContext;
import dshell.internal.parser.error.ParserErrorHandler;
import dshell.internal.parser.error.TypeCheckException;
import dshell.internal.parser.error.ParserErrorHandler.ParserException;
import dshell.internal.type.TypePool;
import dshell.lang.GenericArray;
import dshell.lang.InputStream;
import dshell.lang.OutputStream;

public class DShellEngineFactory implements EngineFactory {
	@Override
	public ExecutionEngine getEngine() {
		return new DShellExecutionEngine();
	}

	public static class DShellExecutionEngine implements ExecutionEngine {
		protected final dshellLexer lexer;
		protected final dshellParser parser;
		protected final DShellClassLoader classLoader;
		protected final TypeChecker checker;
		protected final JavaByteCodeGen codeGen;
		protected EngineConfig config;

		protected DShellExecutionEngine() {
			this.lexer = new dshellLexer(null);
			this.parser = new dshellParser(null);
			this.parser.setErrorHandler(new ParserErrorHandler());

			this.classLoader = new DShellClassLoader();
			this.checker = new TypeChecker(new TypePool(this.classLoader));
			this.codeGen = new JavaByteCodeGen(this.classLoader);
			this.config = new EngineConfig();

			this.initGlobalVar();
		}

		protected void initGlobalVar() {
			RootNode rootNode = new RootNode(null);
			rootNode.addNode(new Node.GlobalVarNode("STDIN", "InputStream", InputStream.createStdin()));
			rootNode.addNode(new Node.GlobalVarNode("STDOUT", "OutputStream", OutputStream.createStdout()));
			rootNode.addNode(new Node.GlobalVarNode("STDERR", "OutputStream", OutputStream.createStderr()));

			RootNode checkedNode = this.checker.checkTypeRootNode(rootNode);
			this.codeGen.generateTopLevelClass(checkedNode, false);
		}

		@Override
		public void setConfig(EngineConfig config) {
			this.config = config;
			this.classLoader.setDump(config.is(EngineConfigRule.bytecodeDump));
		}

		@Override
		public void setArg(String[] scriptArgs) {
			RootNode rootNode = new RootNode(null);
			rootNode.addNode(new Node.GlobalVarNode("ARGV", "Array<String>", new GenericArray(scriptArgs)));

			RootNode checkedNode = this.checker.checkTypeRootNode(rootNode);
			this.codeGen.generateTopLevelClass(checkedNode, false);
		}

		@Override
		public boolean eval(String scriptName) {
			ANTLRFileStream input = null;
			try {
				input = new ANTLRFileStream(scriptName);
			} catch(IOException e) {
				System.err.println("cannot load file: " + scriptName);
				return false;
			}
			return this.eval(input, 1, false);
		}

		@Override
		public boolean eval(String scriptName, String source) {
			ANTLRInputStream input = new ANTLRInputStream(source);
			input.name = scriptName;
			return this.eval(input, 1, false);
		}

		@Override
		public boolean eval(String source, int lineNum) {
			ANTLRInputStream input = new ANTLRInputStream(source);
			input.name = "(stdin)";
			return this.eval(input, lineNum, true);
		}

		@Override
		public void loadDShellRC() {
			String dshellrcPath = Utils.getEnv("HOME") + "/.dshellrc";
			ANTLRFileStream input = null;
			try {
				input = new ANTLRFileStream(dshellrcPath);
			} catch(IOException e) {
				return;
			}
			this.eval(input, 1, false);
		}

		@Override
		public void importCommandsFromPath() {
			TreeSet<String> commandSet = Utils.getCommandSetFromPath();
			CommandScope scope = this.lexer.getScope();
			for(String command : commandSet) {
				if(scope.setCommandPath(command) && RuntimeContext.getInstance().isDebugMode()) {
					System.err.println("duplicated command: " + command);
				}
			}
		}

		/**
		 * evaluate input.
		 * @param input
		 * - include source and source name.
		 * @param lineNum
		 * - start line number.
		 * @return
		 * - return true, if evaluation success.
		 */
		protected boolean eval(ANTLRInputStream input, int lineNum, boolean enableResultPrint) {
			/**
			 * set input stream.
			 */
			this.lexer.setInputStream(input);
			this.lexer.setLine(lineNum);
			CommonTokenStream tokenStream = new CommonTokenStream(this.lexer);
			this.parser.setTokenStream(tokenStream);
			if(this.config.is(EngineConfigRule.parserTrace)) {
				this.lexer.setTrace(true);
				this.parser.setTrace(true);
			}
			/**
			 * parse source
			 */
			ToplevelContext tree;
			try {
				tree = this.parser.startParser();
			} catch(ParseCancellationException | ParserException e) {	// TODO: error report
				return false;
			}
			if(this.config.is(EngineConfigRule.parserInspect)) {
				tree.inspect(this.parser);
			}
			/**
			 * check type
			 */
			RootNode checkedNode;
			try {
				checkedNode = this.checker.checkTypeRootNode(tree.node);
			} catch(TypeCheckException e) {
				this.checker.reset();
				System.err.println(e.getMessage());
				return false;
			}

			if(this.config.is(EngineConfigRule.astDump)) {
				ASTDumper.getInstance().convertToJson(checkedNode);
			}

			/**
			 * code generation
			 */
			Class<?> entryClass = this.codeGen.generateTopLevelClass(checkedNode, enableResultPrint);
			/**
			 * invoke
			 */
			return startExecution(entryClass);
		}

		/**
		 * start execution from top level class.
		 * @param entryClass
		 * - generated top level class.
		 * @return
		 * return false, if invocation target exception has raised.
		 */
		protected boolean startExecution(Class<?> entryClass) {
			try {
				Method staticMethod = entryClass.getMethod("invoke");
				staticMethod.invoke(null);
				return true;
			} catch(InvocationTargetException e) {
				if(RuntimeContext.getInstance().isDebugMode()) {
					e.getCause().printStackTrace();
				} else {
					Utils.printException(e);
				}
			} catch(Throwable t) {
				t.printStackTrace();
				Utils.fatal(1, "invocation problem");
			}
			return false;
		}
	}
}
