package dshell.internal.parser.error;

import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import dshell.internal.parser.dshellLexer;
import dshell.internal.parser.dshellParser;

public class ParserErrorListener extends ConsoleErrorListener {
	private ParserErrorListener() {
	}

	@Override
	public void syntaxError(Recognizer<?,?> recognizer, Object offendingSymbol, int line, 
			int charPositionInLine, String msg, RecognitionException e) {
		if(recognizer instanceof dshellParser) {
			if(e == null) {
				e = new InputMismatchException((Parser) recognizer);
			}
			throw new ParserException((dshellParser) recognizer, e);
		} else {
			throw new LexerException((dshellLexer) recognizer, (LexerNoViableAltException )e, line, charPositionInLine);
		}
	}

	private static class Holder {
		private final static ParserErrorListener INSTANCE = new ParserErrorListener();
	}

	public static ParserErrorListener getInstance() {
		return Holder.INSTANCE;
	}

	public static class ParserException extends RuntimeException {
		private static final long serialVersionUID = 7306109551336858749L;

		private final dshellParser parser;

		private ParserException(dshellParser parser, RecognitionException cause) {
			super(cause);
			this.parser = parser;
		}

		public dshellParser getParser() {
			return this.parser;
		}

		public RecognitionException getCause() {
			return (RecognitionException) super.getCause();
		}
	}

	public static class LexerException extends RuntimeException {
		private static final long serialVersionUID = -5624874781990338591L;

		private final dshellLexer lexer;
		private final int lineNum;
		private final int charPosInLine;

		private LexerException(dshellLexer lexer, LexerNoViableAltException cause, int lineNum, int charPosInLine) {
			super(cause);
			this.lexer = lexer;
			this.lineNum = lineNum;
			this.charPosInLine = charPosInLine;
		}

		public dshellLexer getLexer() {
			return this.lexer;
		}

		public LexerNoViableAltException getCause() {
			return (LexerNoViableAltException) super.getCause();
		}

		public int getLineNum() {
			return this.lineNum;
		}

		public int getCharPosInLine() {
			return this.charPosInLine;
		}
	}
}
