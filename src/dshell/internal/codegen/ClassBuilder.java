package dshell.internal.codegen;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.runtime.Token;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.commons.Method;

import dshell.internal.lib.DShellClassLoader;
import dshell.internal.lib.GlobalVariableTable;
import dshell.internal.lib.Utils;
import dshell.internal.parser.TypeUtils;
import dshell.internal.type.TypePool;
import dshell.internal.type.UserDefinedClassType;
import dshell.internal.type.CalleeHandle.MethodHandle;
import dshell.internal.type.CalleeHandle.StaticFunctionHandle;
import dshell.internal.type.DSType.FuncHolderType;
import dshell.internal.type.DSType.FunctionType;
import dshell.internal.type.DSType;
import dshell.lang.GenericPair;

/**
 * used for class and function wrapper class generation.
 * @author skgchxngsxyz-osx
 *
 */
public class ClassBuilder extends ClassWriter implements Opcodes {
	/**
	 * name prefix for top level class.
	 */
	private final static String className = "toplevel";

	private static int topLevelClassPrefix = -1;

	private final String internalClassName;

	/**
	 * create new class builder for class generation.
	 * @param classType
	 * - target class type.
	 * @param sourceName
	 * - class source code, may be null.
	 */
	public ClassBuilder(UserDefinedClassType classType, String sourceName) {
		super(ClassWriter.COMPUTE_FRAMES);
		this.internalClassName = classType.getInternalName();
		this.visit(V1_7, ACC_PUBLIC, this.internalClassName, null, classType.getSuperType().getInternalName(), null);
		this.visitSource(sourceName, null);
	}

	/**
	 * create new class builder for top level class generation.
	 */
	public ClassBuilder(String sourceName) {
		super(ClassWriter.COMPUTE_FRAMES);
		this.internalClassName = Utils.genUniqueClassName(TypePool.genClassPrefix, className, ++topLevelClassPrefix);
		this.visit(V1_7, ACC_PUBLIC | ACC_FINAL, this.internalClassName, null, "java/lang/Object", null);
		this.visitSource(sourceName, null);
	}

	/**
	 * create new class builder for function holder class.
	 * @param holderType
	 * - function holder type.
	 * @param sourceName
	 * function source code, may be null.
	 */
	public ClassBuilder(FuncHolderType holderType, String sourceName) {
		super(ClassWriter.COMPUTE_FRAMES);
		this.internalClassName = holderType.getInternalName();
		FunctionType superType = (FunctionType) holderType.getFieldHandle().getFieldType();
		this.visit(V1_7, ACC_PUBLIC | ACC_FINAL, this.internalClassName, null, "java/lang/Object", new String[]{superType.getInternalName()});
		this.visitSource(sourceName, null);
	}

	/**
	 * create new generator adapter for method generation.
	 * @param handle
	 * - if null, generate adapter for top level wrapper func.
	 * @return
	 */
	public MethodBuilder createNewMethodBuilder(MethodHandle handle) {
		if(handle == null) {
			Method methodDesc = Method.getMethod("void invoke()");
			return new MethodBuilder(ACC_PUBLIC | ACC_FINAL | ACC_STATIC, methodDesc, this);
		}
		if(handle instanceof StaticFunctionHandle) {
			return new MethodBuilder(ACC_PUBLIC | ACC_STATIC, handle.getMethodDesc(), this);
		}
		return new MethodBuilder(ACC_PUBLIC, handle.getMethodDesc(), this);
	}

	/**
	 * generate and load class.
	 * must call it only once.
	 * @param classLoader
	 * @return
	 * - generated class.
	 */
	public Class<?> generateClass(DShellClassLoader classLoader) {
		this.visitEnd();
		return classLoader.definedAndLoadClass(this.internalClassName, this.toByteArray());
	}

	@Override
	public String toString() {
		return this.internalClassName;
	}

	/**
	 * wrapper class of generator adapter
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class MethodBuilder extends GeneratorAdapter {
		/**
		 * contains loop statement label(break, continue).
		 * left is break label and right is continue label.
		 */
		protected final Deque<GenericPair<Label, Label>> loopLabels;

		/**
		 * used for try catch statement.
		 */
		protected final Deque<TryCatchLabel> tryLabels;

		/**
		 * contains variable scope
		 */
		protected final VarScopes varScopes;

		/**
		 * represent current line number.
		 * used for stack trace.
		 */
		protected int currentLineNum = -1;

		protected MethodBuilder(int access, Method method, ClassVisitor cv) {
			super(Opcodes.ASM4, toMethodVisitor(access, method, cv),
					access, method.getName(), method.getDescriptor());
			this.loopLabels = new ArrayDeque<>();
			this.tryLabels = new ArrayDeque<>();
			int startIndex = 0;
			if((access & ACC_STATIC) != ACC_STATIC) {
				startIndex = 1;
			}
			this.varScopes = new VarScopes(startIndex);
		}

		/**
		 * helper method for method visitor generation
		 * @param access
		 * @param method
		 * @param cv
		 * @return
		 */
		private static MethodVisitor toMethodVisitor(int access, Method method, ClassVisitor cv) {
			MethodVisitor visitor = 
					cv.visitMethod(access, method.getName(), method.getDescriptor(), null, null);
			JSRInlinerAdapter inlinerAdapter = 
					new JSRInlinerAdapter(visitor, access, method.getName(), method.getDescriptor(), null, null);
			return inlinerAdapter;
		}

		/**
		 * get loop labels
		 * @return
		 * - stack of label pair. pair's left value is break label, right value is continue label.
		 */
		public Deque<GenericPair<Label, Label>> getLoopLabels() {
			return this.loopLabels;
		}

		public Deque<TryCatchLabel> getTryLabels() {
			return this.tryLabels;
		}

		public TryCatchLabel createNewTryLabel(boolean existFinally) {
			Label startLabel = this.newLabel();
			Label endLabel = this.newLabel();
			Label finallyLabel = null;
			if(existFinally) {
				finallyLabel = this.newLabel();
			}
			return new TryCatchLabel(startLabel, endLabel, finallyLabel);
		}

		/**
		 * create new ret adrr entry and store return address
		 */
		public void storeReturnAddr() {
			VarEntry entry = this.varScopes.addRetAddressEntry();
			this.visitVarInsn(ASTORE, entry.getVarIndex());
			this.tryLabels.peek().retAddrEntry = entry;
		}

		public void returnFromFinally() {
			VarEntry entry = this.tryLabels.peek().retAddrEntry;
			this.visitVarInsn(RET, entry.getVarIndex());
		}

		public void jumpToFinally() {
			this.jumpToFinally(this.tryLabels.peek());
		}

		private void jumpToFinally(TryCatchLabel label) {
			Label finallyLabel = label.getFinallyLabel();
			if(finallyLabel != null) {
				this.visitJumpInsn(JSR, finallyLabel);
			}
		}

		public void jumpToMultipleFinally() {
			for(TryCatchLabel label : this.tryLabels) {
				this.jumpToFinally(label);
			}
		}

		/**
		 * generate pop instruction.
		 * if type is long or double, generate pop2.
		 * @param type
		 * - stack top type. if type is void, not generate pop ins
		 */
		public void pop(Type type) {
			if(type.equals(Type.LONG_TYPE) || type.equals(Type.DOUBLE_TYPE)) {
				this.pop2();
			} else if(!type.equals(Type.VOID_TYPE)) {
				this.pop();
			}
		}

		public void createNewLocalScope() {
			this.varScopes.createNewScope();
		}

		public void removeCurrentLocalScope() {
			this.varScopes.removeCurrentScope();
		}

		public void defineArgument(String argName, DSType argType) {
			assert this.varScopes.scopeDepth() == 2;
			this.varScopes.addVarEntry(argName, argType);
		}

		/**
		 * create new global variable and store value directly to global var table.
		 * @param varName
		 * @param type
		 * - must not be primitive type.
		 * @param value
		 * - only support object value.
		 */
		public void createNewGlobalVarAndStoreValue(String varName, DSType type, Object value) {
			VarEntry entry = this.varScopes.addVarEntry(varName, type);
			if(!entry.isGlobaVar()) {
				Utils.fatal(1, "must be global variable: " + varName + " : " + type);
			}
			GlobalVariableTable.objectVarTable[entry.getVarIndex()] = value;
		}

		public void createNewVarAndStoreValue(String varName, DSType type) {
			VarEntry entry = this.varScopes.addVarEntry(varName, type);
			// global variable
			if(entry.isGlobaVar()) {
				this.storeValueToGlobal(entry.getVarIndex(), type);
				return;
			}
			// local variable
			Type typeDesc = TypeUtils.toTypeDescriptor(type);
			this.visitVarInsn(typeDesc.getOpcode(ISTORE), entry.getVarIndex());
			return;
		}

		public void storeValueToVar(String varName, DSType type) {
			VarEntry entry = this.varScopes.getVarEntry(varName);
			assert entry != null : "undefined variable: " + varName;
			// global variable
			if(entry.isGlobaVar()) {
				this.storeValueToGlobal(entry.getVarIndex(), type);
				return;
			}
			// local variable
			Type typeDesc = TypeUtils.toTypeDescriptor(type);
			this.visitVarInsn(typeDesc.getOpcode(ISTORE), entry.getVarIndex());
		}

		public void loadValueFromVar(String varName, DSType type) {
			VarEntry entry = this.varScopes.getVarEntry(varName);
			assert entry != null : "undefined variable: " + varName;
			// global variable
			if(entry.isGlobaVar()) {
				this.loadValueFromGlobal(entry.getVarIndex(), type);
				return;
			}
			// local variable
			Type typeDesc = TypeUtils.toTypeDescriptor(type);
			this.visitVarInsn(typeDesc.getOpcode(ILOAD), entry.getVarIndex());
		}

		private void storeValueToGlobal(int index, DSType type) {
			Type typeDesc = TypeUtils.toTypeDescriptor(type);
			Type ownerTypeDesc = Type.getType(GlobalVariableTable.class);
			switch(typeDesc.getSort()) {
			case Type.LONG:
				this.getStatic(ownerTypeDesc, "longVarTable", Type.getType(long[].class));
				this.push(index);
				this.dup2X2();
				this.pop2();
				this.arrayStore(typeDesc);
				break;
			case Type.DOUBLE:
				this.getStatic(ownerTypeDesc, "doubleVarTable", Type.getType(double[].class));
				this.push(index);
				this.dup2X2();
				this.pop2();
				this.arrayStore(typeDesc);
				break;
			case Type.BOOLEAN:
				this.getStatic(ownerTypeDesc, "booleanVarTable", Type.getType(boolean[].class));
				this.swap();
				this.push(index);
				this.swap();
				this.arrayStore(typeDesc);
				break;
			case Type.OBJECT:
				this.getStatic(ownerTypeDesc, "objectVarTable", Type.getType(Object[].class));
				this.swap();
				this.push(index);
				this.swap();
				this.arrayStore(typeDesc);
				break;
			default:
				throw new RuntimeException("illegal type: " + type);
			}
		}

		private void loadValueFromGlobal(int index, DSType type) {
			Type typeDesc = TypeUtils.toTypeDescriptor(type);
			Type ownerTypeDesc = Type.getType(GlobalVariableTable.class);
			switch(typeDesc.getSort()) {
			case Type.LONG:
				this.getStatic(ownerTypeDesc, "longVarTable", Type.getType(long[].class));
				this.push(index);
				this.arrayLoad(typeDesc);
				break;
			case Type.DOUBLE:
				this.getStatic(ownerTypeDesc, "doubleVarTable", Type.getType(double[].class));
				this.push(index);
				this.arrayLoad(typeDesc);
				break;
			case Type.BOOLEAN:
				this.getStatic(ownerTypeDesc, "booleanVarTable", Type.getType(boolean[].class));
				this.push(index);
				this.arrayLoad(typeDesc);
				break;
			case Type.OBJECT:
				this.getStatic(ownerTypeDesc, "objectVarTable", Type.getType(Object[].class));
				this.push(index);
				this.arrayLoad(Type.getType(Object.class));
				this.visitTypeInsn(CHECKCAST, typeDesc.getInternalName());
				break;
			default:
				throw new RuntimeException("illegal type: " + type);
			}
		}

		/**
		 * generate line number.
		 * @param token
		 */
		public void setLineNum(Token token) {
			if(token == null) {
				return;
			}
			int lineNum = token.getLine();
			if(lineNum > this.currentLineNum) {
				this.visitLineNumber(lineNum, this.mark());
			}
		}
	}

	public static class TryCatchLabel {
		private final Label startLabel;
		private final Label endLabel;
		private final Label finallyLabel;

		private VarEntry retAddrEntry;

		/**
		 * 
		 * @param startLabel
		 * @param endLabel
		 * @param finallyLabel
		 * if not found finally, null
		 */
		private TryCatchLabel(Label startLabel, Label endLabel, Label finallyLabel) {
			this.startLabel = startLabel;
			this.endLabel = endLabel;
			this.finallyLabel = finallyLabel;
		}

		public Label getStartLabel() {
			return this.startLabel;
		}

		public Label getEndLabel() {
			return this.endLabel;
		}

		/**
		 * 
		 * @return
		 * may be null
		 */
		public Label getFinallyLabel() {
			return this.finallyLabel;
		}
	}

	private static class VarScopes {
		private int retAddrNameSuffix = -1;

		/**
		 * contains local variable scopes
		 */
		private final Deque<VarScope> scopes;

		/**
		 * local variable start index.
		 * if this builder represents static method or static initializer, index = 0.
		 * if this builder represents instance method or constructor, index = 1;
		 */
		protected final int startVarIndex;

		private VarScopes(int startIndex) {
			this.scopes = new ArrayDeque<>();
			this.scopes.push(new GlobalVarScope());
			this.startVarIndex = startIndex;
		}

		/**
		 * add local variable to scope. 
		 * 
		 * @param varName
		 * - variable name.
		 * @param type
		 * - variable's value type.
		 * @return
		 * - local var index.
		 * throw if variable has already defined in this scope.
		 */
		public VarEntry addVarEntry(String varName, DSType type) {
			return this.scopes.peek().addVarEntry(varName, type);
		}

		public VarEntry addRetAddressEntry() {
			return this.scopes.peek().addRetAddressEntry("$RetAddr$_" + ++this.retAddrNameSuffix);
		}

		/**
		 * get local variable index.
		 * @param varName
		 * - variable index.
		 * @return
		 * - if has no variable, return null.
		 */
		public VarEntry getVarEntry(String varName) {
			return this.scopes.peek().getVarEntry(varName);
		}

		public void createNewScope() {
			int startIndex = this.startVarIndex;
			if(this.scopes.size() > 1) {
				startIndex = this.scopes.peek().getEndIndex();
			}
			this.scopes.push(new LocalVarScope(this.scopes.peek(), startIndex));
		}

		public void removeCurrentScope() {
			if(this.scopes.size() > 1) {
				this.scopes.pop();
			}
		}

		public int scopeDepth() {
			return this.scopes.size();
		}
	}

	private static interface VarScope {
		/**
		 * add local variable to scope. 
		 * 
		 * @param varName
		 * - variable name.
		 * @param type
		 * - variable's value type.
		 * @return
		 * - local var index.
		 * throw if variable has already defined in this scope.
		 */
		public VarEntry addVarEntry(String varName, DSType type);

		/**
		 * add local variable which contains return address. 
		 * for jsr/ret instruction
		 * @return
		 */

		/**
		 * add local variable which contains return address. 
		 * for jsr/ret instruction
		 * @param varName
		 * @return
		 */
		public VarEntry addRetAddressEntry(String varName);

		/**
		 * get local variable index.
		 * @param varName
		 * - variable index.
		 * @return
		 * - if has no var entry, return null.
		 */
		public VarEntry getVarEntry(String varName);

		/**
		 * get start index of local variable in this scope.
		 * @return
		 */
		public int getStartIndex();

		/**
		 * get end index of local variable in this scope.
		 * @return
		 */
		public int getEndIndex();
	}

	private static class LocalVarScope implements VarScope {
		/**
		 * parent var scope. may be null if it is root scope.
		 */
		private final VarScope parentScope;

		/**
		 * represent start index of local variable in this scope.
		 */
		private final int localVarBaseIndex;

		/**
		 * represent local variable index.
		 * after adding new local variable, increment this index by value size.
		 */
		private int currentLocalVarIndex;

		/**
		 * contain var entry. key is variable name.
		 */
		protected final Map<String, VarEntry> varEntryMap;

		protected LocalVarScope(VarScope parentScope, int localVarBaseIndex) {
			this.parentScope = parentScope;
			this.varEntryMap = new HashMap<>();
			this.localVarBaseIndex = localVarBaseIndex;
			this.currentLocalVarIndex = this.localVarBaseIndex;
		}

		@Override
		public VarEntry addVarEntry(String varName, DSType type) {
			int valueSize = TypeUtils.toTypeDescriptor(type).getSize();
			return this.addVarEntry(varName, valueSize);
		}

		@Override
		public VarEntry addRetAddressEntry(String varName) {
			return this.addVarEntry(varName, 1);
		}

		/**
		 * 
		 * @param varName
		 * @param valueSize
		 * size of variable, long, double is 2, otherwise 1
		 * @return
		 */
		protected VarEntry addVarEntry(String varName, int valueSize) {
			assert !this.varEntryMap.containsKey(varName) : varName + " is already defined";
			assert valueSize > 0;
			int index = this.currentLocalVarIndex;
			VarEntry entry = new VarEntry(index, false);
			this.varEntryMap.put(varName, entry);
			this.currentLocalVarIndex += valueSize;
			return entry;
		}

		@Override
		public VarEntry getVarEntry(String varName) {
			VarEntry entry = this.varEntryMap.get(varName);
			if(entry == null) {
				return this.parentScope.getVarEntry(varName);
			}
			return entry;
		}

		@Override
		public int getStartIndex() {
			return this.localVarBaseIndex;
		}

		@Override
		public int getEndIndex() {
			return this.currentLocalVarIndex;
		}
	}

	/**
	 * wrapper class of global variable table
	 * @author skgchxngsxyz-osx
	 *
	 */
	private static class GlobalVarScope extends LocalVarScope {
		private GlobalVarScope() {
			super(null, 0);
		}

		@Override
		public VarEntry addVarEntry(String varName, DSType type) {
			int varIndex = -1;
			switch(TypeUtils.toTypeDescriptor(type).getSort()) {
			case Type.LONG:
				varIndex = GlobalVariableTable.newLongVarEntry(varName);
				break;
			case Type.DOUBLE:
				varIndex = GlobalVariableTable.newDoubleVarEntry(varName);
				break;
			case Type.BOOLEAN:
				varIndex = GlobalVariableTable.newBooleanVarEntry(varName);
				break;
			case Type.OBJECT:
				varIndex = GlobalVariableTable.newObjectVarEntry(varName);
				break;
			default:
				throw new RuntimeException("illegal type: " + type);
			}
			return new VarEntry(varIndex, true);
		}

		@Override
		public VarEntry getVarEntry(String varName) {
			return new VarEntry(GlobalVariableTable.getVarIndex(varName), true);
		}
	}

	/**
	 * contains var index and var flag(isGlobal)
	 * @author skgchxngsxyz-osx
	 *
	 */
	private static class VarEntry {
		/**
		 * In local variable, this index represents jvm local variable table' s index.
		 * In global variable, represents global var table index.
		 */
		private final int varIndex;

		/**
		 * represent variable scope.
		 * if true, variable is global variable.
		 */
		private final boolean isGlobal;

		private VarEntry(int varIndex, boolean isGlobal) {
			this.varIndex = varIndex;
			this.isGlobal = isGlobal;
		}

		private int getVarIndex() {
			return this.varIndex;
		}

		private boolean isGlobaVar() {
			return this.isGlobal;
		}
 	}
}
