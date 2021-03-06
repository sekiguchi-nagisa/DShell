package dshell.internal.codegen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.Token;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import dshell.internal.codegen.ClassBuilder.MethodBuilder;
import dshell.internal.codegen.ClassBuilder.TryCatchLabel;
import dshell.internal.lib.DShellClassLoader;
import dshell.internal.lib.StringContext;
import dshell.internal.lib.Utils;
import dshell.internal.parser.Node.ArgumentNode;
import dshell.internal.parser.Node.ArrayNode;
import dshell.internal.parser.Node.AssertNode;
import dshell.internal.parser.Node.AssignNode;
import dshell.internal.parser.Node.BlockEndNode;
import dshell.internal.parser.Node.BlockNode;
import dshell.internal.parser.Node.BooleanValueNode;
import dshell.internal.parser.Node.BreakNode;
import dshell.internal.parser.Node.CastNode;
import dshell.internal.parser.Node.CatchNode;
import dshell.internal.parser.Node.ClassNode;
import dshell.internal.parser.Node.FinallyNode;
import dshell.internal.parser.Node.ProcessNode;
import dshell.internal.parser.Node.CondOpNode;
import dshell.internal.parser.Node.ConstructorCallNode;
import dshell.internal.parser.Node.ConstructorNode;
import dshell.internal.parser.Node.ContinueNode;
import dshell.internal.parser.Node.IndexNode;
import dshell.internal.parser.Node.EmptyBlockNode;
import dshell.internal.parser.Node.EmptyNode;
import dshell.internal.parser.Node.ExportEnvNode;
import dshell.internal.parser.Node.ExprNode;
import dshell.internal.parser.Node.AccessNode;
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
import dshell.internal.parser.Node.InnerTaskNode;
import dshell.internal.parser.Node.ReturnNode;
import dshell.internal.parser.Node.RootNode;
import dshell.internal.parser.Node.SpecialCharNode;
import dshell.internal.parser.Node.StringExprNode;
import dshell.internal.parser.Node.StringValueNode;
import dshell.internal.parser.Node.SymbolNode;
import dshell.internal.parser.Node.TaskNode;
import dshell.internal.parser.Node.ThrowNode;
import dshell.internal.parser.Node.TryNode;
import dshell.internal.parser.Node.VarDeclNode;
import dshell.internal.parser.Node.WhileNode;
import dshell.internal.parser.Node;
import dshell.internal.parser.NodeVisitor;
import dshell.internal.parser.TypeUtils;
import dshell.internal.process.AbstractProcessContext;
import dshell.internal.process.ArgumentBuilder;
import dshell.internal.process.TaskContext;
import dshell.internal.type.DSType;
import dshell.internal.type.GenericType;
import dshell.internal.type.CalleeHandle.MethodHandle;
import dshell.internal.type.CalleeHandle.StaticFieldHandle;
import dshell.internal.type.CalleeHandle.StaticFunctionHandle;
import dshell.internal.type.DSType.FunctionType;
import dshell.internal.type.DSType.PrimitiveType;
import dshell.internal.type.DSType.VoidType;
import dshell.lang.GenericPair;

/**
 * generate java byte code from node.
 * @author skgchxngsxyz-osx
 *
 */
public class JavaByteCodeGen implements NodeVisitor<Void>, Opcodes {
	protected final DShellClassLoader classLoader;
	protected final Deque<MethodBuilder> methodBuilders;

	/**
	 * if false, not generate assert statement
	 */
	protected boolean enableAssert = true;

	public JavaByteCodeGen(DShellClassLoader classLoader) {
		this.classLoader = classLoader;
		this.methodBuilders = new ArrayDeque<>();
	}

	private MethodBuilder getCurrentMethodBuilder() {
		return this.methodBuilders.peek();
	}

	private void generateCode(Node node) {
		this.getCurrentMethodBuilder().setLineNum(node.getToken());
		node.accept(this);
	}

	private void generateBlockWithCurrentScope(BlockNode blockNode) {
		this.generateCode(blockNode);
	}

	private void generateBlockWithNewScope(BlockNode blockNode) {
		this.getCurrentMethodBuilder().enterScope();
		this.generateBlockWithCurrentScope(blockNode);
		this.getCurrentMethodBuilder().exitScope();
	}

	private void createPopInsIfExprNode(Node node) {
		if(!(node instanceof ExprNode)) {
			return;
		}
		this.getCurrentMethodBuilder().pop(TypeUtils.toTypeDescriptor(((ExprNode) node).getType()));
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
		generateFuncTypeInterface(node.getGenTargetFuncTypeSet(), this.classLoader);

		ClassBuilder classBuilder = new ClassBuilder(node.getToplevelName(), this.getSourceName(node.getToken()));
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

	/**
	 * generate function type interface class. if funcTypeSet is null, not generate interface.
	 * @param funcTypeSet
	 * may be null
	 */
	protected static void generateFuncTypeInterface(Set<FunctionType> funcTypeSet, DShellClassLoader loader) {
		if(funcTypeSet == null) {
			return;
		}
		for(FunctionType funcType : funcTypeSet) {
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			writer.visit(V1_7, ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT, funcType.getInternalName(), null, "java/lang/Object", null);
			// generate method stub
			GeneratorAdapter adapter = new GeneratorAdapter(ACC_PUBLIC | ACC_ABSTRACT, funcType.getHandle().getMethodDesc(), null, null, writer);
			adapter.endMethod();
			// generate static field containing FuncType name
			String fieldDesc = Type.getType(String.class).getDescriptor();
			writer.visitField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "funcTypeName", fieldDesc, null, funcType.getTypeName());
			writer.visitEnd();
			// load generated class
			loader.definedAndLoadClass(funcType.getInternalName(), writer.toByteArray());
		}
	}

	/**
	 * enable or disable assertion
	 * @param enableAssert
	 * if true, enable assertion
	 */
	public void setAssertion(boolean enableAssert) {
		this.enableAssert = enableAssert;
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
	public Void visit(StringExprNode node) {	//FIXME
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		switch(node.getElementList().size()) {
		case 0:
			mBuilder.push("");
			return null;
		case 1:
			this.generateCode(node.getElementList().get(0));
			return null;
		}

		Type ctxTypeDesc = Type.getType(StringContext.class);
		Type stringTypeDesc = TypeUtils.toTypeDescriptor(node.getType());
		Method methodDesc = new Method("newContext", ctxTypeDesc, new Type[]{});

		// create new string context
		mBuilder.invokeStatic(ctxTypeDesc, methodDesc);

		// append string element
		methodDesc = new Method("append", ctxTypeDesc, new Type[]{stringTypeDesc});
		for(ExprNode exprNode : node.getElementList()) {
			this.generateCode(exprNode);
			mBuilder.invokeVirtual(ctxTypeDesc, methodDesc);
		}

		// string context to string
		methodDesc = new Method("toString", stringTypeDesc, new Type[]{});
		mBuilder.invokeVirtual(ctxTypeDesc, methodDesc);
		return null;
	}

	@Override
	public Void visit(ArrayNode node) {
		int size = node.getNodeList().size();
		DSType elementType = ((GenericType) node.getType()).getElementTypeList().get(0);
		Type elementTypeDesc = TypeUtils.toTypeDescriptor(elementType);
		Type arrayClassDesc = TypeUtils.toTypeDescriptor(node.getType());

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
		Type keyTypeDesc = Type.getType(String.class);
		Type valueTypeDesc = Type.getType(Object.class);
		Type mapClassDesc = TypeUtils.toTypeDescriptor(node.getType());

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
		Type pairClassDesc = TypeUtils.toTypeDescriptor(node.getType());

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
	public Void visit(IndexNode node) {
		this.generateCode(node.getRecvNode());
		this.generateCode(node.getIndexNode());
		node.getGetterHandle().call(this.getCurrentMethodBuilder());
		return null;
	}

	@Override
	public Void visit(AccessNode node) {
		this.generateCode(node.getRecvNode());
		node.getHandle().callGetter(this.getCurrentMethodBuilder());
		return null;
	}

	@Override
	public Void visit(CastNode node) {
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		Type targetTypeDesc = TypeUtils.toTypeDescriptor(node.getType());
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
					TypeUtils.toMethodDescriptor(node.getType(), "toString", new ArrayList<DSType>(0)));
			break;
		case CastNode.CHECK_CAST:
			mBuilder.push(targetTypeDesc);
			mBuilder.invokeStatic(Type.getType(Utils.class), 
					new Method("cast", Type.getType(Object.class), 
							new Type[]{Type.getType(Object.class), Type.getType(Class.class)}));
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
		AccessNode getterNode = (AccessNode) node.getRecvNode();
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
	public Void visit(ProcessNode node) {	//TODO: trace
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		Type taskCtxDesc = Type.getType(TaskContext.class);
		Type procCtxDesc = Type.getType(AbstractProcessContext.class);
		Type argDesc = Type.getType(ArgumentBuilder.class);

		int argSize = node.getArgNodeList().size();
		mBuilder.push(node.getCommandPath());
		Method methodDesc = new Method("createProcessContext", procCtxDesc, 
						new Type[]{Type.getType(String.class)});
		mBuilder.invokeStatic(taskCtxDesc, methodDesc);

		// set arguments
		methodDesc = new Method("addArg", procCtxDesc, 
				new Type[]{argDesc});
		for(int i = 0; i < argSize; i++) {
			ExprNode argNode = node.getArgNodeList().get(i);
			this.generateCode(argNode);
			mBuilder.invokeVirtual(procCtxDesc, methodDesc);
		}

		// set redirect options
		Method redirDesc = new Method("setRedirOption", procCtxDesc, 
				new Type[]{Type.INT_TYPE, argDesc});
		for(GenericPair<Integer, ExprNode> pair : node.getRedirOptionList()) {
			mBuilder.push(pair.getLeft());
			this.generateCode(pair.getRight());
			mBuilder.invokeVirtual(procCtxDesc, redirDesc);
		}

		// enable system call trace
		if(node.isTracable()) {
			Method traceDesc = new Method("enableTrace", procCtxDesc, new Type[]{});
			mBuilder.invokeVirtual(procCtxDesc, traceDesc);
		}

		methodDesc = new Method("addContext", taskCtxDesc, new Type[]{procCtxDesc});
		mBuilder.invokeVirtual(taskCtxDesc, methodDesc);
		return null;
	}

	@Override
	public Void visit(ArgumentNode node) {	//TODO: refactoring
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		Type bufferTypeDesc = Type.getType(ArgumentBuilder.class);
		Method methodDesc = new Method("newArgumentBuilder", bufferTypeDesc, new Type[]{});

		mBuilder.invokeStatic(bufferTypeDesc, methodDesc);

		// add argument segment, string or array
		for(ExprNode exprNode : node.getSegmentNodeList()) {
			Type paramTypeDesc = TypeUtils.toTypeDescriptor(exprNode.getType());
			this.generateCode(exprNode);

			if(!exprNode.getType().getTypeName().startsWith("Array<")) {	// treat as object
				mBuilder.box(paramTypeDesc);
				paramTypeDesc = Type.getType(Object.class);
			}
			methodDesc = new Method("append", bufferTypeDesc, 
					new Type[]{paramTypeDesc});
			mBuilder.invokeVirtual(bufferTypeDesc, methodDesc);
		}
		return null;
	}

	@Override
	public Void visit(SpecialCharNode node) {	//TODO: refactoring
		switch(node.getExpandType()) {
		case SpecialCharNode.at: {
			Method methodDesc = 
					new Method("getArgs", TypeUtils.toTypeDescriptor(node.getType()), new Type[]{});
			this.getCurrentMethodBuilder().invokeStatic(Type.getType(Utils.class), methodDesc);
			break;
		}
		}
		return null;
	}

	@Override
	public Void visit(TaskNode node) {	// TODO: refactoring
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		Type taskCtxDesc = Type.getType(TaskContext.class);

		mBuilder.newInstance(taskCtxDesc);
		mBuilder.dup();
		mBuilder.push(node.isBackGround());
		mBuilder.invokeConstructor(taskCtxDesc, Method.getMethod("void <init> (boolean)"));

		// set output buffer
		GenericPair<String, DSType> bufferEntry = node.getBufferEntry();
		if(bufferEntry != null) {
			DSType bufferType = bufferEntry.getRight();
			mBuilder.loadValueFromVar(bufferEntry.getLeft(), bufferType);
			Method methodDesc = new Method("setOutputBuffer", 
					taskCtxDesc, new Type[] {TypeUtils.toTypeDescriptor(bufferType)});
			mBuilder.invokeVirtual(taskCtxDesc, methodDesc);
		}

		// generate process context
		for(ProcessNode prcoNode : node.getProcNodeList()) {
			this.generateCode(prcoNode);
		}

		// resolve execution type
		String methodName = "execAsInt";
		switch(node.getType().getTypeName()) {
		case "int":
			methodName = "execAsInt";
			break;
		case "boolean":
			methodName = "execAsBoolean";
			break;
		case "void":
			methodName = "execAsVoid";
			break;
		case "Task":
			methodName = "execAsTask";
			break;
		}
		Type returnTypeDesc = TypeUtils.toTypeDescriptor(node.getType());
		Method methodDesc = new Method(methodName, returnTypeDesc, new Type[]{});
		mBuilder.invokeVirtual(taskCtxDesc, methodDesc);
		return null;
	}

	@Override
	public Void visit(InnerTaskNode node) {	//FIXME: refactoring
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		mBuilder.enterScope();

		GenericPair<String, DSType> bufferEntry = node.getEntry();
		DSType bufferType = bufferEntry.getRight();

		// create output buffer
		Type bufferTypeDesc = TypeUtils.toTypeDescriptor(bufferType);
		Method methodDesc = new Method("newBuffer", bufferTypeDesc, new Type[]{});
		mBuilder.invokeStatic(bufferTypeDesc, methodDesc);
		mBuilder.createNewVarAndStoreValue(bufferEntry.getLeft(), bufferType);

		// generate command expression
		this.generateCode(node.getExprNode());

		// pop return value of command
		mBuilder.pop(TypeUtils.toTypeDescriptor(node.getExprNode().getType()));

		// get message
		DSType type = node.getType();
		Type typeDesc = TypeUtils.toTypeDescriptor(type);
		mBuilder.loadValueFromVar(bufferEntry.getLeft(), bufferType);
		switch(type.getTypeName()) {
		case "String":
			methodDesc = new Method("getMessageString", typeDesc, new Type[]{});
			mBuilder.invokeVirtual(bufferTypeDesc, methodDesc);
			break;
		case "Array<String>":
			methodDesc = new Method("getMessageArray", typeDesc, new Type[]{});
			mBuilder.invokeVirtual(bufferTypeDesc, methodDesc);
			break;
		default:
			Utils.fatal(1, "unsupported type: " + type);
			break;
		}
		mBuilder.exitScope();
		return null;
	}

	@Override
	public Void visit(AssertNode node) {
		if(this.enableAssert) {
			this.generateCode(node.getExprNode());
			node.getHandle().call(this.getCurrentMethodBuilder());
		}
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
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		Label label = mBuilder.getLoopLabels().peek().getLeft();
		mBuilder.jumpToMultipleFinally();
		mBuilder.goTo(label);
		return null;
	}

	@Override
	public Void visit(ContinueNode node) {
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		Label label = mBuilder.getLoopLabels().peek().getRight();
		mBuilder.jumpToMultipleFinally();
		mBuilder.goTo(label);
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
		mBuilder.getLoopLabels().push(new GenericPair<Label, Label>(breakLabel, continueLabel));

		mBuilder.enterScope();
		// init
		this.generateCode(node.getInitNode());
		this.createPopInsIfExprNode(node.getInitNode());
		// cond
		mBuilder.mark(continueLabel);
		mBuilder.push(true);
		this.generateCode(node.getCondNode());
		mBuilder.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, breakLabel);
		// block
		this.generateBlockWithCurrentScope(node.getBlockNode());
		// iter
		this.generateCode(node.getIterNode());
		this.createPopInsIfExprNode(node.getIterNode());
		mBuilder.goTo(continueLabel);
		mBuilder.mark(breakLabel);

		mBuilder.exitScope();
		// remove label
		mBuilder.getLoopLabels().pop();
		return null;
	}

	@Override
	public Void visit(ForInNode node) {
		// init label
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		Label continueLabel = mBuilder.newLabel();
		Label breakLabel = mBuilder.newLabel();
		mBuilder.getLoopLabels().push(new GenericPair<Label, Label>(breakLabel, continueLabel));

		mBuilder.enterScope();
		// init
		this.generateCode(node.getExprNode());
		mBuilder.dup();
		node.getResetHandle().call(mBuilder);
		// cond
		mBuilder.mark(continueLabel);
		mBuilder.dup();
		node.getHasNextHandle().call(mBuilder);
		mBuilder.push(true);
		mBuilder.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, breakLabel);
		// block
		mBuilder.dup();
		node.getNextHandle().call(mBuilder);
		DSType varType = node.getNextHandle().getReturnType();
		mBuilder.createNewVarAndStoreValue(node.getInitName(), varType);
		this.generateBlockWithCurrentScope(node.getBlockNode());
		// iter
		mBuilder.goTo(continueLabel);
		mBuilder.mark(breakLabel);

		mBuilder.exitScope();
		mBuilder.pop();
		// remove label
		mBuilder.getLoopLabels().pop();
		return null;
	}

	@Override
	public Void visit(WhileNode node) {
		if(node.isAsDoWhile()) {
			this.generateDoWhile(node);
			return null;
		}
		// init label
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		Label continueLabel = mBuilder.newLabel();
		Label breakLabel = mBuilder.newLabel();
		mBuilder.getLoopLabels().push(new GenericPair<Label, Label>(breakLabel, continueLabel));

		mBuilder.mark(continueLabel);
		mBuilder.push(true);
		this.generateCode(node.getCondNode());
		mBuilder.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, breakLabel);
		this.generateBlockWithNewScope(node.getBlockNode());
		mBuilder.goTo(continueLabel);
		mBuilder.mark(breakLabel);

		// remove label
		mBuilder.getLoopLabels().pop();
		return null;
	}

	private void generateDoWhile(WhileNode node) {
		// init label
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		Label continueLabel = mBuilder.newLabel();
		Label breakLabel = mBuilder.newLabel();
		Label enterLabel = mBuilder.newLabel();
		mBuilder.getLoopLabels().push(new GenericPair<Label, Label>(breakLabel, continueLabel));

		mBuilder.mark(enterLabel);
		this.generateBlockWithNewScope(node.getBlockNode());
		mBuilder.mark(continueLabel);
		mBuilder.push(true);
		this.generateCode(node.getCondNode());
		mBuilder.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, breakLabel);
		mBuilder.goTo(enterLabel);
		mBuilder.mark(breakLabel);

		// remove label
		mBuilder.getLoopLabels().pop();
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
		this.generateBlockWithNewScope(node.getThenBlockNode());
		adapter.goTo(mergeLabel);
		// else block
		adapter.mark(elseLabel);
		this.generateBlockWithNewScope(node.getElseBlockNode());
		adapter.mark(mergeLabel);
		return null;
	}

	@Override
	public Void visit(ReturnNode node) {
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		this.generateCode(node.getExprNode());
		mBuilder.jumpToMultipleFinally();
		mBuilder.returnValue();
		return null;
	}

	@Override
	public Void visit(ThrowNode node) {
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		this.generateCode(node.getExprNode());
		mBuilder.jumpToMultipleFinally();
		mBuilder.throwException();
		return null;
	}

	@Override
	public Void visit(TryNode node) {
		// init labels
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		TryCatchLabel labels = 
				mBuilder.createNewTryLabel(node.getFinallyNode() instanceof FinallyNode);
		mBuilder.getTryLabels().push(labels);
		Label mergeLabel = mBuilder.newLabel();

		// try block
		mBuilder.mark(labels.getStartLabel());
		this.generateBlockWithNewScope(node.getTryBlockNode());
		mBuilder.mark(labels.getEndLabel());

		List<Node> nodeList = node.getTryBlockNode().getNodeList();
		if(!(nodeList.get(nodeList.size() - 1) instanceof BlockEndNode)) {
			mBuilder.jumpToFinally();
		}
		mBuilder.goTo(mergeLabel);

		// catch block
		for(CatchNode catchNode : node.getCatchNodeList()) {
			this.generateCode(catchNode);
			mBuilder.goTo(mergeLabel);
		}

		// finally block
		this.generateCode(node.getFinallyNode());
		mBuilder.getTryLabels().pop();

		// merge
		mBuilder.mark(mergeLabel);
		return null;
	}

	@Override
	public Void visit(CatchNode node) {
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		TryCatchLabel labels = mBuilder.getTryLabels().peek();
		mBuilder.enterScope();

		DSType exceptionType = node.getExceptionType();
		Type exceptionTypeDesc = TypeUtils.toExceptionTypeDescriptor(exceptionType);
		mBuilder.catchException(labels.getStartLabel(), labels.getEndLabel(), exceptionTypeDesc);

		// catch and wrap all kind of exception (include java native exception)
		if(exceptionType.getTypeName().equals("Exception")) {
			Method methodDesc = TypeUtils.toExceptionWrapperDescriptor();
			mBuilder.invokeStatic(TypeUtils.toTypeDescriptor(exceptionType), methodDesc);
		}
		mBuilder.createNewVarAndStoreValue(node.getExceptionVarName(), exceptionType);
		this.generateBlockWithCurrentScope(node.getCatchBlockNode());

		// jump to finally
		List<Node> nodeList = node.getCatchBlockNode().getNodeList();
		if(!(nodeList.get(nodeList.size() - 1) instanceof BlockEndNode)) {
			mBuilder.jumpToFinally();
		}

		mBuilder.exitScope();
		return null;
	}

	@Override
	public Void visit(FinallyNode node) {
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		mBuilder.mark(mBuilder.getTryLabels().peek().getFinallyLabel());
		mBuilder.enterScope();
		mBuilder.storeReturnAddr();
		this.generateBlockWithCurrentScope(node.getBlockNode());
		mBuilder.returnFromFinally();

		mBuilder.exitScope();
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
		/**
		 * assign to element
		 */
		if(leftNode instanceof IndexNode) {
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
		} else if(leftNode instanceof AccessNode) {
			AccessNode getterNode = (AccessNode) leftNode;
			this.generateCode(getterNode.getRecvNode());
			this.generateRightValue(node);
			getterNode.getHandle().callSetter(mBuilder);
		}
		return null;
	}

	private void generateAssignToElement(AssignNode node) {
		MethodBuilder mBuilder = this.getCurrentMethodBuilder();
		IndexNode leftNode = (IndexNode) node.getLeftNode();
		MethodHandle handle = node.getHandle();
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
		MethodHandle handle = node.getHandle();
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
		mBuilder.enterScope();
		// set argument decl
		int size = node.getArgDeclNodeList().size();
		for(int i = 0; i < size; i++) {
			SymbolNode argNode = node.getArgDeclNodeList().get(i);
			DSType argType = node.getHolderType().getFuncHandle().getParamTypeList().get(i);
			mBuilder.defineArgument(argNode.getSymbolName(), argType);
		}
		this.generateBlockWithCurrentScope(node.getBlockNode());
		mBuilder.exitScope();
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
		adapter.invokeConstructor(Type.getType(Object.class), initDesc);
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
