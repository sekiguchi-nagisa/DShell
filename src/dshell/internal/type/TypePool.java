package dshell.internal.type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dshell.internal.lib.Utils;
import dshell.internal.parser.error.TypeCheckException.TypeErrorKind_OneArg;
import dshell.internal.parser.error.TypeCheckException.TypeErrorKind_ThreeArg;
import dshell.internal.parser.error.TypeCheckException.TypeLookupException;
import dshell.internal.type.DSType.BoxedPrimitiveType;
import dshell.internal.type.DSType.FuncHolderType;
import dshell.internal.type.DSType.FunctionBaseType;
import dshell.internal.type.DSType.FunctionType;
import dshell.internal.type.DSType.PrimitiveType;
import dshell.internal.type.DSType.UnresolvedType;
import dshell.internal.type.DSType.VoidType;
import dshell.internal.type.DSType.RootClassType;
import dshell.internal.type.ParametricType.ParametricGenericType;
import dshell.lang.Errno;
import dshell.lang.Errno.DerivedFromErrnoException;

/**
 * It contains builtin types ant user defined types.
 * @author skgchxngsxyz-osx
 *
 */
public class TypePool {
	/**
	 * prefix of generated class.
	 */
	public final static String generatedPackage = "dshell/generated/";

	/**
	 * package name for generated class.
	 */
	public final static String generatedClassPackage = generatedPackage + "class/";

	/**
	 * package name for generated func interface.
	 */
	public final static String generatedFuncPackage = generatedPackage + "func/";

	private static int funcNameSuffix = -1;

	/**
	 * name prefix for top level class.
	 */
	public final static String toplevelClassName = generatedPackage + "toplevel";

	private static int topLevelClassSuffix = -1;

	/**
	 * used for type checker.
	 * it is default value of ExprNode type.
	 */
	public final static DSType unresolvedType = new UnresolvedType();

	/**
	 * Equivalent to java void.
	 */
	public final static VoidType voidType = new VoidType();

	/**
	 * Equivalent to java long.
	 */
	public final PrimitiveType intType;

	/**
	 * Equivalent to java double.
	 */
	public final PrimitiveType floatType;

	/**
	 * Equivalent to java boolean.
	 */
	public final PrimitiveType booleanType;

	/**
	 * Represents D-Shell root class type.
	 * It is equivalent to java Object.
	 */
	public final DSType objectType = new RootClassType();

	/**
	 * Represents D-Shell string type.
	 */
	public final ClassType stringType;

	/**
	 * Represents D-Shell root exception type.
	 */
	public final ClassType exceptionType;

	/**
	 * represent D-Shell Task type.
	 */
	public final ClassType taskType;

	public final GenericBaseType baseArrayType;
	public final GenericBaseType baseMapType;
	public final GenericBaseType basePairType;

	public final FunctionBaseType baseFuncType;

	/**
	 * boxed primitive type. used for generic type.
	 */
	private final BoxedPrimitiveType boxedIntType;
	private final BoxedPrimitiveType boxedFloaType;
	private final BoxedPrimitiveType boxedBooleanType;
	/**
	 * type name to type translation table
	 */
	protected final HashMap<String, DSType> typeMap;

	protected Set<FunctionType> ungeneratedFucnTypeSet;

	public TypePool() {
		this.typeMap = new HashMap<>();

		this.setTypeAndThrowIfDefined(voidType);
		this.intType         = (PrimitiveType) this.setTypeAndThrowIfDefined(new PrimitiveType("int", "long"));
		this.floatType       = (PrimitiveType) this.setTypeAndThrowIfDefined(new PrimitiveType("float", "double"));
		this.booleanType     = (PrimitiveType) this.setTypeAndThrowIfDefined(new PrimitiveType("boolean", "boolean"));
		this.stringType          = (ClassType) this.setTypeAndThrowIfDefined(TypeInitializer.init_StringWrapper(this));
		this.exceptionType       = (ClassType) this.setTypeAndThrowIfDefined(TypeInitializer.init_Exception(this));
		this.taskType            = (ClassType) this.setTypeAndThrowIfDefined(TypeInitializer.init_Task(this));
		this.baseArrayType = (GenericBaseType) this.setTypeAndThrowIfDefined(TypeInitializer.init_GenericArray(this));
		this.baseMapType   = (GenericBaseType) this.setTypeAndThrowIfDefined(TypeInitializer.init_GenericMap(this));
		this.basePairType  = (GenericBaseType) this.setTypeAndThrowIfDefined(TypeInitializer.init_GenericPair(this));
		this.baseFuncType = (FunctionBaseType) this.setTypeAndThrowIfDefined(new FunctionBaseType());

		this.setTypeAndThrowIfDefined(TypeInitializer.init_IntArray(this));
		this.setTypeAndThrowIfDefined(TypeInitializer.init_FloatArray(this));
		this.setTypeAndThrowIfDefined(TypeInitializer.init_BooleanArray(this));

		this.setTypeAndThrowIfDefined(TypeInitializer.init_KeyNotFoundException(this));
		this.setTypeAndThrowIfDefined(TypeInitializer.init_OutOfIndexException(this));
		this.setTypeAndThrowIfDefined(TypeInitializer.init_ArithmeticException(this));
		this.setTypeAndThrowIfDefined(TypeInitializer.init_TypeCastException(this));
		this.setTypeAndThrowIfDefined(TypeInitializer.init_DShellException(this));
		this.setTypeAndThrowIfDefined(TypeInitializer.init_NullException(this));
		this.setTypeAndThrowIfDefined(TypeInitializer.init_MultipleException(this));
		this.setTypeAndThrowIfDefined(TypeInitializer.init_DerivedFromErrnoException(this));

		this.setTypeAndThrowIfDefined(TypeInitializer.init_InputStream(this));
		this.setTypeAndThrowIfDefined(TypeInitializer.init_OutputStream(this));

		this.boxedIntType     = new BoxedPrimitiveType(this.intType);
		this.boxedFloaType    = new BoxedPrimitiveType(this.floatType);
		this.boxedBooleanType = new BoxedPrimitiveType(this.booleanType);

		this.loadAndSetErrnoClass();
	}

	private DSType setTypeAndThrowIfDefined(DSType type) {
		if(this.typeMap.containsKey(type.getTypeName())) {
			Utils.fatal(1, type.getTypeName() + " is already defined");
		}
		this.typeMap.put(type.getTypeName(), type);
		return type;
	}

	/**
	 * import Errno Exception class
	 */
	private void loadAndSetErrnoClass() {
		DSType superType = this.getType(DerivedFromErrnoException.class.getSimpleName());
		Set<Class<? extends DerivedFromErrnoException>> classSet = Errno.getExceptionClassSet();
		for(Class<? extends DerivedFromErrnoException> clazz : classSet) {
			String[][] ce = {
					{},
					{"String"},
			};

			String[][] fe = null;

			String[][] me = null;

			String op = null;
			DSType type = BuiltinClassType.createType(0, this, clazz.getSimpleName(), 
					clazz.getName().replace('.', '/'), superType, true, ce, fe, me, op);
			this.setTypeAndThrowIfDefined(type);
		}
	}

	// type getter api
	/**
	 * 
	 * @param typeName
	 * @return
	 * - if type is not defined, return unresolvedType.
	 * cannot get generic base type.
	 */
	public DSType getType(String typeName) {
		DSType type = this.typeMap.get(typeName);
		if(type instanceof GenericBaseType) {
			throw new TypeLookupException(TypeErrorKind_OneArg.NotUseGeneric, type.getTypeName());
		}
		return type == null ? TypePool.unresolvedType : type;
	}

	/**
	 * get type except generic base type.
	 * @param typeName
	 * @return
	 * - if type is undefined, throw exception.
	 */
	public DSType getTypeAndThrowIfUndefined(String typeName) {
		DSType type = this.getType(typeName);
		if(type instanceof UnresolvedType) {
			throw new TypeLookupException(TypeErrorKind_OneArg.UndefinedType, typeName);
		}
		return type;
	}

	/**
	 * get generic base type.
	 * @param typeName
	 * @return
	 * - if type is undefined, throw exception.
	 */
	public GenericBaseType getGenericBaseType(String typeName, int elementSize) {
		DSType type = this.typeMap.get(typeName);
		if(type instanceof GenericBaseType) {
			GenericBaseType baseType = (GenericBaseType) type;
			final int requireElementSize = baseType.getElementSize();
			if(requireElementSize != elementSize) {
				throw new TypeLookupException(TypeErrorKind_ThreeArg.UnmatchElement, 
						typeName, requireElementSize, elementSize);
			}
			return (GenericBaseType) type;
		}
		throw new TypeLookupException(TypeErrorKind_OneArg.NotGenericBase, typeName);
	}

	/**
	 * get primitive type
	 * @param typeName
	 * @return
	 * - if undefined, throw exception.
	 */
	public PrimitiveType getPrimitiveType(String typeName) {
		DSType type = this.getTypeAndThrowIfUndefined(typeName);
		if(type instanceof PrimitiveType) {
			return (PrimitiveType) type;
		}
		throw new TypeLookupException(TypeErrorKind_OneArg.NotPrimitive, typeName);
	}

	/**
	 * get boxed primitive type.
	 * @param type
	 * @return
	 */
	public BoxedPrimitiveType getBoxedPrimitiveType(PrimitiveType type) {
		if(type.equals(this.intType)) {
			return this.boxedIntType;
		}
		if(type.equals(this.floatType)) {
			return this.boxedFloaType;
		}
		if(type.equals(this.booleanType)) {
			return this.boxedBooleanType;
		}
		Utils.fatal(1, "unsuppoirted primitive type: " + type);
		return null;
	}

	/**
	 * get class type.
	 * @param typeName
	 * @return
	 * - if undefined, throw exception.
	 */
	public ClassType getClassType(String typeName) {
		DSType type = this.getTypeAndThrowIfUndefined(typeName);
		if(type instanceof ClassType) {
			return (ClassType) type;
		}
		throw new TypeLookupException(TypeErrorKind_OneArg.NotClass, typeName);
	}

	// type creator api
	/**
	 * create class and set to typemap.
	 * @param className
	 * - user defined class name.
	 * @param superType
	 * @return
	 * - generated class type
	 */
	public ClassType createAndSetClassType(String className, DSType superType) {
		if(!superType.allowExtends()) {
			throw new TypeLookupException(TypeErrorKind_OneArg.Nonheritable, superType.getTypeName());
		}
		if(!(this.getType(className) instanceof UnresolvedType)) {
			throw new TypeLookupException(TypeErrorKind_OneArg.DefinedType, className);
		}
		ClassType classType = new UserDefinedClassType(className, generatedClassPackage + className, superType, true);
		this.typeMap.put(className, classType);
		return classType;
	}

	/**
	 * Currently user defined generic class not supported.
	 * Future may be supported.
	 * @param baseTypeName
	 * @param typeList
	 * @return
	 */
	public DSType createAndGetReifiedTypeIfUndefined(String baseTypeName, List<DSType> typeList) {
		GenericBaseType baseType = this.getGenericBaseType(baseTypeName, typeList.size());
		String typeName = toReifiedTypeName(baseType, typeList);
		DSType genericType = this.getType(typeName);
		if(genericType instanceof UnresolvedType) {
			genericType = new ReifiedType(this, typeName, baseType, typeList);
			this.typeMap.put(typeName, genericType);
		}
		return genericType;
	}

	public FunctionType createAndGetFuncTypeIfUndefined(DSType returnType, List<DSType> paramTypeList) {
		String typeName = toFuncTypeName(returnType, paramTypeList);
		DSType funcType = this.getType(typeName);
		if(funcType instanceof UnresolvedType) {
			String internalName = generatedFuncPackage + "FuncType" + ++funcNameSuffix;
			funcType = new FunctionType(typeName, internalName, returnType, paramTypeList);
			this.typeMap.put(typeName, funcType);
			this.addUngeneratedFuncType((FunctionType) funcType);
		}
		return (FunctionType) funcType;
	}

	public FuncHolderType createFuncHolderType(FunctionType funcType, String funcName) {
		String typeName = "FuncHolder" + ++funcNameSuffix + "of" + funcType.getTypeName();
		String internalName = generatedFuncPackage + "FuncHolder_" + funcName + funcNameSuffix;
		return new FuncHolderType(typeName, internalName, funcType);
	}

	// type name creator api.
	/**
	 * create generic type name.
	 * @param baseType
	 * - may be Array, Map or Pair.
	 * @param typeList
	 * - not null
	 * @return
	 */
	public static String toReifiedTypeName(GenericBaseType baseType, List<DSType> typeList) {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append(baseType.getTypeName());
		sBuilder.append("<");
		int size = typeList.size();
		for(int i = 0; i < size; i++) {
			if(i > 0) {
				sBuilder.append(",");
			}
			sBuilder.append(typeList.get(i).getTypeName());
		}
		sBuilder.append(">");
		return sBuilder.toString();
	}

	/**
	 * create func type name from return type and param types
	 * @param returnType
	 * - not null
	 * @param paramTypeList
	 * - if has no parameters, it is empty list
	 * @return
	 */
	public static String toFuncTypeName(DSType returnType, List<DSType> paramTypeList) {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append("Func<");
		sBuilder.append(returnType.toString());

		int size = paramTypeList.size();
		if(size > 0) {
			sBuilder.append(",[");
			for(int i = 0; i < size; i++) {
				if(i > 0) {
					sBuilder.append(",");
				}
				sBuilder.append(paramTypeList.get(i).toString());
			}
			sBuilder.append("]");
		}
		sBuilder.append(">");
		return sBuilder.toString();
	}

	public String createToplevelClassName() {
		return toplevelClassName + ++topLevelClassSuffix;
	}

	private void addUngeneratedFuncType(FunctionType funcType) {
		if(this.ungeneratedFucnTypeSet == null) {
			this.ungeneratedFucnTypeSet = new HashSet<>();
		}
		this.ungeneratedFucnTypeSet.add(funcType);
	}

	/**
	 * for code generator
	 * @return
	 */
	public Set<FunctionType> removeUngeneratedFuncTypeSet() {
		Set<FunctionType> set = this.ungeneratedFucnTypeSet;
		this.ungeneratedFucnTypeSet = null;
		return set;
	}

	/**
	 * parse type name and get type.
	 * @param typeName
	 * @return
	 * - if has no type, throw exception.
	 */
	public DSType parseTypeName(String typeName) {	//FIXME: only support generic type,
		if(typeName.indexOf('<') == -1) {
			if(typeName.startsWith("@")) {
				return new ParametricType(typeName);
			}
			return this.getTypeAndThrowIfUndefined(typeName);
		}
		return new ParseContext(this, typeName).parse();
	}

	/**
	 * type parser for generic type.
	 * @author skgchxngsxyz-osx
	 *
	 */
	private class ParseContext {
		private final TypePool pool;
		private final String source;
		private final int size;
		private int index;

		private ParseContext(TypePool pool, String source) {
			this.pool = pool;
			this.source = source;
			this.size = source.length();
			this.index = 0;
		}

		private DSType parse() {
			final int startIndex = this.index;
			for(; this.index < this.size; this.index++) {
				char ch = this.source.charAt(this.index);
				switch(ch) {
				case '<':
					String baseTypeName = this.source.substring(startIndex, this.index++);
					List<DSType> typeList = new ArrayList<>();
					this.consumeSpace();
					typeList.add(this.parse());
					while(this.source.charAt(this.index) != '>') {
						this.consumeSeparator();
						typeList.add(this.parse());
					}
					return this.createReifiedType(baseTypeName, typeList);
				case '>':
				case ',': {
					String typeName = this.source.substring(startIndex, this.index);
					if(typeName.startsWith("@")) {
						return new ParametricType(typeName);
					}
					return this.pool.getTypeAndThrowIfUndefined(typeName);
				}
				case ' ': {
					String typeName = this.source.substring(startIndex, this.index);
					this.consumeSpace();
					if(typeName.startsWith("@")) {
						return new ParametricType(typeName);
					}
					return this.pool.getTypeAndThrowIfUndefined(typeName);
				}
				default:
					break;
				}
			}
			Utils.fatal(1, "illegal type name: " + this.source);
			return null;
		}

		/**
		 * consume space and increment index.
		 */
		private void consumeSpace() {
			for(; this.index < this.size; this.index++) {
				if(this.source.charAt(this.index) != ' ') {
					break;
				}
			}
		}

		/**
		 * consume separator (space or ,) and increment index.
		 */
		private void consumeSeparator() {
			int separatorCount = 0;
			for(; this.index < this.size; this.index++) {
				switch(this.source.charAt(this.index)) {
				case ' ':
					break;
				case ',':
					separatorCount++;
					break;
				default:
					if(separatorCount != 0) {
						Utils.fatal(1, "illegal separator count: " + separatorCount + ", " + this.source);
					}
					return;
				}
			}
			Utils.fatal(1, "found problem: " + this.source);
		}

		/**
		 * create reified type
		 * @param baseTypeName
		 * - may be Array, Map or Pair
		 * @param elementTypeList
		 * - may contain parametric type.
		 * @return
		 * - if elementTypeList has parametric type, return parametric generic type.
		 */
		private DSType createReifiedType(String baseTypeName, List<DSType> elementTypeList) {
			boolean foundParametricType = false;
			for(DSType elementType : elementTypeList) {
				if((elementType instanceof ParametricType) || (elementType instanceof ParametricGenericType)) {
					foundParametricType = true;
					break;
				}
			}
			if(foundParametricType) {
				return new ParametricGenericType(baseTypeName, elementTypeList);
			} else {
				return this.pool.createAndGetReifiedTypeIfUndefined(baseTypeName, elementTypeList);
			}
		}
	}

	/**
	 * for test
	 * @param args
	 */
	public static void main(String[] args) {
		TypePool pool = new TypePool();
		String source = "Array<Array<@T>>";
		DSType parsedType = pool.parseTypeName(source);
		System.out.println(parsedType);
	}
}
