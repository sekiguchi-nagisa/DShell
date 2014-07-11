package dshell.internal.codegen;

import java.util.ArrayList;
import java.util.Stack;

import org.antlr.v4.runtime.Token;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import dshell.internal.codegen.ClassBuilder.MethodBuilder;
import dshell.internal.codegen.ClassBuilder.TryBlockLabels;
import dshell.internal.lib.DShellClassLoader;
import dshell.internal.lib.Utils;
import dshell.internal.parser.CalleeHandle.MethodHandle;
import dshell.internal.parser.CalleeHandle.OperatorHandle;
import dshell.internal.parser.CalleeHandle.StaticFieldHandle;
import dshell.internal.parser.CalleeHandle.StaticFunctionHandle;
import dshell.internal.parser.Node.ArrayNode;
import dshell.internal.parser.Node.AssertNode;
import dshell.internal.parser.Node.AssignNode;
import dshell.internal.parser.Node.BlockNode;
import dshell.internal.parser.Node.BooleanValueNode;
import dshell.internal.parser.Node.BreakNode;
import dshell.internal.parser.Node.CastNode;
import dshell.internal.parser.Node.CatchNode;
import dshell.internal.parser.Node.ClassNode;
import dshell.internal.parser.Node.CommandNode;
import dshell.internal.parser.Node.CondOpNode;
import dshell.internal.parser.Node.ConstructorCallNode;
import dshell.internal.parser.Node.ConstructorNode;
import dshell.internal.parser.Node.ContinueNode;
import dshell.internal.parser.Node.ElementGetterNode;
import dshell.internal.parser.Node.EmptyBlockNode;
import dshell.internal.parser.Node.EmptyNode;
import dshell.internal.parser.Node.ExportEnvNode;
import dshell.internal.parser.Node.ExprNode;
import dshell.internal.parser.Node.FieldGetterNode;
import dshell.internal.parser.Node.FloatValueNode;
import dshell.internal.parser.Node.ForInNode;
import dshell.internal.parser.Node.ForNode;
import dshell.internal.parser.Node.FunctionNode;
import dshell.internal.parser.Node.GlobalVarNode;
import dshell.internal.parser.Node.IfNode;
import dshell.internal.parser.Node.ImportEnvNode;
import dshell.internal.parser.Node.InstanceofNode;
import dshell.internal.parser.Node.IntValueNode;
import dshell.internal.parser.Node.ApplyNode;
import dshell.internal.parser.Node.MapNode;
import dshell.internal.parser.Node.OperatorCallNode;
import dshell.internal.parser.Node.PairNode;
import dshell.internal.parser.Node.ReturnNode;
import dshell.internal.parser.Node.RootNode;
import dshell.internal.parser.Node.StringValueNode;
import dshell.internal.parser.Node.SymbolNode;
import dshell.internal.parser.Node.ThrowNode;
import dshell.internal.parser.Node.TryNode;
import dshell.internal.parser.Node.VarDeclNode;
import dshell.internal.parser.Node.WhileNode;
import dshell.internal.parser.Node;
import dshell.internal.parser.NodeVisitor;
import dshell.internal.parser.TypeUtils;
import dshell.internal.process.AbstractProcessContext;
import dshell.internal.process.TaskContext;
import dshell.internal.type.DSType;
import dshell.internal.type.GenericType;
import dshell.internal.type.DSType.FunctionType;
import dshell.internal.type.DSType.PrimitiveType;
import dshell.internal.type.DSType.VoidType;

/**
 * generate java byte code from node.
 * @author skgchxngsxyz-osx
 *
 */
public class JavaByteCodeGen implements NodeVisitor<Void>, Opcodes {
	protected final DShellClassLoader classLoader;
	protected final Stack<MethodBuilder> methodBuilders;

	public JavaByteCodeGen(DShellClassLoader classLoader) {
		this.classLoader = classLoader;
		this.methodBuilders = new Stack<>();
	}

	public static byte[] generateFuncTypeInterface(FunctionType funcType) {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		writer.visit(V1_7, ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT, funcType.getInternalName(), null, "java/lang/Object", null);
		// generate method stub
		GeneratorAdapter adapter = new GeneratorAdapter(ACC_PUBLIC | ACC_ABSTRACT, funcType.getHandle().getMethodDesc(), null, null, writer);
		adapter.endMethod();
		// generate static field containing FuncType name
		String fieldDesc = Type.getType(String.class).getDescriptor();
		writer.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "funcTypeName", fieldDesc, null, funcType.getTypeName());
		writer.visitEnd();
		return writer.toByteArray();
	}

	private MethodBuilder getCurrentMethodBuilder() {
		return this.methodBuilders.peek();
	}

	private void generateCode(Node node) {
		this.getCurrentMethodBuilder().setLineNum(node.getToken());
		node.accept(this);
	}

	private void visitBlockWithCurrentScope(BlockNode blockNode) {
		this.generateCode(blockNode);
	}

	private void visitBlockWithNewScope(BlockNode blockNode) {
		this.getCurrentMethodBuilder().createNewLocalScope();
		this.visitBlockWithCurrentScope(blockNode);
		this.getCurrentMethodBuilder().removeCurrentLocalScope();
	}

	private void createPopInsIfExprNode(Node node) {
		if(!(node instanceof ExprNode)) {
			return;
		}
		DSType type = ((ExprNode) node).getType();
		if(type instanceof VoidType) {
			return;
		}
		this.getCurrentMethodBuilder().pop(TypeUtils.toTypeDescriptor(type));
	}

	/**
	 * get source name from token.
	 * @param token
	 * @return
	 * - return null, if token is null.
	 */
	private String getSourceName(Token token) {
		if(token != null) {
			return token.getTokenSource().getSourceName();
		}
		return null;
	}

	/**
	 * generate class from RootNode
	 * @param node
	 * - root node
	 * @param enableResultPrint
	 * - if true, insert print instruction.
	 * @return
	 * - generated class.
	 */
	public Class<?> generateTopLevelClass(RootNode node, boolean enableResultPrint) {
		ClassBuilder classBuilder = new ClassBuilder(this.getSourceName(node.getToken()));
		this.methodBuilders.push(classBuilder.createNewMethodBuilder(null));
		for(Node targetNode : node.getNodeList()) {
			this.generateCode(targetNode);
			if((targetNode instanceof ExprNode) && !(((ExprNode)targetNode).getType() instanceof VoidType)) {
				MethodBuilder adapter = this.getCurrentMethodBuilder();
				DSType type = ((ExprNode)targetNode).getType();
				if(enableResultPrint) {	// if true, insert print ins
					if(type instanceof PrimitiveType) {
						adapter.box(TypeUtils.toTypeDescriptor(type));
					}
					adapter.push(type.getTypeName());
					node.getHandle().call(adapter);
				} else {	// if false, pop stack
					adapter.pop(TypeUtils.toTypeDescriptor(type));
				}
			}
		}
		this.methodBuilders.peek().returnValue();
		this.methodBuilders.pop().endMethod();
		return classBuilder.generateClass(this.classLoader.createChild());
	}

	// visit api
	@Override
	public Void visit(IntValueNode node) {
		this.getCurrentMethodBuilder().push(node.getValue());
		return null;
	}

	@Override
	public Void visit(FloatValueNode node) {
		this.getCurrentMethodBuilder().push(node.getValue());
		return null;
	}

	@Override
	public Void visit(BooleanValueNode node) {
		this.getCurrentMethodBuilder().push(node.getValue());
		return null;
	}

	@Override
	public Void visit(StringValueNode node) {
		this.getCurrentMethodBuilder().push(node.getValue());
		return null;
	}

	@Override
	public Void visit(ArrayNode node) {
		int size = node.getNodeList().size();
		DSType elementType = ((GenericType) node.getType()).getElementTypeList().get(0);
		Type elementTypeDesc = TypeUtils.toTypeDescriptor(elementType);
		Type arrayClassDesc = TypeUtils.toTypeDescriptor(node.getType().getInternalName());

		GeneratorAdapter adapter = this.getCurrentMethodBuilder();
		adapter.newInstance(arrayClassDesc);
		adapter.dup();
		adapter.push(size);
		adapter.newArray(elementTypeDesc);
		for(int i = 0; i < size; i++) {
			adapter.dup();
			adapter.push(i);
			this.generateCode(node.getNodeList().get(i));
			adapter.arrayStore(elementTypeDesc);
		}
		Method methodDesc = TypeUtils.toArrayConstructorDescriptor(elementType);
		adapter.invokeConstructor(arrayClassDesc, methodDesc);
		return null;
	}

	@Override
	public Void visit(MapNode node) {
		int size = node.getKeyList().size();
		Type elementTypeDesc = TypeUtils.toTypeDescriptor(node.getValueList().get(0).getType());
		Type keyTypeDesc = TypeUtils.toTypeDescriptor("java/lang/String");
		Type valueTypeDesc = TypeUtils.toTypeDescriptor("java/lang/Object");
		Type mapClassDesc = TypeUtils.toTypeDescriptor(node.getType().getInternalName());

		GeneratorAdapter adapter = this.getCurrentMethodBuilder();
		adapter.newInstance(mapClassDesc);
		adapter.dup();
		// generate key array
		adapter.push(size);
		adapter.newArray(keyTypeDesc);
		for(int i = 0; i < size; i++) {
			adapter.dup();
			adapter.push(i);
			this.generateCode(node.getKeyList().get(i));
			adapter.arrayStore(keyTypeDesc);
		}
		// generate value array
		adapter.push(size);
		adapter.newArray(valueTypeDesc);
		for(int i = 0; i < size; i++) {
			adapter.dup();
			adapter.push(i);
			this.generateCode(node.getValueList().get(i));
			adapter.box(elementTypeDesc);
			adapter.arrayStore(valueTypeDesc);
		}
		adapter.invokeConstructor(mapClassDesc, TypeUtils.toMapConstructorDescriptor());
		return null;
	}

	@Override
	public Void visit(PairNode node) {
		Type leftTypeDesc = TypeUtils.toTypeDescriptor(node.getLeftNode().getType());
		Type rightTypeDesc = TypeUtils.toTypeDescriptor(node.getRightNode().getType());
		Type pairClassDesc = TypeUtils.toTypeDescriptor(node.getType().getInternalName());

		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		mBuilder.newInstance(pairClassDesc);
		mBuilder.dup();
		this.generateCode(node.getLeftNode());
		mBuilder.box(leftTypeDesc);
		this.generateCode(node.getRightNode());
		mBuilder.box(rightTypeDesc);
		mBuilder.invokeConstructor(pairClassDesc, TypeUtils.toPairConstructorDescriptor());
		return null;
	}

	@Override
	public Void visit(SymbolNode node) {
		// get func object from static field
		StaticFieldHandle handle = node.getHandle();
		if(handle != null) {
			handle.callGetter(this.getCurrentMethodBuilder());
			return null;
		}
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		mBuilder.loadValueFromVar(node.getSymbolName(), node.getType());
		return null;
	}

	@Override
	public Void visit(ElementGetterNode node) {
		this.generateCode(node.getRecvNode());
		this.generateCode(node.getIndexNode());
		node.getGetterHandle().call(this.getCurrentMethodBuilder());
		return null;
	}

	@Override
	public Void visit(FieldGetterNode node) {
		this.generateCode(node.getRecvNode());
		node.getHandle().callGetter(this.getCurrentMethodBuilder());
		return null;
	}

	@Override
	public Void visit(CastNode node) {
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		Type targetTypeDesc = TypeUtils.toTypeDescriptor(node.getTargetType());
		this.generateCode(node.getExprNode());
		switch(node.getCastOp()) {
		case CastNode.NOP:
			break;
		case CastNode.BOX:
			mBuilder.box(targetTypeDesc);
			break;
		case CastNode.INT_2_FLOAT:
			mBuilder.cast(Type.LONG_TYPE, targetTypeDesc);
			break;
		case CastNode.FLOAT_2_INT:
			mBuilder.cast(Type.DOUBLE_TYPE, targetTypeDesc);
			break;
		case CastNode.TO_STRING:
			mBuilder.box(TypeUtils.toTypeDescriptor(node.getExprNode().getType()));
			mBuilder.invokeVirtual(Type.getType(Object.class), 
					TypeUtils.toMehtodDescriptor(node.getTargetType(), "toString", new ArrayList<DSType>(0)));
			break;
		case CastNode.CHECK_CAST:
			mBuilder.checkCast(targetTypeDesc);
			break;
		default:
			throw new RuntimeException("unsupported cast op: " + node.getCastOp());
		}
		return null;
	}

	@Override
	public Void visit(InstanceofNode node) {
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		DSType exprType = node.getExprNode().getType();
		DSType targetType = node.getTargetType();
		this.generateCode(node.getExprNode());
		switch(node.getOpType()) {
		case InstanceofNode.ALWAYS_FALSE:
			mBuilder.pop(TypeUtils.toTypeDescriptor(exprType));
			mBuilder.push(false);
			break;
		case InstanceofNode.COMP_TYPE:
			mBuilder.pop(TypeUtils.toTypeDescriptor(exprType));
			mBuilder.push(exprType.getTypeName().equals(targetType.getTypeName()));
			break;
		case InstanceofNode.INSTANCEOF:
			mBuilder.instanceOf(TypeUtils.toTypeDescriptor(targetType));
			break;
		default:
			throw new RuntimeException("unsupported instanceof op: " + node.getOpType());
		}
		return null;
	}

	@Override
	public Void visit(OperatorCallNode node) {
		for(Node paramNode : node.getNodeList()) {
			this.generateCode(paramNode);
		}
		node.getHandle().call(this.getCurrentMethodBuilder());
		return null;
	}

	protected void generateAsFuncCall(ApplyNode node) {
		MethodHandle handle = node.getHandle();
		if(!(handle instanceof StaticFunctionHandle)) {
			this.generateCode(node.getRecvNode());
		}
		for(Node paramNode : node.getArgList()) {
			this.generateCode(paramNode);
		}
		node.getHandle().call(this.getCurrentMethodBuilder());
	}

	protected void generateAsMethodCall(ApplyNode node) {
		FieldGetterNode getterNode = (FieldGetterNode) node.getRecvNode();
		this.generateCode(getterNode.getRecvNode());
		for(Node paramNode : node.getArgList()) {
			this.generateCode(paramNode);
		}
		node.getHandle().call(this.getCurrentMethodBuilder());
	}

	@Override
	public Void visit(ApplyNode node) {
		if(node.isFuncCall()) {
			this.generateAsFuncCall(node);
		} else {
			this.generateAsMethodCall(node);
		}
		return null;
	}

	@Override
	public Void visit(ConstructorCallNode node) {
		DSType revType = node.getHandle().getOwnerType();
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		mBuilder.newInstance(TypeUtils.toTypeDescriptor(revType));
		mBuilder.dup();
		for(Node paramNode : node.getNodeList()) {
			this.generateCode(paramNode);
		}
		node.getHandle().call(mBuilder);
		return null;
	}

	@Override
	public Void visit(CondOpNode node) {
		GeneratorAdapter adapter = this.getCurrentMethodBuilder();
		// generate and.
		if(node.getConditionalOp().equals("&&")) {
			Label rightLabel = adapter.newLabel();
			Label mergeLabel = adapter.newLabel();
			// and left
			this.generateCode(node.getLeftNode());
			adapter.push(true);
			adapter.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, rightLabel);
			adapter.push(false);
			adapter.goTo(mergeLabel);
			// and right
			adapter.mark(rightLabel);
			this.generateCode(node.getRightNode());
			adapter.mark(mergeLabel);
		// generate or
		} else {
			Label rightLabel = adapter.newLabel();
			Label mergeLabel = adapter.newLabel();
			// or left
			this.generateCode(node.getLeftNode());
			adapter.push(true);
			adapter.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, rightLabel);
			adapter.push(true);
			adapter.goTo(mergeLabel);
			// or right
			adapter.mark(rightLabel);
			this.generateCode(node.getRightNode());
			adapter.mark(mergeLabel);
		}
		return null;
	}

	@Override
	public Void visit(CommandNode node) {	//TODO: pipe, reidirect .. etc.
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		Type taskCtxDesc = Type.getType(TaskContext.class);
		Type procCtxDesc = Type.getType(AbstractProcessContext.class);

		mBuilder.newInstance(taskCtxDesc);
		mBuilder.dup();
		mBuilder.invokeConstructor(taskCtxDesc, Method.getMethod("void <init> ()"));
		int argSize = node.getArgNodeList().size();
		mBuilder.push(node.getCommandPath());
		Method method = new Method("createProcessContext", procCtxDesc, 
						new Type[]{Type.getType(String.class)});
		mBuilder.invokeStatic(taskCtxDesc, method);

		method = new Method("addArg", procCtxDesc, 
				new Type[]{Type.getType(String.class)});
		for(int i = 0; i < argSize; i++) {
			this.generateCode(node.getArgNodeList().get(i));
			mBuilder.invokeVirtual(procCtxDesc, method);
		}

		method = new Method("addContext", taskCtxDesc, new Type[]{procCtxDesc});
		mBuilder.invokeVirtual(taskCtxDesc, method);

		method = new Method("execAsInt", Type.LONG_TYPE, new Type[]{});
		mBuilder.invokeVirtual(taskCtxDesc, method);
		return null;
	}

	@Override
	public Void visit(AssertNode node) {
		this.generateCode(node.getExprNode());
		node.getHandle().call(this.getCurrentMethodBuilder());
		return null;
	}

	@Override
	public Void visit(BlockNode node) {
		for(Node targetNode : node.getNodeList()) {
			this.generateCode(targetNode);
			this.createPopInsIfExprNode(targetNode);
		}
		return null;
	}

	@Override
	public Void visit(BreakNode node) {
		Label label = this.getCurrentMethodBuilder().getBreakLabels().peek();
		this.getCurrentMethodBuilder().goTo(label);
		return null;
	}

	@Override
	public Void visit(ContinueNode node) {
		Label label = this.getCurrentMethodBuilder().getContinueLabels().peek();
		this.getCurrentMethodBuilder().goTo(label);
		return null;
	}

	@Override
	public Void visit(ExportEnvNode node) {
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		mBuilder.push(node.getEnvName());
		this.generateCode(node.getExprNode());
		node.getHandle().call(mBuilder);
		mBuilder.createNewVarAndStoreValue(node.getEnvName(), node.getHandle().getReturnType());
		return null;
	}

	@Override
	public Void visit(ImportEnvNode node) {
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		mBuilder.push(node.getEnvName());
		node.getHandle().call(mBuilder);
		mBuilder.createNewVarAndStoreValue(node.getEnvName(), node.getHandle().getReturnType());
		return null;
	}

	@Override
	public Void visit(ForNode node) {
		// init label
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		Label continueLabel = mBuilder.newLabel();
		Label breakLabel = mBuilder.newLabel();
		mBuilder.continueLabels.push(continueLabel);
		mBuilder.breakLabels.push(breakLabel);

		mBuilder.createNewLocalScope();
		// init
		this.generateCode(node.getInitNode());
		this.createPopInsIfExprNode(node.getInitNode());
		// cond
		mBuilder.mark(continueLabel);
		mBuilder.push(true);
		this.generateCode(node.getCondNode());
		mBuilder.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, breakLabel);
		// block
		this.visitBlockWithCurrentScope(node.getBlockNode());
		// iter
		this.generateCode(node.getIterNode());
		this.createPopInsIfExprNode(node.getIterNode());
		mBuilder.goTo(continueLabel);
		mBuilder.mark(breakLabel);

		mBuilder.removeCurrentLocalScope();
		// remove label
		mBuilder.continueLabels.pop();
		mBuilder.breakLabels.pop();
		return null;
	}

	@Override
	public Void visit(ForInNode node) {	//TODO:
		Utils.fatal(1, "unimplemented: " + node);
		return null;
	}

	@Override
	public Void visit(WhileNode node) {
		// init label
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		Label continueLabel = mBuilder.newLabel();
		Label breakLabel = mBuilder.newLabel();
		mBuilder.continueLabels.push(continueLabel);
		mBuilder.breakLabels.push(breakLabel);

		mBuilder.mark(continueLabel);
		mBuilder.push(true);
		this.generateCode(node.getCondNode());
		mBuilder.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, breakLabel);
		this.visitBlockWithNewScope(node.getBlockNode());
		mBuilder.goTo(continueLabel);
		mBuilder.mark(breakLabel);

		// remove label
		mBuilder.continueLabels.pop();
		mBuilder.breakLabels.pop();
		return null;
	}

	@Override
	public Void visit(IfNode node) {
		GeneratorAdapter adapter = this.getCurrentMethodBuilder();
		Label elseLabel = adapter.newLabel();
		Label mergeLabel = adapter.newLabel();
		// if cond
		this.generateCode(node.getCondNode());
		adapter.push(true);
		adapter.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, elseLabel);
		// then block
		this.visitBlockWithNewScope(node.getThenBlockNode());
		adapter.goTo(mergeLabel);
		// else block
		adapter.mark(elseLabel);
		this.visitBlockWithNewScope(node.getElseBlockNode());
		adapter.mark(mergeLabel);
		return null;
	}

	@Override
	public Void visit(ReturnNode node) {
		this.generateCode(node.getExprNode());
		this.getCurrentMethodBuilder().returnValue();
		return null;
	}

	@Override
	public Void visit(ThrowNode node) {
		this.generateCode(node.getExprNode());
		this.getCurrentMethodBuilder().throwException();
		return null;
	}

	@Override
	public Void visit(TryNode node) {
		// init labels
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		TryBlockLabels labels = mBuilder.createNewTryLabel();
		mBuilder.getTryLabels().push(labels);
		// try block
		mBuilder.mark(labels.startLabel);
		this.visitBlockWithNewScope(node.getTryBlockNode());
		mBuilder.mark(labels.endLabel);
		mBuilder.goTo(labels.finallyLabel);
		// catch block
		for(CatchNode catchNode : node.getCatchNodeList()) {
			this.generateCode(catchNode);
		}
		// finally block
		mBuilder.mark(labels.finallyLabel);
		this.visitBlockWithNewScope(node.getFinallyBlockNode());
		mBuilder.getTryLabels().pop();
		return null;
	}

	@Override
	public Void visit(CatchNode node) {
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		TryBlockLabels labels = mBuilder.getTryLabels().peek();
		mBuilder.createNewLocalScope();
		DSType exceptionType = node.getExceptionType();
		mBuilder.catchException(labels.startLabel, labels.endLabel, TypeUtils.toTypeDescriptor(exceptionType));
		mBuilder.createNewVarAndStoreValue(node.getExceptionVarName(), exceptionType);
		this.visitBlockWithCurrentScope(node.getCatchBlockNode());
		mBuilder.goTo(labels.finallyLabel);
		mBuilder.removeCurrentLocalScope();
		return null;
	}

	@Override
	public Void visit(VarDeclNode node) {
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		this.generateCode(node.getInitValueNode());
		mBuilder.createNewVarAndStoreValue(node.getVarName(), node.getInitValueNode().getType());
		return null;
	}

	@Override
	public Void visit(AssignNode node) {
		ExprNode leftNode = node.getLeftNode();
		if(leftNode instanceof ElementGetterNode) {
			this.generateAssignToElement(node);
			return null;
		}

		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		/**
		 * assign to symbol
		 */
		if(leftNode instanceof SymbolNode) {
			SymbolNode symbolNode = (SymbolNode) leftNode;
			this.generateRightValue(node);
			mBuilder.storeValueToVar(symbolNode.getSymbolName(), symbolNode.getType());

		/**
		 * assign to field
		 */
		} else if(leftNode instanceof FieldGetterNode) {
			FieldGetterNode getterNode = (FieldGetterNode) leftNode;
			this.generateCode(getterNode.getRecvNode());
			this.generateRightValue(node);
			getterNode.getHandle().callSetter(mBuilder);
		}
		return null;
	}

	private void generateAssignToElement(AssignNode node) {
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		ElementGetterNode leftNode = (ElementGetterNode) node.getLeftNode();
		OperatorHandle handle = node.getHandle();
		if(handle == null) {	
			this.generateCode(leftNode.getRecvNode());
			this.generateCode(leftNode.getIndexNode());
			this.generateCode(node.getRightNode());
			leftNode.getSetterHandle().call(mBuilder);
		} else {	// self assign
			this.generateCode(leftNode.getRecvNode());
			mBuilder.dup();
			this.generateCode(leftNode.getIndexNode());
			Type indexTypeDesc = TypeUtils.toTypeDescriptor(leftNode.getIndexNode().getType());
			if(indexTypeDesc.getSize() != 2) { 
				mBuilder.dupX1();
			} else {	// long or double
				mBuilder.dup2X1();
			}
			leftNode.getGetterHandle().call(mBuilder);
			this.generateCode(node.getRightNode());
			handle.call(mBuilder);
			leftNode.getSetterHandle().call(mBuilder);
		}
	}

	private void generateRightValue(AssignNode node) {
		OperatorHandle handle = node.getHandle();
		if(handle != null) {	// self assgin
			this.generateCode(node.getLeftNode());
			this.generateCode(node.getRightNode());
			handle.call(this.getCurrentMethodBuilder());
		} else {
			this.generateCode(node.getRightNode());
		}
	}

	@Override
	public Void visit(FunctionNode node) {
		ClassBuilder classBuilder = new ClassBuilder(node.getHolderType(), this.getSourceName(node.getToken()));
		// create static field.
		StaticFieldHandle fieldHandle = node.getHolderType().getFieldHandle();
		Type fieldTypeDesc = TypeUtils.toTypeDescriptor(fieldHandle.getFieldType());
		classBuilder.visitField(ACC_PUBLIC | ACC_STATIC, fieldHandle.getCalleeName(), fieldTypeDesc.getDescriptor(), null, null);

		// generate static method.
		MethodBuilder mBuilder = classBuilder.createNewMethodBuilder(node.getHolderType().getFuncHandle());
		this.methodBuilders.push(mBuilder);
		mBuilder.createNewLocalScope();
		// set argument decl
		int size = node.getArgDeclNodeList().size();
		for(int i = 0; i < size; i++) {
			SymbolNode argNode = node.getArgDeclNodeList().get(i);
			DSType argType = node.getHolderType().getFuncHandle().getParamTypeList().get(i);
			mBuilder.defineArgument(argNode.getSymbolName(), argType);
		}
		this.visitBlockWithCurrentScope(node.getBlockNode());
		mBuilder.removeCurrentLocalScope();
		this.methodBuilders.pop().endMethod();

		// generate interface method
		MethodHandle handle = ((FunctionType)node.getHolderType().getFieldHandle().getFieldType()).getHandle();
		mBuilder = classBuilder.createNewMethodBuilder(handle);
		mBuilder.loadArgs();
		node.getHolderType().getFuncHandle().call(mBuilder);
		mBuilder.returnValue();
		mBuilder.endMethod();

		// generate constructor.
		Method initDesc = TypeUtils.toConstructorDescriptor(new ArrayList<DSType>());
		GeneratorAdapter adapter = new GeneratorAdapter(ACC_PUBLIC, initDesc, null, null, classBuilder);
		adapter.loadThis();
		adapter.invokeConstructor(Type.getType("java/lang/Object"), initDesc);
		adapter.returnValue();
		adapter.endMethod();

		// generate static initializer
		Method cinitDesc = Method.getMethod("void <clinit> ()");
		adapter = new GeneratorAdapter(ACC_PUBLIC | ACC_STATIC, cinitDesc, null, null, classBuilder);
		Type ownerType = TypeUtils.toTypeDescriptor(fieldHandle.getOwnerType());
		adapter.newInstance(ownerType);
		adapter.dup();
		adapter.invokeConstructor(ownerType, initDesc);
		fieldHandle.callSetter(adapter);
		adapter.returnValue();
		adapter.endMethod();

		classBuilder.generateClass(this.classLoader);
		return null;
	}

	@Override
	public Void visit(ClassNode node) {	//TODO:
		Utils.fatal(1, "unimplemented: " + node);
		return null;
	}

	@Override
	public Void visit(ConstructorNode node) {	//TODO:
		Utils.fatal(1, "unimplemented: " + node);
		return null;
	}

	@Override
	public Void visit(GlobalVarNode node) {
		String varName = node.getVarName();
		DSType valueType = node.getValueType();
		this.getCurrentMethodBuilder().createNewGlobalVarAndStoreValue(varName, valueType, node.getValue());
		return null;
	}

	@Override
	public Void visit(EmptyNode node) {	//do nothing
		return null;
	}

	@Override
	public Void visit(EmptyBlockNode node) {	// do nothing
		return null;
	}
}
