package dshell.internal.parser;

import org.antlr.v4.runtime.Token;

import dshell.internal.parser.TypePool.Type;

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
	 * - return null, if type not found.
	 */
	public abstract Type toType(TypePool pool);

	public static TypeSymbol toPrimitive(Token token) {
		return new PrimitiveTypeSymbol(token);
	}

	public static TypeSymbol toVoid(Token token) {
		return new VoidTypeSymbol();
	}

	public static TypeSymbol toClass(Token token) {
		return new ClassTypeSymbol(token);
	}

	public static TypeSymbol toArray(TypeSymbol typeSymbol) {
		return new ArrayTypeSymbol(typeSymbol);
	}

	public static TypeSymbol toMap(TypeSymbol typeSymbol) {
		return new MapTypeSymbol(typeSymbol);
	}

	public static TypeSymbol toFunc(TypeSymbol returnTypeSymbol, TypeSymbol[] paramTypeSymbols) {
		return new FuncTypeSymbol(returnTypeSymbol, paramTypeSymbols);
	}

	public static TypeSymbol toGeneric(Token token, TypeSymbol[] typeSymbols) {
		return new GenericTypeSymbol(token, typeSymbols);
	}

	public static class PrimitiveTypeSymbol extends TypeSymbol {
		private final String typeName;

		private PrimitiveTypeSymbol(Token token) {
			this.typeName = token.getText();
		}

		@Override
		public Type toType(TypePool pool) {
			return pool.getPrimitiveType(this.typeName);
		}
	}

	public static class VoidTypeSymbol extends TypeSymbol {
		@Override
		public Type toType(TypePool pool) {
			return TypePool.voidType;
		}
	}

	public static class ClassTypeSymbol extends TypeSymbol {
		private final String typeName;

		private ClassTypeSymbol(Token token) {
			this.typeName = token.getText();
		}

		@Override
		public Type toType(TypePool pool) {
			return pool.getClassType(this.typeName);
		}
	}

	public static class ArrayTypeSymbol extends TypeSymbol {
		private final TypeSymbol elementTypeSymbol;

		private ArrayTypeSymbol(TypeSymbol typeSymbol) {
			this.elementTypeSymbol = typeSymbol;
		}

		@Override
		public Type toType(TypePool pool) {
			return pool.createAndGetArrayTypeIfUndefined(this.elementTypeSymbol.toType(pool));
		}
	}

	public static class MapTypeSymbol extends TypeSymbol {
		private final TypeSymbol elementTypeSymbol;

		private MapTypeSymbol(TypeSymbol typeSymbol) {
			this.elementTypeSymbol = typeSymbol;
		}

		@Override
		public Type toType(TypePool pool) {
			return pool.createAndGetMapTypeIfUndefined(this.elementTypeSymbol.toType(pool));
		}
	}

	public static class FuncTypeSymbol extends TypeSymbol {
		private final TypeSymbol returnTypeSymbol;
		private final TypeSymbol[] paramtypeSymbols;

		private FuncTypeSymbol(TypeSymbol returnTypeSymbol, TypeSymbol[] paramTypeSymbols) {
			this.returnTypeSymbol = returnTypeSymbol;
			this.paramtypeSymbols = paramTypeSymbols;
		}

		@Override
		public Type toType(TypePool pool) {
			Type returnType = this.returnTypeSymbol.toType(pool);
			int size = this.paramtypeSymbols.length;
			Type[] paramTypes = new Type[size];
			for(int i = 0; i < size; i++) {
				paramTypes[i] = this.paramtypeSymbols[i].toType(pool);
			}
			return pool.createAndGetFuncTypeIfUndefined(returnType, paramTypes);
		}
	}

	public static class GenericTypeSymbol extends TypeSymbol {
		private final String baseTypeName;
		private final TypeSymbol[] typeSymbols;

		private GenericTypeSymbol(Token token, TypeSymbol[] typeSymbols) {
			this.baseTypeName = token.getText();
			this.typeSymbols = typeSymbols;
		}

		@Override
		public Type toType(TypePool pool) {
			int size = this.typeSymbols.length;
			Type[] types = new Type[size];
			for(int i =0; i < size; i++) {
				types[i] = this.typeSymbols[i].toType(pool);
			}
			return pool.createAndGetGenericTypeIfUndefined(this.baseTypeName, types);
		}
	}
}