package dshell.internal.parser.error;

import org.antlr.v4.runtime.Token;

/**
 * report type error.
 * @author skgchxngsxyz-osx
 *
 */
public class TypeCheckException extends RuntimeException {
	private static final long serialVersionUID = 5959458194095777506L;

	// definition of type error message
	public static interface TypeErrorKind {
		public String getTemplate();
	}

	public static enum TypeErrorKind_ZeroArg implements TypeErrorKind {
		Unresolved     ("having unresolved type"),
		InsideLoop     ("only available inside loop statement"),
		UnfoundReturn  ("not found return statement"),
		Unreachable    ("found unreachable code"),
		InsideFunc     ("only available inside function"),
		NotNeedExpr    ("not need expression"),
		Assignable     ("require assignable node"),
		ReadOnly       ("read only value"),
		InsideFinally  ("unavailable inside finally block"),
		;

		private final String template;

		private TypeErrorKind_ZeroArg(String template) {
			this.template = template;
		}

		@Override
		public String getTemplate() {
			return this.template;
		}
	}

	public static enum TypeErrorKind_OneArg implements TypeErrorKind {
		DefinedSymbol  ("already defined symbol: %s"),
		UndefinedSymbol("undefined symbol: %s"),
		UndefinedField ("undefined field: %s"),
		UndefinedMethod("undefined method: %s"),
		UndefinedInit  ("undefined constructor: %s"),
		Unacceptable   ("unacceptable type: %s"),
		Unimplemented  ("unimplemented type checker api: %s"),
		;

		private final String template;

		private TypeErrorKind_OneArg(String template) {
			this.template = template;
		}

		@Override
		public String getTemplate() {
			return this.template;
		}
	}

	public static enum TypeErrorKind_TwoArg implements TypeErrorKind {
		Required       ("require %s, but is %s"),
		CastOp         ("unsupported cast op: %s -> %s"),
		UnaryOp        ("undefined operator: %s %s"),
		UnmatchParam   ("not match parameter, require size is %d, but is %d"),
		;

		private final String template;

		private TypeErrorKind_TwoArg(String template) {
			this.template = template;
		}

		@Override
		public String getTemplate() {
			return this.template;
		}
	}

	public static enum TypeErrorKind_ThreeArg implements TypeErrorKind {
		BinaryOp       ("undefined operator: %s %s %s"),
		;

		private final String template;

		private TypeErrorKind_ThreeArg(String template) {
			this.template = template;
		}

		@Override
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
