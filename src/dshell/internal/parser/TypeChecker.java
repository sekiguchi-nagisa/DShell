package dshell.internal.parser;

import java.util.ArrayList;
import java.util.List;

import dshell.internal.lib.Utils;
import dshell.internal.parser.Node.ArrayNode;
import dshell.internal.parser.Node.AssertNode;
import dshell.internal.parser.Node.AssignNode;
import dshell.internal.parser.Node.AssignableNode;
import dshell.internal.parser.Node.BlockEndNode;
import dshell.internal.parser.Node.BlockNode;
import dshell.internal.parser.Node.BooleanValueNode;
import dshell.internal.parser.Node.BreakNode;
import dshell.internal.parser.Node.CastNode;
import dshell.internal.parser.Node.CatchNode;
import dshell.internal.parser.Node.ClassNode;
import dshell.internal.parser.Node.ProcessNode;
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
import dshell.internal.parser.Node.LoopNode;
import dshell.internal.parser.Node.MapNode;
import dshell.internal.parser.Node.OperatorCallNode;
import dshell.internal.parser.Node.PairNode;
import dshell.internal.parser.Node.QuotedTaskNode;
import dshell.internal.parser.Node.ReturnNode;
import dshell.internal.parser.Node.RootNode;
import dshell.internal.parser.Node.StringValueNode;
import dshell.internal.parser.Node.SymbolNode;
import dshell.internal.parser.Node.TaskNode;
import dshell.internal.parser.Node.ThrowNode;
import dshell.internal.parser.Node.TryNode;
import dshell.internal.parser.Node.VarDeclNode;
import dshell.internal.parser.Node.WhileNode;
import dshell.internal.parser.SymbolTable.SymbolEntry;
import dshell.internal.parser.error.DShellErrorListener;
import dshell.internal.parser.error.DShellErrorListener.TypeErrorKind;
import dshell.internal.process.OutputBuffer;
import dshell.internal.type.CalleeHandle;
import dshell.internal.type.ClassType;
import dshell.internal.type.DSType;
import dshell.internal.type.GenericType;
import dshell.internal.type.CalleeHandle.ConstructorHandle;
import dshell.internal.type.CalleeHandle.FieldHandle;
import dshell.internal.type.CalleeHandle.MethodHandle;
import dshell.internal.type.CalleeHandle.OperatorHandle;
import dshell.internal.type.DSType.BoxedPrimitiveType;
import dshell.internal.type.DSType.FuncHolderType;
import dshell.internal.type.DSType.FunctionType;
import dshell.internal.type.DSType.PrimitiveType;
import dshell.internal.type.DSType.UnresolvedType;
import dshell.internal.type.DSType.VoidType;
import dshell.internal.type.TypePool;
import dshell.lang.GenericPair;

public class TypeChecker implements NodeVisitor<Node> {
	private final TypePool typePool;
	private final SymbolTable symbolTable;
	private final AbstractOperatorTable opTable;
	private DShellErrorListener error;

	public TypeChecker(TypePool typePool) {
		this.typePool = typePool;
		this.symbolTable = new SymbolTable();
		this.opTable = new OperatorTable(this.typePool);
		this.error = new DShellErrorListener();
	}

	/**
	 * check type.
	 * @param targetNode
	 * @return
	 * - this node. if node type is void type, always success.
	 */
	private Node checkTypeAcceptingVoidType(Node targetNode) {
		return this.checkType(null, targetNode, null);
	}

	/**
	 * check node type.
	 * @param targetNode
	 * @return
	 * - typed this node.
	 * if node type is void type, throw exception
	 */
	private Node checkType(Node targetNode) {
		return this.checkType(null, targetNode, TypePool.voidType);
	}

	/**
	 * check node type
	 * @param requiredType
	 * - not null
	 * @param targetNode
	 * @return
	 * - typed this node.
	 * if requiredType is not equivalent to node type, throw exception.
	 */
	private Node checkType(DSType requiredType, Node targetNode) {
		assert requiredType != null;
		return this.checkType(requiredType, targetNode, null);
	}

	/**
	 * check node type
	 * @param requiredType
	 * - may be null
	 * @param targetNode
	 * @param unacceptableType
	 * - may be null
	 * @return
	 * - typed this node.
	 * if requiredType is not equivalent to node type, throw exception.
	 * if requiredType is null, do not try matching node type 
	 * and if unaccepatbelType is equivalent to node type, throw exception.
	 */
	private Node checkType(DSType requiredType, Node targetNode, DSType unacceptableType) {
		/**
		 * if target node is statement, always check type.
		 */
		if(!(targetNode instanceof ExprNode)) {
			return targetNode.accept(this);
		}

		/**
		 * if target node is expr node and unresolved type, 
		 * try type check.
		 */
		ExprNode exprNode = (ExprNode) targetNode;
		if(exprNode.getType() instanceof UnresolvedType) {
			exprNode = (ExprNode) exprNode.accept(this);
		}

		/**
		 * after type checking, if type is still unresolved type, 
		 * throw exception.
		 */
		DSType type = exprNode.getType();
		if(type instanceof UnresolvedType) {
			this.error.reportTypeError(exprNode, TypeErrorKind.Unresolved);
		}

		/**	
		 * do not try type matching.
		 */
		if(requiredType == null) {
			if(unacceptableType != null && unacceptableType.isAssignableFrom(type)) {
				this.error.reportTypeError(exprNode, TypeErrorKind.Unacceptable, type);
			}
			return exprNode;
		}

		/**
		 * try type matching.
		 */
		if(requiredType.isAssignableFrom(type)) {
			return exprNode;
		}

		/**
		 * primitive type boxing for generic type.
		 */
		if((requiredType instanceof BoxedPrimitiveType) && (type instanceof PrimitiveType)) {
			if(((BoxedPrimitiveType) requiredType).isAcceptableType((PrimitiveType) type)) {
				return CastNode.wrapPrimitive(exprNode);
			}
			// int to float cast and boxing
			if(((BoxedPrimitiveType) requiredType).getUnwrappedType().equals(this.typePool.floatType) &&
					type.equals(this.typePool.intType)) {
				return CastNode.wrapPrimitive(CastNode.intToFloat(this.typePool, exprNode));
			}
		}

		/**
		 * int to float cast
		 */
		if(requiredType.equals(this.typePool.floatType) && type.equals(this.typePool.intType)) {
			return CastNode.intToFloat(this.typePool, exprNode);
		}
		this.error.reportTypeError(exprNode, TypeErrorKind.Required, requiredType, type);
		return null;
	}

	private ClassType checkAndGetClassType(ExprNode recvNode) {
		this.checkType(this.typePool.objectType, recvNode);
		DSType resolvedType = recvNode.getType();
		if(!(resolvedType instanceof ClassType)) {
			this.error.reportTypeError(recvNode, TypeErrorKind.Required, this.typePool.objectType, resolvedType);
			return null;
		}
		return (ClassType) resolvedType;
	}

	/**
	 * check node type and try type matching.
	 * used for FuncCallNode, ConstructorCallNode and MethodCallNode
	 * @param requireTypeList
	 * @param paramNodeList
	 * - after type matching, paramNode structure may be changed due to primitive boxing.
	 * @param index
	 */
	private void checkParamTypeAt(List<DSType> requireTypeList, List<ExprNode> paramNodeList, int index) {
		ExprNode checkedNode = (ExprNode) this.checkType(requireTypeList.get(index), paramNodeList.get(index));
		paramNodeList.set(index, checkedNode);
	}

	/**throwAndReportTypeError
	 * create new symbol table and check type each node within block.
	 * after type checking, remove current symbol table.
	 * @param blockNode
	 */
	private void checkTypeWithNewBlockScope(BlockNode blockNode) {
		this.symbolTable.createAndPushNewTable();
		this.checkTypeWithCurrentBlockScope(blockNode);
		this.symbolTable.popCurrentTable();
	}

	/**
	 * check type each node within block in current block scope.
	 * @param blockNode
	 */
	private void checkTypeWithCurrentBlockScope(BlockNode blockNode) {
		blockNode.accept(this);
	}

	private void addEntryAndThrowIfDefined(Node node, String symbolName, DSType type, boolean isReadOnly) {
		if(!this.symbolTable.addEntry(symbolName, type, isReadOnly)) {
			this.error.reportTypeError(node, TypeErrorKind.DefinedSymbol, symbolName);
		}
	}

	/**
	 * check node inside loop.
	 * if node is out of loop, throw exception
	 * @param node
	 * - break node or continue node
	 */
	private void checkAndThrowIfOutOfLoop(Node node) {
		Node parentNode = node.getParentNode();
		while(true) {
			if(parentNode == null) {
				break;
			}
			if((parentNode instanceof FunctionNode) || (parentNode instanceof RootNode)) {
				break;
			}
			if(parentNode instanceof LoopNode) {
				return;
			}
			parentNode = parentNode.getParentNode();
		}
		this.error.reportTypeError(node, TypeErrorKind.InsideLoop);
	}

	private boolean findBlockEnd(BlockNode blockNode) {
		if(blockNode instanceof EmptyBlockNode) {
			return false;
		}
		List<Node> nodeList = blockNode.getNodeList();
		int endIndex = nodeList.size() - 1;
		if(endIndex < 0) {
			return false;
		}
		Node endNode = nodeList.get(endIndex);
		if(endNode instanceof BlockEndNode) {
			return true;
		}
		if(endNode instanceof IfNode) {
			IfNode ifNode = (IfNode) endNode;
			return this.findBlockEnd(ifNode.getThenBlockNode()) && this.findBlockEnd(ifNode.getElseBlockNode());
		}
		return false;
	}

	/**
	 * check block end (return, throw) existence in function block.
	 * @param blockNode
	 * - function block.
	 * @param returnType
	 * - function return type.
	 */
	private void checkBlockEndExistence(BlockNode blockNode, DSType returnType) {
		int endIndex = blockNode.getNodeList().size() - 1;
		Node endNode = blockNode.getNodeList().get(endIndex);
		if((returnType instanceof VoidType) && !(endNode instanceof BlockEndNode)) {
			blockNode.getNodeList().add(new ReturnNode(null));
			return;
		}
		if(!this.findBlockEnd(blockNode)) {
			this.error.reportTypeError(endNode, TypeErrorKind.UnfoundReturn);
		}
	}

	/**
	 * reset symbol table when error happened.
	 */
	public void reset() {
		this.symbolTable.popAllLocal();
		this.symbolTable.removeCachedEntries();
	}

	public void setErrorListener(DShellErrorListener listener) {
		this.error = listener;
	}

	// visitotr api
	@Override
	public Node visit(IntValueNode node) {
		node.setType(this.typePool.intType);
		return node;
	}

	@Override
	public Node visit(FloatValueNode node) {
		node.setType(this.typePool.floatType);
		return node;
	}

	@Override
	public Node visit(BooleanValueNode node) {
		node.setType(this.typePool.booleanType);
		return node;
	}

	@Override
	public Node visit(StringValueNode node) {
		node.setType(this.typePool.stringType);
		return node;
	}

	@Override
	public Node visit(ArrayNode node) {
		int elementSize = node.getNodeList().size();
		assert elementSize != 0;
		ExprNode firstElementNode = node.getNodeList().get(0);
		this.checkType(firstElementNode);
		DSType elementType = firstElementNode.getType();
		for(int i = 1; i < elementSize; i++) {
			node.getNodeList().set(i, (ExprNode) this.checkType(elementType, node.getNodeList().get(i)));
		}
		String baseArrayName = this.typePool.baseArrayType.getTypeName();
		List<DSType> elementTypeList = new ArrayList<>(1);
		elementTypeList.add(elementType);
		node.setType(this.typePool.createAndGetReifiedTypeIfUndefined(baseArrayName, elementTypeList));
		return node;
	}

	@Override
	public Node visit(MapNode node) {
		int entrySize = node.getKeyList().size();
		assert entrySize != 0;
		ExprNode firstValueNode = node.getValueList().get(0);
		this.checkType(firstValueNode);
		DSType valueType = firstValueNode.getType();
		for(int i = 0; i < entrySize; i++) {
			this.checkType(this.typePool.stringType, node.getKeyList().get(i));
			node.getValueList().set(i, (ExprNode) this.checkType(valueType, node.getValueList().get(i)));
		}
		String baseMapName = this.typePool.baseMapType.getTypeName();
		List<DSType> valueTypeList = new ArrayList<>(1);
		valueTypeList.add(valueType);
		node.setType(this.typePool.createAndGetReifiedTypeIfUndefined(baseMapName, valueTypeList));
		return node;
	}

	@Override
	public Node visit(PairNode node) {
		DSType leftType = ((ExprNode)this.checkType(node.getLeftNode())).getType();
		DSType rightType = ((ExprNode)this.checkType(node.getRightNode())).getType();
		List<DSType> elementTypeList = new ArrayList<>(2);
		elementTypeList.add(leftType);
		elementTypeList.add(rightType);
		String basePairName = this.typePool.basePairType.getTypeName();
		node.setType(this.typePool.createAndGetReifiedTypeIfUndefined(basePairName, elementTypeList));
		return node;
	}

	@Override
	public Node visit(SymbolNode node) {
		SymbolEntry entry = this.symbolTable.getEntry(node.getSymbolName());
		if(entry == null) {
			this.error.reportTypeError(node, TypeErrorKind.UndefinedSymbol, node.getSymbolName());
		}
		node.setSymbolEntry(entry);	// call ExprNode#setType
		return node;
	}

	@Override
	public Node visit(ElementGetterNode node) {	//FIXME: adhoc implementation.
		this.checkType(node.getRecvNode());
		DSType recvType = node.getRecvNode().getType();
		String typeName = recvType.getTypeName();
		if(!typeName.startsWith("Array<") && 
				!typeName.startsWith("Map<") && !typeName.equals("String")) {
			this.error.reportTypeError(node, TypeErrorKind.Required, "Map, Array or String type", recvType);
		}
		MethodHandle handle = recvType.lookupMethodHandle("get");
		if(handle == null || handle.getParamTypeList().size() != 1) {
			Utils.fatal(1, "illegal method: get");
		}
		this.checkType(handle.getParamTypeList().get(0), node.getIndexNode());
		node.setGetterHandle(handle);
		node.setType(handle.getReturnType());
		return node;
	}

	@Override
	public Node visit(FieldGetterNode node) {
		ClassType recvType = this.checkAndGetClassType(node.getRecvNode());
		FieldHandle handle = recvType.lookupFieldHandle(node.getFieldName());
		if(handle == null) {
			this.error.reportTypeError(node, TypeErrorKind.UndefinedField, node.getFieldName());
			return null;
		}
		node.setHandle(handle);
		node.setType(handle.getFieldType());
		return node;
	}

	@Override
	public Node visit(CastNode node) {
		this.checkType(node.getExprNode());
		DSType type = node.getExprNode().getType();
		DSType targetType = node.getTypeSymbol().toType(this.typePool);
		node.setType(targetType);

		// resolve cast op
		if(type.equals(targetType)) {
			return node;
		}
		if(targetType instanceof VoidType) {
			this.error.reportTypeError(node, TypeErrorKind.CastOp, type, targetType);
		}
		if(type.equals(this.typePool.intType) && targetType.equals(this.typePool.floatType)) {
			node.resolveCastOp(CastNode.INT_2_FLOAT);
		} else if(type.equals(this.typePool.floatType) && targetType.equals(this.typePool.intType)) {
			node.resolveCastOp(CastNode.FLOAT_2_INT);
		} else if(targetType.getTypeName().equals("String")) {
			node.resolveCastOp(CastNode.TO_STRING);
		} else if(!(type instanceof PrimitiveType) && !(targetType instanceof PrimitiveType) &&
				!(type instanceof GenericType) && !(targetType instanceof GenericType) &&
				!(type instanceof FunctionType) && !(targetType instanceof FunctionType)) {
			node.resolveCastOp(CastNode.CHECK_CAST);
		} else {
			this.error.reportTypeError(node, TypeErrorKind.CastOp, type, targetType);
		}
		return node;
	}

	@Override
	public Node visit(InstanceofNode node) {
		DSType exprType = ((ExprNode)this.checkType(node.getExprNode())).getType();
		node.setTargetType(this.typePool);
		DSType targetType = node.getTargetType();

		// resolve instanceof op
		if((targetType instanceof VoidType) || (exprType instanceof VoidType)) {
			node.resolveOpType(InstanceofNode.ALWAYS_FALSE);
		} else if((exprType instanceof PrimitiveType) || (targetType instanceof PrimitiveType) || 
				(targetType instanceof GenericType) || (targetType instanceof FunctionType)) {
			node.resolveOpType(InstanceofNode.COMP_TYPE);
		} else {
			node.resolveOpType(InstanceofNode.INSTANCEOF);
		}
		node.setType(this.typePool.booleanType);
		return node;
	}

	@Override
	public Node visit(OperatorCallNode node) {
		int size = node.getNodeList().size();
		assert (size > 0 && size < 3);
		List<ExprNode> paramNodeList = node.getNodeList();
		for(Node paramNode : paramNodeList) {
			this.checkType(paramNode);
		}
		OperatorHandle handle;
		if(size == 1) {
			DSType rightType = paramNodeList.get(0).getType();
			handle = this.opTable.getOperatorHandle(node.getFuncName(), rightType);
			if(handle == null) {
				this.error.reportTypeError(node, TypeErrorKind.UnaryOp, node.getFuncName(), rightType);
			}
		} else {
			DSType leftType = paramNodeList.get(0).getType();
			DSType rightType = paramNodeList.get(1).getType();
			handle = this.opTable.getOperatorHandle(node.getFuncName(), leftType, rightType);
			if(handle == null) {
				this.error.reportTypeError(node, TypeErrorKind.BinaryOp, leftType, node.getFuncName(), rightType);
			}
		}
		node.setHandle(handle);
		node.setType(handle.getReturnType());
		return node;
	}

	/**
	 * treat invoke node as function call
	 * @param node
	 * @return
	 */
	protected Node checkTypeAsFuncCall(ApplyNode node) {
		node.setAsFuncCallNode();
		MethodHandle handle = this.lookupFuncHandle(node.getRecvNode());

		// check param type
		int paramSize = handle.getParamTypeList().size();
		if(handle.getParamTypeList().size() != node.getArgList().size()) {
			this.error.reportTypeError(node, TypeErrorKind.UnmatchParam, paramSize, node.getArgList().size());
		}
		for(int i = 0; i < paramSize; i++) {
			this.checkParamTypeAt(handle.getParamTypeList(), node.getArgList(), i);
		}
		node.setHandle(handle);
		node.setType(handle.getReturnType());
		return node;
	}

	protected MethodHandle lookupFuncHandle(ExprNode recvNode) {
		// look up static function
		if(recvNode instanceof SymbolNode) {
			String funcName = ((SymbolNode) recvNode).getSymbolName();
			SymbolEntry entry = this.symbolTable.getEntry(funcName);
			if(entry != null && entry.getType() instanceof FuncHolderType) {
				return ((FuncHolderType) entry.getType()).getFuncHandle();
			}
		}
		// look up function
		this.checkType(this.typePool.baseFuncType, recvNode);
		return ((FunctionType) recvNode.getType()).getHandle();
	}

	/**
	 * treat invoke node as method call.
	 * @param node
	 * @return
	 */
	protected Node checkTypeAsMethodCall(ApplyNode node) {
		FieldGetterNode getterNode = (FieldGetterNode) node.getRecvNode();
		String methodName = getterNode.getFieldName();
		ClassType recvType = this.checkAndGetClassType(getterNode.getRecvNode());
		MethodHandle handle = recvType.lookupMethodHandle(methodName);
		if(handle == null) {
			this.error.reportTypeError(node, TypeErrorKind.UndefinedMethod, methodName);
			return null;
		}
		int paramSize = handle.getParamTypeList().size();
		if(handle.getParamTypeList().size() != node.getArgList().size()) {
			this.error.reportTypeError(node, TypeErrorKind.UnmatchParam, paramSize, node.getArgList().size());
		}
		for(int i = 0; i < paramSize; i++) {
			this.checkParamTypeAt(handle.getParamTypeList(), node.getArgList(), i);
		}
		node.setHandle(handle);
		node.setType(handle.getReturnType());
		return node;
	}

	@Override
	public Node visit(ApplyNode node) {
		if(node.getRecvNode() instanceof FieldGetterNode) {
			return this.checkTypeAsMethodCall(node);
		}
		return this.checkTypeAsFuncCall(node);
	}

	@Override
	public Node visit(ConstructorCallNode node) {
		DSType recvType = node.getTypeSymbol().toType(this.typePool);
		List<DSType> paramTypeList = new ArrayList<>();
		for(ExprNode paramNode : node.getNodeList()) {
			this.checkType(paramNode);
			paramTypeList.add(paramNode.getType());
		}
		ConstructorHandle handle = recvType.lookupConstructorHandle(paramTypeList);
		if(handle == null) {
			StringBuilder sBuilder = new StringBuilder();
			sBuilder.append(recvType);
			for(DSType paramType : paramTypeList) {
				sBuilder.append(" ");
				sBuilder.append(paramType.toString());
			}
			this.error.reportTypeError(node, TypeErrorKind.UndefinedInit, sBuilder.toString());
			return null;
		}
		int size = handle.getParamTypeList().size();
		for(int i = 0; i < size; i++) {
			this.checkParamTypeAt(handle.getParamTypeList(), node.getNodeList(), i);
		}
		node.setHandle(handle);
		node.setType(recvType);
		return node;
	}

	@Override
	public Node visit(CondOpNode node) {
		String condOp = node.getConditionalOp();
		DSType booleanType = this.typePool.booleanType;
		if(!condOp.equals("&&") && !condOp.equals("||")) {
			this.error.reportTypeError(node, TypeErrorKind.BinaryOp, booleanType, condOp, booleanType);
		}
		this.checkType(booleanType, node.getLeftNode());
		this.checkType(booleanType, node.getRightNode());
		node.setType(booleanType);
		return node;
	}

	@Override
	public Node visit(ProcessNode node) {
		for(ExprNode argNode : node.getArgNodeList()) {
			this.checkType(this.typePool.stringType, argNode);
		}
		// check type redirect options
		for(GenericPair<Integer, ExprNode> pair : node.getRedirOptionList()) {
			this.checkType(pair.getRight());
		}
		node.setType(TypePool.voidType);	// ProcessNode is always void type
		return node;
	}

	@Override
	public Node visit(TaskNode node) {
		for(ProcessNode procNode : node.getProcNodeList()) {
			this.checkTypeAcceptingVoidType(procNode);	// accept void type
		}

		/**
		 * resolve task type
		 */
		Node parentNode = node.getParentNode();
		/**
		 * as void type in statement.
		 */
		if((parentNode instanceof RootNode) || (parentNode instanceof BlockNode)) {
			node.setType(TypePool.voidType);

		}
		else if((parentNode instanceof VarDeclNode) || (parentNode instanceof AssignNode)) {
			/**
			 * as Task type if background.
			 */
			if(node.isBackGround()) { 
				node.setType(this.typePool.taskType);
			}
			/**
			 * as int type in variable declaration or assign statement.
			 */
			else {
				node.setType(this.typePool.intType);
			}
		}
		/**
		 * as boolean type in conditional logical operation or if condition.
		 */
		else if((parentNode instanceof IfNode) || (parentNode instanceof CondOpNode)) {
			node.setType(this.typePool.booleanType);
		}
		/**
		 * otherwise, as int type.
		 */
		else {
			node.setType(this.typePool.booleanType);
		}
		return node;
	}

	@Override
	public Node visit(QuotedTaskNode node) {
		ExprNode exprNode = (ExprNode) this.checkType(node.getExprNode());
		// generate var entry of output buffer
		String internalName = OutputBuffer.class.getCanonicalName().replace('.', '/');
		String typeName = internalName.substring(internalName.lastIndexOf('/'));
		DSType varType = new DSType(typeName, internalName) {};
		String bufferName = node.getBufferName();
		GenericPair<String, DSType> bufferEntry = new GenericPair<>(bufferName, varType);
		node.setEntry(bufferEntry);

		// add entry to expr node
		this.setEntryToTaskNode(bufferEntry, exprNode);

		// resolve node type
		Node parentNode = node.getParentNode();
		if((parentNode instanceof ProcessNode) || (parentNode instanceof ForInNode)) {
			List<DSType> elementTypeList = new ArrayList<>(1);
			elementTypeList.add(this.typePool.stringType);
			node.setType(this.typePool.createAndGetReifiedTypeIfUndefined("Array", elementTypeList));
		} else {
			node.setType(this.typePool.stringType);
		}
		return node;
	}

	private void setEntryToTaskNode(GenericPair<String, DSType> entry, ExprNode targetNode) {
		if(targetNode instanceof TaskNode) {
			 ((TaskNode)targetNode).setBufferEntry(entry);
		} else if(targetNode instanceof CondOpNode) {
			CondOpNode condOpNode = (CondOpNode) targetNode;
			this.setEntryToTaskNode(entry, condOpNode.getLeftNode());
			this.setEntryToTaskNode(entry, condOpNode.getRightNode());
		} else {
			Utils.fatal(1, "illeagal node type: " + targetNode.getClass());
		}
	}

	@Override
	public Node visit(AssertNode node) {
		this.checkType(this.typePool.booleanType, node.getExprNode());
		OperatorHandle handle = this.opTable.getOperatorHandle(AssertNode.opName, this.typePool.booleanType);
		if(handle == null) {
			this.error.reportTypeError(node, TypeErrorKind.UnaryOp, AssertNode.opName, this.typePool.booleanType);
		}
		node.setHandle(handle);
		return node;
	}

	@Override
	public Node visit(BlockNode node) {
		int size = node.getNodeList().size();
		for(int i = 0; i < size; i++) {
			Node targetNode = node.getNodeList().get(i);
			this.checkTypeAcceptingVoidType(targetNode);
			if((targetNode instanceof BlockEndNode) && (i != size - 1)) {
				this.error.reportTypeError(node.getNodeList().get(i + 1), TypeErrorKind.Unreachable);
			}
		}
		return node;
	}

	@Override
	public Node visit(BreakNode node) {
		this.checkAndThrowIfOutOfLoop(node);
		return node;
	}

	@Override
	public Node visit(ContinueNode node) {
		this.checkAndThrowIfOutOfLoop(node);
		return node;
	}

	@Override
	public Node visit(ExportEnvNode node) {
		DSType stringType = this.typePool.stringType;
		this.addEntryAndThrowIfDefined(node, node.getEnvName(), stringType, true);
		this.checkType(stringType, node.getExprNode());
		OperatorHandle handle = this.opTable.getOperatorHandle("setEnv", stringType, stringType);
		if(handle == null) {
			Utils.fatal(1, "undefined operator: setEnv(String, String)");
		}
		node.setHandle(handle);
		return node;
	}

	@Override
	public Node visit(ImportEnvNode node) {
		DSType stringType = this.typePool.stringType;
		this.addEntryAndThrowIfDefined(node, node.getEnvName(), stringType, true);
		OperatorHandle handle = this.opTable.getOperatorHandle("getEnv", stringType);
		if(handle == null) {
			Utils.fatal(1, "undefined operator: getEnv(String)");
		}
		node.setHandle(handle);
		return node;
	}

	@Override
	public Node visit(ForNode node) {
		this.symbolTable.createAndPushNewTable();
		this.checkTypeAcceptingVoidType(node.getInitNode());
		this.checkType(this.typePool.booleanType, node.getCondNode());
		this.checkTypeAcceptingVoidType(node.getIterNode());
		this.checkTypeWithCurrentBlockScope(node.getBlockNode());
		this.symbolTable.popCurrentTable();
		return node;
	}

	@Override
	public Node visit(ForInNode node) {
		// look up iterator op.
		DSType exprType = ((ExprNode)this.checkType(node.getExprNode())).getType();
		if(!exprType.getTypeName().startsWith("Array<")) {
			this.error.reportTypeError(node.getExprNode(), TypeErrorKind.Required, "Array type");
		}
		MethodHandle reset = exprType.lookupMethodHandle("$iter$Reset");
		MethodHandle next = exprType.lookupMethodHandle("$iter$Next");
		MethodHandle hasNext = exprType.lookupMethodHandle("$iter$HasNext");
		if(reset == null || next == null || hasNext == null) {
			Utils.fatal(1, "iterator op is not found.");
		}
		node.setIteratorHandles(reset, next, hasNext);

		// add symbol entry
		this.symbolTable.createAndPushNewTable();
		this.addEntryAndThrowIfDefined(node, node.getInitName(), next.getReturnType(), false);
		this.checkTypeWithCurrentBlockScope(node.getBlockNode());
		this.symbolTable.popCurrentTable();
		return node;
	}

	@Override
	public Node visit(WhileNode node) {
		this.checkType(this.typePool.booleanType, node.getCondNode());
		this.checkTypeWithNewBlockScope(node.getBlockNode());
		return node;
	}

	@Override
	public Node visit(IfNode node) {
		this.checkType(this.typePool.booleanType, node.getCondNode());
		this.checkTypeWithNewBlockScope(node.getThenBlockNode());
		this.checkTypeWithNewBlockScope(node.getElseBlockNode());
		return node;
	}

	@Override
	public Node visit(ReturnNode node) {
		DSType returnType = this.symbolTable.getCurrentReturnType();
		if(returnType instanceof UnresolvedType) {
			this.error.reportTypeError(node, TypeErrorKind.InsideFunc);
		}
		this.checkType(returnType, node.getExprNode());
		if(node.getExprNode().getType() instanceof VoidType) {
			if(!(node.getExprNode() instanceof EmptyNode)) {
				this.error.reportTypeError(node, TypeErrorKind.NotNeedExpr);
			}
		}
		return node;
	}

	@Override
	public Node visit(ThrowNode node) {
		this.checkType(this.typePool.exceptionType, node.getExprNode());
		return node;
	}

	@Override
	public Node visit(TryNode node) {
		this.checkTypeWithNewBlockScope(node.getTryBlockNode());
		for(CatchNode catchNode : node.getCatchNodeList()) {
			this.checkType(catchNode);
		}
		this.checkTypeWithNewBlockScope(node.getFinallyBlockNode());

		/**
		 * verify catch block order.
		 */
		final int size = node.getCatchNodeList().size();
		for(int i = 0; i < size - 1; i++) {
			DSType curType = node.getCatchNodeList().get(i).getExceptionType();
			CatchNode nextNode = node.getCatchNodeList().get(i + 1);
			DSType nextType = nextNode.getExceptionType();
			if(curType.isAssignableFrom(nextType)) {
				this.error.reportTypeError(nextNode, TypeErrorKind.Unreachable);
			}
		}
		return node;
	}

	@Override
	public Node visit(CatchNode node) {
		/**
		 * resolve exception type.
		 */
		TypeSymbol typeSymbol = node.getTypeSymbol();
		DSType exceptionType = this.typePool.exceptionType;
		if(typeSymbol != null) {
			exceptionType = typeSymbol.toType(this.typePool);
		}
		if(!this.typePool.exceptionType.isAssignableFrom(exceptionType)) {
			this.error.reportTypeError(node, TypeErrorKind.Required, this.typePool.exceptionType, exceptionType);
		}
		node.setExceptionType((ClassType) exceptionType);
		/**
		 * check type catch block.
		 */
		this.symbolTable.createAndPushNewTable();
		this.addEntryAndThrowIfDefined(node, node.getExceptionVarName(), exceptionType, true);
		this.checkTypeWithCurrentBlockScope(node.getCatchBlockNode());
		this.symbolTable.popCurrentTable();
		return node;
	}

	@Override
	public Node visit(VarDeclNode node) {
		this.checkType(node.getInitValueNode());
		this.addEntryAndThrowIfDefined(node, node.getVarName(), node.getInitValueNode().getType(), node.isReadOnly());
		node.setGlobal(node.getParentNode() instanceof RootNode);
		return node;
	}

	private void checkIsAssignable(ExprNode leftNode) {
		if(!(leftNode instanceof AssignableNode)) {
			this.error.reportTypeError(leftNode, TypeErrorKind.Assignable);
		}
		AssignableNode assignableNode = (AssignableNode) leftNode;
		if(assignableNode.isReadOnly()) {
			this.error.reportTypeError(assignableNode, TypeErrorKind.ReadOnly);
		}
	}

	private Node checkTypeAsAssignToElement(AssignNode node) {
		ElementGetterNode getterNode = (ElementGetterNode) node.getLeftNode();
		this.checkIsAssignable(getterNode);

		this.checkType(getterNode);
		DSType recvType = getterNode.getRecvNode().getType();
		String recvTypeName = recvType.getTypeName();
		if(!recvTypeName.startsWith("Array<") && !recvTypeName.startsWith("Map<")) {
			this.error.reportTypeError(getterNode, TypeErrorKind.Required, "Array or Map type", recvType);
		}
		MethodHandle handle = recvType.lookupMethodHandle("set");
		if(handle == null || handle.getParamTypeList().size() != 2) {
			Utils.fatal(1, "illegal method: set");
		}
		getterNode.setSetterHandle(handle);

		String op = node.getAssignOp();
		if(op.equals("=")) {
			node.setRightNode((ExprNode) this.checkType(handle.getParamTypeList().get(1), node.getRightNode()));
		} else {
			DSType leftType = getterNode.getGetterHandle().getReturnType();
			String opPrefix = op.substring(0, op.length() - 1);
			DSType rightType = ((ExprNode) this.checkType(node.getRightNode())).getType();
			MethodHandle opHandle = this.opTable.getOperatorHandle(opPrefix, leftType, rightType);
			if(opHandle == null) {
				this.error.reportTypeError(node, TypeErrorKind.BinaryOp, leftType, opPrefix, rightType);
			}
			if(!leftType.isAssignableFrom(opHandle.getReturnType())) {
				this.error.reportTypeError(getterNode, TypeErrorKind.Required, leftType, opHandle.getReturnType());
			}
			node.setHandle(this.checkMethodHandleReturnType(opHandle, handle.getParamTypeList().get(1)));
		}
		return node;
	}

	private MethodHandle checkMethodHandleReturnType(MethodHandle handle, DSType requiredType) {
		DSType returnType = handle.getReturnType();
		if(requiredType.isAssignableFrom(returnType)) {
			return handle;
		}
		if((requiredType instanceof BoxedPrimitiveType) && (returnType instanceof PrimitiveType)) {
			if(((BoxedPrimitiveType) requiredType).isAcceptableType((PrimitiveType) returnType)) {
				return new CalleeHandle.BoxedHandle(handle);
			}
		}
		Utils.fatal(1, "require " + requiredType + ", but is " + returnType);
		return null;
	}

	@Override
	public Node visit(AssignNode node) {
		ExprNode leftNode = node.getLeftNode();

		/**
		 * assign to element
		 */
		if(leftNode instanceof ElementGetterNode) {
			return this.checkTypeAsAssignToElement(node);
		}

		/**
		 * assign to symbol or field
		 */
		String op = node.getAssignOp();
		DSType leftType = ((ExprNode) this.checkType(leftNode)).getType();
		this.checkIsAssignable(leftNode);
		if(op.equals("=")) {	// left = right
			node.setRightNode((ExprNode) this.checkType(leftType, node.getRightNode()));
		} else {	// left = left op right
			String opPrefix = op.substring(0, op.length() - 1);
			this.checkType(node.getRightNode());
			DSType rightType = node.getRightNode().getType();
			OperatorHandle handle = this.opTable.getOperatorHandle(opPrefix, leftType, rightType);
			if(handle == null) {
				this.error.reportTypeError(node, TypeErrorKind.BinaryOp, leftType, opPrefix, rightType);
			}
			if(!leftType.isAssignableFrom(handle.getReturnType())) {
				this.error.reportTypeError(leftNode, TypeErrorKind.Required, leftType, handle.getReturnType());
			}
			node.setHandle(handle);
		}
		return node;
	}

	@Override
	public Node visit(FunctionNode node) {
		// create function type and holder type
		List<TypeSymbol> paramTypeSymbols = node.getParamTypeSymbolList();
		List<DSType> paramTypeList = new ArrayList<>(paramTypeSymbols.size());
		for(TypeSymbol typeSymbol : paramTypeSymbols) {
			paramTypeList.add(typeSymbol.toType(this.typePool));
		}
		String funcName = node.getFuncName();
		DSType returnType = node.getReturnTypeSymbol().toType(this.typePool);
		FunctionType funcType = this.typePool.createAndGetFuncTypeIfUndefined(returnType, paramTypeList);
		FuncHolderType holderType = this.typePool.createFuncHolderType(funcType, funcName);
		this.addEntryAndThrowIfDefined(node, funcName, holderType, true);

		// check type func body
		this.symbolTable.pushReturnType(returnType);
		this.symbolTable.createAndPushNewTable();

		int size = paramTypeList.size();
		for(int i = 0; i < size; i++) {
			this.visitParamDecl(node.getArgDeclNodeList().get(i), paramTypeList.get(i));
		}
		this.checkTypeWithCurrentBlockScope(node.getBlockNode());
		this.symbolTable.popCurrentTable();
		this.symbolTable.popReturnType();
		node.setHolderType(holderType);
		// check control structure
		this.checkBlockEndExistence(node.getBlockNode(), returnType);
		return null;
	}

	private void visitParamDecl(SymbolNode paramDeclNode, DSType paramType) {
		this.addEntryAndThrowIfDefined(paramDeclNode, paramDeclNode.getSymbolName(), paramType, true);
	}

	@Override
	public Node visit(ClassNode node) {
		//TODO
		this.error.reportTypeError(node, TypeErrorKind.Unimplemented, node.getClass().getSimpleName());
		return null;
	}

	@Override
	public Node visit(ConstructorNode node) {
		// TODO 
		this.error.reportTypeError(node, TypeErrorKind.Unimplemented, node.getClass().getSimpleName());
		return null;
	}

	@Override
	public Node visit(GlobalVarNode node) {
		node.resolveValueType(this.typePool);
		this.addEntryAndThrowIfDefined(node, node.getVarName(), node.getValueType(), true);
		return node;
	}

	@Override
	public Node visit(EmptyNode node) {
		node.setType(TypePool.voidType);
		return node;
	}

	@Override
	public Node visit(EmptyBlockNode node) {
		return node;	// do nothing
	}

	public RootNode checkTypeRootNode(RootNode node) {
		this.symbolTable.clearEntryCache();
		for(Node targetNode : node.getNodeList()) {
			this.checkTypeAcceptingVoidType(targetNode);
		}
		OperatorHandle handle = this.opTable.getOperatorHandle(RootNode.opName, this.typePool.objectType, this.typePool.stringType);
		if(handle == null) {
			Utils.fatal(1, "undefined operator: " + RootNode.opName + "(" + this.typePool.objectType + ", " + this.typePool.stringType + ")");
		}
		node.setHandle(handle);
		return node;
	}
}
