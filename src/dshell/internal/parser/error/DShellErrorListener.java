package dshell.internal.parser.error;

import java.util.List;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import dshell.internal.lib.Utils;
import dshell.internal.parser.Node;
import dshell.internal.parser.ParserUtils;
import dshell.internal.parser.dshellParser;

public class DShellErrorListener {
	public static enum TypeErrorKind {
		Unresolved     ("having unresolved type"),
		Required       ("require %s, but is %s"),
		DefinedSymbol  ("already defined symbol: %s"),
		InsideLoop     ("only available inside loop statement"),
		UnfoundReturn  ("not found return statement"),
		UndefinedSymbol("undefined symbol: %s"),
		UndefinedField ("undefined field: %s"),
		CastOp         ("unsupported cast op: %s -> %s"),
		UnaryOp        ("undefined operator: %s %s"),
		BinaryOp       ("undefined operator: %s %s %s"),
		UnmatchParam   ("not match parameter, require size is %d, but is %d"),
		UndefinedMethod("undefined method: %s"),
		UndefinedInit  ("undefined constructor: %s"),
		Unreachable    ("found unreachable code"),
		InsideFunc     ("only available inside function"),
		NotNeedExpr    ("not need expression"),
		Assignable     ("require assignable node"),
		ReadOnly       ("read only value"),

		Unimplemented  ("unimplemented type checker api: %s");

		private final String template;

		private TypeErrorKind(String template) {
			this.template = template;
		}

		public String getTemplate() {
			return this.template;
		}
	}

	public void reportTypeError(Node node, TypeErrorKind kind, Object... args) {
		throw new TypeCheckException(node.getToken(), String.format(kind.getTemplate(), args));
	}

	public static String formatTypeError(TypeCheckException e, dshellParser parser) {
		Token token = e.getErrorToken();
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append(formatLocation(token));
		sBuilder.append(" [TypeError] ");
		sBuilder.append(e.getMessage().trim());
		if(token != null) {
			sBuilder.append('\n');
			sBuilder.append(formatLine(parser, token));
		}
		return sBuilder.toString();
	}

	/**
	 * 
	 * @param token
	 * - may be null
	 * @return
	 */
	private static String formatLocation(Token token) {
		StringBuilder sBuilder = new StringBuilder();
		if(token == null) {
			return "???:?:?:";
		}
		sBuilder.append(token.getTokenSource().getSourceName());
		sBuilder.append(':');
		sBuilder.append(token.getLine());
		sBuilder.append(':');
		sBuilder.append(token.getCharPositionInLine());
		sBuilder.append(':');
		return sBuilder.toString();
	}

	/**
	 * 
	 * @param token
	 * - not null
	 * @return
	 */
	private static String formatLine(dshellParser parser, Token token) {
		StringBuilder sBuilder = new StringBuilder();
		List<Token> tokenList = ((CommonTokenStream) parser.getTokenStream()).getTokens();
		final int lineNum = token.getLine();
		final int tokenIndex = token.getTokenIndex();
		final int size = tokenList.size();
		Token lineStartToken = token;
		Token lineEndToken = token;

		// look up line start token
		int index = tokenIndex;
		while(index - 1 > -1) {
			Token curToken = tokenList.get(--index);
			if(curToken.getLine() != lineNum) {
				break;
			}
			lineStartToken = curToken;
		}

		// look up line end token
		index = tokenIndex;
		while(index + 1 < size) {
			Token curToken = tokenList.get(++index);
			if(curToken.getLine() != lineNum) {
				break;
			}
			lineEndToken = curToken;
		}
		sBuilder.append(Utils.removeNewLine(new ParserUtils.JoinedToken(lineStartToken, lineEndToken).getText()));
		sBuilder.append('\n');

		// create marker
		for(int i = 0; i < token.getCharPositionInLine(); i++) {
			sBuilder.append(' ');
		}
		final int tokenSize = token.getText().length();
		for(int i = 0; i < tokenSize; i++) {
			sBuilder.append('^');
		}
		return sBuilder.toString();
	}
}
