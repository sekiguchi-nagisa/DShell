package dshell.internal.parser.error;

import org.antlr.v4.runtime.FailedPredicateException;
import org.antlr.v4.runtime.InputMismatchException;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

import dshell.internal.parser.SourceStream;
import dshell.internal.parser.dshellParser;
import dshell.internal.parser.error.ParserErrorListener.LexerException;
import dshell.internal.parser.error.ParserErrorListener.ParserException;

public class DShellErrorListener implements ErrorListener {
	// for parser error
	@Override
	public void displayTokenError(LexerException e) {
		System.err.println(this.formatTokenError(e));
	}

	@Override
	public void displayParseError(ParserException e) {
		System.err.println(this.formatParseError(e));
	}

	protected ErrorMessage formatTokenError(LexerException cause) {
		String message = cause.getCause().toString();
		int startIndex = message.indexOf('\'');
		int stopIndex = message.lastIndexOf('\'');
		String tokenText = message.substring(startIndex + 1, stopIndex);
		message = "unrecognized token: '" + tokenText + '\'';

		return new ErrorMessage()
			.setErrorLocation(cause.getCause().getInputStream().getSourceName(),
				cause.getLineNum(), cause.getCharPosInLine())
			.setErrorType(ErrorMessage.SYNTAX_ERROR)
			.setMessage(message);
	}

	protected ErrorMessage formatParseError(ParserException cause) {
		RecognitionException e = cause.getCause();
		dshellParser parser = cause.getParser();
		
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append("invalid syntax");
		if(e instanceof NoViableAltException) {
			IntervalSet expectedTokens = e.getExpectedTokens();
			if(expectedTokens != null) {
				sBuilder.append(", expect for: ");
				sBuilder.append(expectedTokens.toString(parser.getTokenNames()));
			}
		} else if(e instanceof FailedPredicateException) {
			String predicate = ((FailedPredicateException) e).getPredicate();
			if(predicate.equals("!hasNewLine()")) {
				sBuilder.append(", unexpected new line");
			}
		} else if(e instanceof InputMismatchException) {
			IntervalSet expectedTokens = e.getExpectedTokens();
			if(expectedTokens != null) {
				sBuilder.append(", mismatch input. expect for: ");
				sBuilder.append(expectedTokens.toString(parser.getTokenNames()));
			}
		}
		Token token = e.getOffendingToken();

		return this.formatLine(new ErrorMessage()
				.setErrorLocation(token)
				.setErrorType(ErrorMessage.SYNTAX_ERROR)
				.setMessage(sBuilder.toString()), token);
	}

	// for type error
	@Override
	public void displayTypeError(TypeCheckException e, dshellParser parser) {
		System.err.println(this.formatTypeError(e, parser));
	}

	protected ErrorMessage formatTypeError(TypeCheckException e, dshellParser parser) {
		Token token = e.getErrorToken();
		return this.formatLine(
			new ErrorMessage()
				.setErrorLocation(token)
				.setErrorType(ErrorMessage.TYPE_ERROR)
				.setMessage(e.getMessage().trim()), token);
	}

	/**
	 * 
	 * @param parser
	 * @param token
	 * not null
	 * @return
	 */
	protected ErrorMessage formatLine(ErrorMessage eMessage, Token token) {
		SourceStream input = (SourceStream) token.getInputStream();
		StringBuilder sBuilder = new StringBuilder();

		for(int i = 0; i < token.getCharPositionInLine() + input.getOffset(); i++) {
			sBuilder.append(' ');
		}
		final int tokenSize = token.getText().length();
		for(int i = 0; i < tokenSize; i++) {
			sBuilder.append('^');
		}

		return eMessage
				.setLineText(input.getLineText(token))
				.setLineMarker(sBuilder.toString());
	}
}
