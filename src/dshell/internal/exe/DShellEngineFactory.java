package dshell.internal.exe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.TokenStream;

import dshell.internal.codegen.JavaByteCodeGen;
import dshell.internal.lib.DShellClassLoader;
import dshell.internal.lib.RuntimeContext;
import dshell.internal.lib.Utils;
import dshell.internal.parser.ASTDumper;
import dshell.internal.parser.Node;
import dshell.internal.parser.SourceStream;
import dshell.internal.parser.TypeChecker;
import dshell.internal.parser.dshellLexer;
import dshell.internal.parser.dshellParser;
import dshell.internal.parser.Node.RootNode;
import dshell.internal.parser.error.DShellErrorListener;
import dshell.internal.parser.error.ErrorListener;
import dshell.internal.parser.error.TypeCheckException;
import dshell.internal.parser.error.ParserErrorListener;
import dshell.internal.parser.error.ParserErrorListener.LexerException;
import dshell.internal.parser.error.ParserErrorListener.ParserException;
import dshell.internal.type.TypePool;
import dshell.lang.GenericArray;
import dshell.lang.InputStream;
import dshell.lang.OutputStream;

public class DShellEngineFactory implements EngineFactory {
	@Override
	public ExecutionEngine getEngine() {
		return new DShellExecutionEngine();
	}

	protected static class DShellExecutionEngine implements ExecutionEngine {
		protected final dshellLexer lexer;
		protected final dshellParser parser;
		protected final DShellClassLoader classLoader;
		protected final TypeChecker checker;
		protected final JavaByteCodeGen codeGen;
		protected ErrorListener listener;
		protected EngineConfig config;

		protected DShellExecutionEngine() {
			this.lexer = new dshellLexer(null);
			this.lexer.removeErrorListeners();
			this.lexer.addErrorListener(ParserErrorListener.getInstance());
			this.parser = new dshellParser(null);
			this.parser.removeErrorListeners();
			this.parser.addErrorListener(ParserErrorListener.getInstance());

			TypePool pool = new TypePool();
			this.classLoader = new DShellClassLoader(TypePool.generatedPackage);
			this.checker = new TypeChecker(pool);
			this.codeGen = new JavaByteCodeGen(this.classLoader);
			this.listener = new DShellErrorListener();
			this.config = new EngineConfig();

			this.initGlobalVar();
		}

		/**
		 * initialize global variables (STDIN, STDOUT, STDERR)
		 */
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
			DShellClassLoader.setDump(config.is(EngineConfigRule.bytecodeDump));
			this.codeGen.setAssertion(!this.config.is(EngineConfigRule.skipAssertion));
		}

		@Override
		public void setArg(String[] scriptArgs) {
			RootNode rootNode = new RootNode(null);

			final int size = scriptArgs.length - 1;
			String[] args = new String[size];
			if(size > 0) {
				System.arraycopy(scriptArgs, 1, args, 0, size);
			}
			rootNode.addNode(new Node.GlobalVarNode("$ARGS", "Array<String>", new GenericArray(args)));
			rootNode.addNode(new Node.GlobalVarNode("ARGV", "Array<String>", new GenericArray(scriptArgs)));

			RootNode checkedNode = this.checker.checkTypeRootNode(rootNode);
			this.codeGen.generateTopLevelClass(checkedNode, false);
		}

		@Override
		public void setErrorListener(ErrorListener listener) {
			this.listener = listener;
		}

		@Override
		public ErrorListener getErrorListener() {
			return this.listener;
		}

		@Override
		public boolean eval(String scriptName) {
			return this.eval(new SourceStream(scriptName), 1, false);
		}

		@Override
		public boolean eval(String scriptName, String source) {
			return this.eval(new SourceStream(scriptName, source), 1, false);
		}

		@Override
		public boolean eval(String source, int lineNum) {
			return this.eval(new SourceStream("(stdin)", source), lineNum, true);
		}

		@Override
		public void loadDShellRC() {
			String dshellrcPath = Utils.getEnv("HOME") + "/.dshellrc";
			char[] buffer = Utils.load(dshellrcPath);
			if(buffer != null) {
				this.eval(new SourceStream(dshellrcPath, buffer), 1, false);
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
		protected boolean eval(SourceStream input, int lineNum, boolean enableResultPrint) {
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
			if(this.config.is(EngineConfigRule.parserInspect)) {
				this.parser.setInspect(true);
			}

			// parse and execution
			boolean result = true;
			while(tokenStream.LA(1) != TokenStream.EOF) {
				/**
				 * parse source
				 */
				RootNode untypedNode;
				try {
					untypedNode = this.parser.parseToplevel();
				} catch(LexerException e) {
					this.listener.displayTokenError(e);
					return false;
				} catch(ParserException e) {
					this.listener.displayParseError(e);
					return false;
				}
				/**
				 * check type
				 */
				RootNode checkedNode;
				try {
					checkedNode = this.checker.checkTypeRootNode(untypedNode);
				} catch(TypeCheckException e) {
					this.checker.recover();
					this.listener.displayTypeError(e, this.parser);
					if(RuntimeContext.getInstance().isDebugMode()) {
						e.printStackTrace();
					}
					return false;
				}

				if(this.config.is(EngineConfigRule.astDump)) {
					ASTDumper.getInstance().convertToJson(checkedNode);
				}
				if(this.config.is(EngineConfigRule.onlyParsing)) {
					System.out.println("=== only parsing ===");
					return true;
				}

				/**
				 * code generation
				 */
				Class<?> entryClass = this.codeGen.generateTopLevelClass(checkedNode, enableResultPrint);
				/**
				 * invoke
				 */
				result = startExecution(entryClass);
				if(!result) {
					this.checker.recover();
					return false;
				}
			}
			return result;
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
