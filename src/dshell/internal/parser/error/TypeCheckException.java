package dshell.internal.parser.error;

import org.antlr.v4.runtime.Token;

/**
 * report type error.
 * @author skgchxngsxyz-osx
 *
 */
public class TypeCheckException extends RuntimeException {
	private static final long serialVersionUID = 5959458194095777506L;

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
		Unacceptable   ("unacceptable type: %s"),
		InsideFinally  ("unavailable inside finally block"),

		Unimplemented  ("unimplemented type checker api: %s");

		private final String template;

		private TypeErrorKind(String template) {
			this.template = template;
		}

		public String getTemplate() {
			return this.template;
		}
	}

	/**
	 * used for message foramting.
	 * may be null
	 */
	protected final Token errorPointToken;

	public TypeCheckException(Token errorPointToken, String message) {
		super(message);
		this.errorPointToken = errorPointToken;
	}

	public Token getErrorToken() {
		return this.errorPointToken;
	}

	public static class TypeLookupException extends TypeCheckException {
		private static final long serialVersionUID = -2757004654976764776L;

		private final String message;

		public TypeLookupException(String message) {
			super(null, message);
			this.message = message;
		}

		private String getSourceMessage() {
			return this.message;
		}

		public static void formatAndPropagateException(TypeLookupException e, Token token) {
			throw new FormattedTypeLookupException(e, token);
		}
	}

	private static class FormattedTypeLookupException extends TypeCheckException {
		private static final long serialVersionUID = -7553167319361964982L;

		private final TypeLookupException cause;

		private FormattedTypeLookupException(TypeLookupException cause, Token errorPointToken) {
			super(errorPointToken, cause.getSourceMessage());
			this.cause = cause;
		}

		@Override
		public void printStackTrace() {
			this.cause.printStackTrace();
			super.printStackTrace();
		}
	}
}
