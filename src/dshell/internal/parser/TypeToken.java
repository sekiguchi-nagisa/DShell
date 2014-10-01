package dshell.internal.parser;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.Token;

import dshell.internal.parser.error.TypeCheckException.TypeLookupException;
import dshell.internal.type.TypePool;
import dshell.internal.type.DSType;

/**
 * contains parsed type symbol.
 * @author skgchxngsxyz-opensuse
 *
 */
public abstract class TypeToken {
	/**
	 * represent type token.
	 */
	protected final Token token;

	protected TypeToken(Token token) {
		this.token = token;
	}

	public Token getToken() {
		return this.token;
	}

	@Override
	public String toString() {
		return this.token.getText();
	}

	/**
	 * TypeSymbol to Type.
	 * called from TypeChecker
	 * @param pool
	 * @return
	 * - throw exception, if type not found.
	 */
	public abstract DSType toType(TypePool pool);

	public static TypeToken toPrimitive(Token token) {
		return new PrimitiveTypeToken(token);
	}

	public static TypeToken toVoid(Token token) {
		return new VoidTypeToken(token);
	}

	public static TypeToken toVoid() {
		return new VoidTypeToken(null);
	}

	public static TypeToken toClass(Token token) {
		return new ClassTypeToken(token);
	}

	public static class PrimitiveTypeToken extends TypeToken {
		public PrimitiveTypeToken(Token token) {
			super(token);
		}

		@Override
		public DSType toType(TypePool pool) {
			try {
				return pool.getPrimitiveType(this.token.getText());
			} catch(TypeLookupException e) {
				TypeLookupException.formatAndPropagateException(e, this.token);
			}
			return null;
		}
	}

	public static class VoidTypeToken extends TypeToken {
		public VoidTypeToken(Token token) {
			super(token);
		}

		@Override
		public DSType toType(TypePool pool) {
			return TypePool.voidType;
		}

		@Override
		public String toString() {
			return "void";
		}
	}

	public static class ClassTypeToken extends TypeToken {
		public ClassTypeToken(Token token) {
			super(token);
		}

		@Override
		public DSType toType(TypePool pool) {
			try {
				return pool.getClassType(this.token.getText());
			} catch(TypeLookupException e) {
				TypeLookupException.formatAndPropagateException(e, this.token);
			}
			return null;
		}
	}

	public static class FuncTypeToken extends TypeToken {
		private final TypeToken returnTypeToken;
		private final List<TypeToken> paramtypeTokenList;

		public FuncTypeToken(Token token, TypeToken returnTypeSymbol) {
			super(token);
			this.returnTypeToken = returnTypeSymbol;
			this.paramtypeTokenList = new ArrayList<>();
		}

		public void addParamTypeToken(TypeToken typeToken) {
			this.paramtypeTokenList.add(typeToken);
		}

		@Override
		public DSType toType(TypePool pool) {
			DSType returnType = this.returnTypeToken.toType(pool);
			List<DSType> paramTypeList = new ArrayList<>(this.paramtypeTokenList.size());
			for(TypeToken typeSymbol : this.paramtypeTokenList) {
				paramTypeList.add(typeSymbol.toType(pool));
			}
			try {
				return pool.createAndGetFuncTypeIfUndefined(returnType, paramTypeList);
			} catch(TypeLookupException e) {
				TypeLookupException.formatAndPropagateException(e, this.token);
			}
			return null;
		}

		@Override
		public String toString() {
			StringBuilder sBuilder = new StringBuilder();
			sBuilder.append(this.token.toString());
			sBuilder.append('<');
			sBuilder.append(returnTypeToken.toString());
			final int size = this.paramtypeTokenList.size();
			if(size > 0) {
				sBuilder.append(",[");
				for(int i = 0; i < size; i++) {
					if(i > 0) {
						sBuilder.append(',');
					}
					sBuilder.append(this.paramtypeTokenList.get(i).toString());
				}
				sBuilder.append(']');
			}
			sBuilder.append('>');
			return sBuilder.toString();
		}
	}

	public static class GenericTypeToken extends TypeToken {
		private final List<TypeToken> elementTypeTokenList;

		public GenericTypeToken(Token token) {
			super(token);
			this.elementTypeTokenList = new ArrayList<>();
		}

		public void addElementTypeToken(TypeToken typeToken) {
			this.elementTypeTokenList.add(typeToken);
		}

		@Override
		public DSType toType(TypePool pool) {
			List<DSType> elementTypeList = new ArrayList<>(this.elementTypeTokenList.size());
			for(TypeToken typeSymbol : this.elementTypeTokenList) {
				elementTypeList.add(typeSymbol.toType(pool));
			}
			try {
				return pool.createAndGetReifiedTypeIfUndefined(this.token.getText(), elementTypeList);
			} catch(TypeLookupException e) {
				TypeLookupException.formatAndPropagateException(e, this.token);
			}
			return null;
		}

		@Override
		public String toString() {
			StringBuilder sBuilder = new StringBuilder();
			sBuilder.append(this.token.toString());
			sBuilder.append('<');
			final int size = this.elementTypeTokenList.size();
			for(int i = 0; i < size; i++) {
				if(i > 0) {
					sBuilder.append(',');
				}
				sBuilder.append(this.elementTypeTokenList.get(i).toString());
			}
			sBuilder.append('>');
			return sBuilder.toString();
		}
	}
}
