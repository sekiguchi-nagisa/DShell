package dshell.internal.exe;

import java.util.EnumSet;

import dshell.internal.parser.error.ErrorListener;

/**
 * definition of ExecutionEngine.
 * if you vreate your own engine, you must implement it.
 * @author skgchxngsxyz-osx
 *
 */
public interface ExecutionEngine {
	/**
	 * overwrite engine configuration.
	 * @param config
	 */
	public void setConfig(EngineConfig config);
	
	/**
	 * set script argument to ARGV.
	 * @param scriptArgs
	 */
	public void setArg(String[] scriptArgs);

	/**
	 * overwrite error listener
	 * @param listener
	 */
	public void setErrorListener(ErrorListener listener);

	/**
	 * get error listener
	 * @return
	 */
	public ErrorListener getErrorListener();

	/**
	 * evaluate script.
	 * @param scriptName
	 * - script file name.
	 */
	public boolean eval(String scriptName);

	/**
	 * evaluate script from input.
	 * @param scriptName
	 * - source name.
	 * @param source
	 * - target script.
	 */
	public boolean eval(String scriptName, String source);

	/**
	 * evaluate one line script.
	 * @param source
	 * - target source
	 * @param lineNum
	 * - source line number.
	 * @return
	 * return true if exit success.
	 */
	public boolean eval(String source, int lineNum);

	/**
	 * load .dshellrc file.
	 */
	public void loadDShellRC();

	public static class EngineConfig {
		private EnumSet<EngineConfigRule> ruleSet;

		public EngineConfig() {
			this.ruleSet = EnumSet.noneOf(EngineConfigRule.class);
		}

		public void enableParserInspect() {
			this.ruleSet.add(EngineConfigRule.parserInspect);
		}

		public void enableParserTrace() {
			this.ruleSet.add(EngineConfigRule.parserTrace);
		}

		public void enableASTDump() {
			this.ruleSet.add(EngineConfigRule.astDump);
		}

		public void enableByteCodeDump() {
			this.ruleSet.add(EngineConfigRule.bytecodeDump);
		}

		public void enableOnlyParsing() {
			this.ruleSet.add(EngineConfigRule.onlyParsing);
		}

		public void disableAssertion() {
			this.ruleSet.add(EngineConfigRule.skipAssertion);
		}

		public boolean is(EngineConfigRule rule) {
			return this.ruleSet.contains(rule);
		}
	}

	public static enum EngineConfigRule {
		parserInspect,
		parserTrace,
		astDump,
		bytecodeDump,
		onlyParsing,
		skipAssertion,
		;
	}
}
