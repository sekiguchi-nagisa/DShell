package dshell.internal.parser;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.Token;

import dshell.internal.parser.error.TypeLookupException;
import dshell.internal.type.TypePool;
import dshell.internal.type.DSType;

/**
 * contains parsed type symbol.
 * @author skgchxngsxyz-opensuse
 *
 */
public abstract class TypeSymbol {
	/**
	 * TypeSymbol to Type.
	 * called from TypeChecker
	 * @param pool
	 * @return
	 * - throw exception, if type not found.
	 */
	public abstract DSType toType(TypePool pool);

	public static TypeSymbol toPrimitive(Token token) {
		return new PrimitiveTypeSymbol(token);
	}

	public static TypeSymbol toVoid(Token token) {
		return new VoidTypeSymbol();
	}

	public static TypeSymbol toVoid() {
		return new VoidTypeSymbol();
	}

	public static TypeSymbol toClass(Token token) {
		return new ClassTypeSymbol(token);
	}

	public static TypeSymbol toFunc(Token token, TypeSymbol returnTypeSymbol, TypeSymbol[] paramTypeSymbols) {
		return new FuncTypeSymbol(token, returnTypeSymbol, paramTypeSymbols);
	}

	public static TypeSymbol toGeneric(Token token, TypeSymbol[] typeSymbols) {
		return new GenericTypeSymbol(token, typeSymbols);
	}

	public static class PrimitiveTypeSymbol extends TypeSymbol {
		private final Token token;

		private PrimitiveTypeSymbol(Token token) {
			this.token = token;
		}

		@Override
		public DSType toType(TypePool pool) {
			try {
				return pool.getPrimitiveType(this.token.getText());
			} catch(TypeLookupException e) {
				TypeLookupException.formateAndPropagateException(e, this.token);
			}
			return null;
		}
	}

	public static class VoidTypeSymbol extends TypeSymbol {
		@Override
		public DSType toType(TypePool pool) {
			return TypePool.voidType;
		}
	}

	public static class ClassTypeSymbol extends TypeSymbol {
		private final Token token;

		private ClassTypeSymbol(Token token) {
			this.token = token;
		}

		@Override
		public DSType toType(TypePool pool) {
			try {
				return pool.getClassType(this.token.getText());
			} catch(TypeLookupException e) {
				TypeLookupException.formateAndPropagateException(e, this.token);
			}
			return null;
		}
	}

	public static class FuncTypeSymbol extends TypeSymbol {
		private final Token token;
		private final TypeSymbol returnTypeSymbol;
		private final TypeSymbol[] paramtypeSymbols;

		private FuncTypeSymbol(Token token, TypeSymbol returnTypeSymbol, TypeSymbol[] paramTypeSymbols) {
			this.token = token;
			this.returnTypeSymbol = returnTypeSymbol;
			this.paramtypeSymbols = paramTypeSymbols;
		}

		@Override
		public DSType toType(TypePool pool) {
			DSType returnType = this.returnTypeSymbol.toType(pool);
			List<DSType> paramTypeList = new ArrayList<>(this.paramtypeSymbols.length);
			for(TypeSymbol typeSymbol : this.paramtypeSymbols) {
				paramTypeList.add(typeSymbol.toType(pool));
			}
			try {
				return pool.createAndGetFuncTypeIfUndefined(returnType, paramTypeList);
			} catch(TypeLookupException e) {
				TypeLookupException.formateAndPropagateException(e, this.token);
			}
			return null;
		}
	}

	public static class GenericTypeSymbol extends TypeSymbol {
		private final Token token;
		private final TypeSymbol[] typeSymbols;

		private GenericTypeSymbol(Token token, TypeSymbol[] typeSymbols) {
			this.token = token;
			this.typeSymbols = typeSymbols;
		}

		@Override
		public DSType toType(TypePool pool) {
			List<DSType> elementTypeList = new ArrayList<>(this.typeSymbols.length);
			for(TypeSymbol typeSymbol : this.typeSymbols) {
				elementTypeList.add(typeSymbol.toType(pool));
			}
			try {
				return pool.createAndGetReifiedTypeIfUndefined(this.token.getText(), elementTypeList);
			} catch(TypeLookupException e) {
				TypeLookupException.formateAndPropagateException(e, this.token);
			}
			return null;
		}
	}
}
