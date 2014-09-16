package dshell.internal.parser.error;

import org.antlr.v4.runtime.FailedPredicateException;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

import dshell.internal.parser.SourceStream;
import dshell.internal.parser.dshellParser;
import dshell.internal.parser.error.ParserErrorListener.LexerException;
import dshell.internal.parser.error.ParserErrorListener.ParserException;

public class DShellErrorListener {
	public void displayTokenError(LexerException e) {
		System.err.println(this.formatTokenError(e));
	}

	public void displayParseError(ParserException e) {
		System.err.println(this.formatParseError(e));
	}

	protected String formatTokenError(LexerException cause) {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append(this.formatLocation(cause.getCause(), cause.getLineNum(), cause.getCharPosInLine()));

		String message = cause.getCause().toString();
		int startIndex = message.indexOf('\'');
		int stopIndex = message.lastIndexOf('\'');
		String tokenText = message.substring(startIndex + 1, stopIndex);
		sBuilder.append(" [SyntaxError] ");
		sBuilder.append("unrecognized token: '" + tokenText + '\'');
		return sBuilder.toString();
	}

	protected String formatLocation(LexerNoViableAltException e, int lineNum, int charPosInLine) {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append(e.getInputStream().getSourceName());
		sBuilder.append(':');
		sBuilder.append(lineNum);
		sBuilder.append(':');
		sBuilder.append(charPosInLine);
		sBuilder.append(':');
		return sBuilder.toString();
	}

	protected String formatParseError(ParserException cause) {
		RecognitionException e = cause.getCause();
		dshellParser parser = cause.getParser();

		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append(this.formatLocation(e.getOffendingToken()));
		sBuilder.append(" [SyntaxError] ");
		sBuilder.append("invalid syntax");
		if(e instanceof NoViableAltException) {
			IntervalSet expectedTokens = e.getExpectedTokens();
			if(expectedTokens != null && expectedTokens.size() == 1) {
				sBuilder.append(", expect for: ");
				sBuilder.append(expectedTokens.toString(parser.getTokenNames()));
			}
		} else if(e instanceof FailedPredicateException) {
			String predicate = ((FailedPredicateException) e).getPredicate();
			if(predicate.equals("isCommand()")) {
				sBuilder.append(", expect for command");
			}
		} else if(e instanceof InputMismatchException) {
			IntervalSet expectedTokens = e.getExpectedTokens();
			if(expectedTokens != null) {
				sBuilder.append(", expect for: ");
				sBuilder.append(expectedTokens.toString(parser.getTokenNames()));
			}
		}
		Token token = e.getOffendingToken();
		if(token != null) {
			sBuilder.append('\n');
			sBuilder.append(this.formatLine(parser, token));
		}
		return sBuilder.toString();
	}

	/**
	 * format error location from token.
	 * @param token
	 * - may be null
	 * @return
	 */
	protected String formatLocation(Token token) {
		StringBuilder sBuilder = new StringBuilder();
		if(token == null) {
			return "??:??:?:";
		}

		SourceStream input = (SourceStream) token.getInputStream();
		sBuilder.append(token.getTokenSource().getSourceName());
		sBuilder.append(':');
		sBuilder.append(token.getLine());
		sBuilder.append(':');
		sBuilder.append(token.getCharPositionInLine() + input.getOffset());
		sBuilder.append(':');
		return sBuilder.toString();
	}

	// for type error
	/**
	 * format and display type error.
	 * @param e
	 * @param parser
	 * - for error location
	 */
	public void displayTypeError(TypeCheckException e, dshellParser parser) {
		System.err.println(this.formatTypeError(e, parser));
	}

	protected String formatTypeError(TypeCheckException e, dshellParser parser) {
		Token token = e.getErrorToken();
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append(this.formatLocation(token));
		sBuilder.append(" [TypeError] ");
		sBuilder.append(e.getMessage().trim());
		if(token != null) {
			sBuilder.append('\n');
			sBuilder.append(this.formatLine(parser, token));
		}
		return sBuilder.toString();
	}

	/**
	 * 
	 * @param parser
	 * @param token
	 * not null
	 * @return
	 */
	protected String formatLine(dshellParser parser, Token token) {
		StringBuilder sBuilder = new StringBuilder();
		SourceStream input = (SourceStream) token.getInputStream();

		sBuilder.append(input.getLineText(token));
		sBuilder.append('\n');

		// create marker
		for(int i = 0; i < token.getCharPositionInLine() + input.getOffset(); i++) {
			sBuilder.append(' ');
		}
		final int tokenSize = token.getText().length();
		for(int i = 0; i < tokenSize; i++) {
			sBuilder.append('^');
		}
		return sBuilder.toString();
	}
}
