package dshell.internal.parser.error;

import dshell.internal.parser.dshellParser;
import dshell.internal.parser.error.ParserErrorListener.LexerException;
import dshell.internal.parser.error.ParserErrorListener.ParserException;

public interface ErrorListener {
	/**
	 * format and display lexer error
	 * @param e
	 * contains lexer error
	 */
	public void displayTokenError(LexerException e);

	/**
	 * format and display parser error
	 * @param e
	 * contains parser error
	 */
	public void displayParseError(ParserException e);

	/**
	 * format and display type error
	 * @param e
	 * contains type error
	 * @param parser
	 * for message formatting
	 */
	public void displayTypeError(TypeCheckException e, dshellParser parser);
}
