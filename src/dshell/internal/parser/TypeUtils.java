package dshell.internal.parser;

import java.util.List;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import dshell.internal.type.DSType.PrimitiveType;
import dshell.internal.type.TypePool;
import dshell.internal.type.DSType;

/**
 * helper utilities for type descriptor generation.
 * @author skgchxngsxyz-opensuse
 *
 */
public class TypeUtils {
	/**
	 * create type descriptor from type.
	 * @param type
	 * @return
	 */
	public static Type toTypeDescriptor(DSType type) {
		return toTypeDescriptor(type.getInternalName());
	}

	/**
	 * create type descriptor from internal class name.
	 * @param internalName
	 * fully qualified class name.
	 * @return
	 */
	public static Type toTypeDescriptor(String internalName) {
		if(internalName.equals("long")) {
			return Type.LONG_TYPE;
		} else if(internalName.equals("double")) {
			return Type.DOUBLE_TYPE;
		} else if(internalName.equals("boolean")) {
			return Type.BOOLEAN_TYPE;
		} else if(internalName.equals("void")) {
			return Type.VOID_TYPE;
		} else {
			return Type.getType( "L" + internalName + ";");
		}
	}

	/**
	 * used for JavaByteCodeGen#visitCatchNode.
	 * @param exceptionType
	 * @return
	 * if exception type is Exception, return type descriptor of java.lang.Throwable
	 */
	public static Type toExceptionTypeDescriptor(DSType exceptionType) {
		if(exceptionType.getTypeName().equals("Exception")) {
			return Type.getType(Throwable.class);
		}
		return toTypeDescriptor(exceptionType);
	}

	// for method descriptor generation
	/**
	 * create mrthod descriptor from types.
	 * @param returnType
	 * @param methodName
	 * @param paramTypeList
	 * @return
	 */
	public static Method toMethodDescriptor(DSType returnType, String methodName, List<DSType> paramTypeList) {
		int size = paramTypeList.size();
		Type[] paramtypeDecs = new Type[size];
		for(int i = 0; i < size; i++) {
			paramtypeDecs[i] = toTypeDescriptor(paramTypeList.get(i));
		}
		Type returnTypeDesc = toTypeDescriptor(returnType);
		return new Method(methodName, returnTypeDesc, paramtypeDecs);
	}

	public static Method toConstructorDescriptor(List<DSType> paramTypeList) {
		return toMethodDescriptor(TypePool.voidType, "<init>", paramTypeList);
	}

	public static Method toArrayConstructorDescriptor(DSType elementType) {
		Type returnTypeDesc = Type.VOID_TYPE;
		Type elementTypeDesc = toTypeDescriptor(elementType);
		if(!(elementType instanceof PrimitiveType)) {
			elementTypeDesc = Type.getType(Object.class);
		}
		Type paramTypeDesc = Type.getType("[" + elementTypeDesc.getDescriptor());
		Type[] paramtypeDecs = new Type[]{paramTypeDesc};
		return new Method("<init>", returnTypeDesc, paramtypeDecs);
	}

	public static Method toMapConstructorDescriptor() {
		Type returnTypeDesc = Type.VOID_TYPE;
		Type keysTypeDesc = Type.getType(String[].class);
		Type valuesTypeDesc = Type.getType(Object[].class);
		Type[] paramTypeDescs = new Type[] {keysTypeDesc, valuesTypeDesc};
		return new Method("<init>", returnTypeDesc, paramTypeDescs);
	}

	public static Method toPairConstructorDescriptor() {
		Type returnTypeDesc = Type.VOID_TYPE;
		Type paramTypeDesc = Type.getType(Object.class);
		Type[] paramTypeDescs = new Type[] {paramTypeDesc, paramTypeDesc};
		return new Method("<init>", returnTypeDesc, paramTypeDescs);
	}

	public static Method toExceptionWrapperDescriptor() {
		Type returnTypeDesc = Type.getType(dshell.lang.Exception.class);
		Type paramTypeDesc = Type.getType(Throwable.class);
		return new Method("wrapException", returnTypeDesc, new Type[]{paramTypeDesc});
	}
}
