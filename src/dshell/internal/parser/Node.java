package dshell.internal.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.Token;

import dshell.annotation.ObjectReference;
import dshell.internal.lib.Utils;
import dshell.internal.parser.ParserUtils.ArgDecl;
import dshell.internal.parser.ParserUtils.ArgsDecl;
import dshell.internal.parser.ParserUtils.Arguments;
import dshell.internal.parser.ParserUtils.Block;
import dshell.internal.parser.ParserUtils.IfElseBlock;
import dshell.internal.parser.SymbolTable.SymbolEntry;
import dshell.internal.type.ClassType;
import dshell.internal.type.CalleeHandle.ConstructorHandle;
import dshell.internal.type.CalleeHandle.FieldHandle;
import dshell.internal.type.CalleeHandle.MethodHandle;
import dshell.internal.type.CalleeHandle.OperatorHandle;
import dshell.internal.type.CalleeHandle.StaticFieldHandle;
import dshell.internal.type.DSType.FuncHolderType;
import dshell.internal.type.DSType.FunctionType;
import dshell.internal.type.DSType.PrimitiveType;
import dshell.internal.type.TypePool;
import dshell.internal.type.DSType;
import dshell.lang.GenericPair;

/**
 * Represents dshell grammar element.
 * @author skgchxngsxyz-osx
 *
 */
public abstract class Node {
	/**
	 * for line number generation.
	 * may be null.
	 */
	protected final Token token;

	@ObjectReference
	protected Node parentNode;

	protected Node(Token token) {
		this.token = token;
	}

	public Token getToken() {
		return this.token;
	}

	/**
	 * set parent node reference
	 * @param parentNode
	 */
	public void setParentNode(Node parentNode) {
		this.parentNode = parentNode;
	}

	/**
	 * set node as this node's child
	 * @param childNode
	 * @return
	 * - childNode
	 */
	public Node setNodeAsChild(Node childNode) {
		/**
		 * set child node parent.
		 */
		childNode.setParentNode(this);
		return childNode;
	}

	/**
	 * set expr node as this node's child
	 * @param childNode
	 * @return
	 * - child node
	 */
	public ExprNode setExprNodeAsChild(ExprNode childNode) {
		return (ExprNode) this.setNodeAsChild(childNode);
	}

	/**
	 * get parent node.
	 * @return
	 * - return null, if this node is RootNode.
	 */
	public Node getParentNode() {
		return this.parentNode;
	}

	/**
	 * get first found ancestor node 
	 * @param targetClass
	 * @return
	 * return null, if not found
	 */
	public Node getFirstFoundAncestor(Class<? extends Node> targetClass) {
		Node node = this.getParentNode();
		if(node == null) {
			return null;
		}
		if(targetClass.isAssignableFrom(node.getClass())) {
			return node;
		}
		return node.getFirstFoundAncestor(targetClass);
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + ":void";
	}

	abstract public <T> T accept(NodeVisitor<T> visitor);

	// ##################
	// #   expression   #
	// ##################
	/**
	 * This node represents expression.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static abstract class ExprNode extends Node {
		/**
		 * It represents expression return type.
		 */
		protected DSType type = TypePool.unresolvedType;

		protected ExprNode(Token token) {
			super(token);
		}

		/**
		 * Set evaluated value type of this node.
		 * @param type
		 * - this node's evaluated value type
		 */
		public void setType(DSType type) {
			this.type = type;
		}

		/**
		 * Get this node's evaluated value type
		 * @return
		 */
		public DSType getType() {
			return this.type;
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName() + ":" + this.type;
		}
	}

	/**
	 * Represent constant int value.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class IntValueNode extends ExprNode {
		private final long value;

		public IntValueNode(Token token) {
			super(token);
			this.value = Long.parseLong(token.getText());
		}

		/**
		 * used for assign node.
		 * @param value
		 */
		private IntValueNode(long value) {
			super(null);
			this.value = value;
		}

		public long getValue() {
			return this.value;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * Represents constant float value.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class FloatValueNode extends ExprNode {
		private final double value;

		public FloatValueNode(Token token) {
			super(token);
			this.value = Double.parseDouble(token.getText());
		}

		public double getValue() {
			return this.value;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * Represents constant boolean value.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class BooleanValueNode extends ExprNode {
		private boolean value;

		public BooleanValueNode(Token token) {
			super(token);
			this.value = Boolean.parseBoolean(resolveName(token));
		}

		/**
		 * for ForNode condition
		 * @param value
		 */
		public BooleanValueNode(boolean value) {
			super(null);
			this.value = value;
		}

		public boolean getValue() {
			return this.value;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * Represent constant string value.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class StringValueNode extends ExprNode {
		private final String value;

		public StringValueNode(Token token) {
			this(token, true);
		}

		public StringValueNode(Token token, boolean isSingleQuoteStr) {
			super(token);
			this.value = parseTokenText(this.token, isSingleQuoteStr);
		}
		/**
		 * used for CommandNode
		 * @param value
		 */
		public StringValueNode(String value) {
			super(null);
			this.value = value;
		}

		public static String parseTokenText(Token token, boolean isSingleQuoteStr) {
			StringBuilder sBuilder = new StringBuilder();
			String text = token.getText();
			int startIndex = isSingleQuoteStr ? 1 : 0;
			int endIndex = isSingleQuoteStr ? text.length() - 1 : text.length();
			for(int i = startIndex; i < endIndex; i++) {
				char ch = text.charAt(i);
				if(ch == '\\') {
					char nextCh = text.charAt(++i);
					switch(nextCh) {
					case 't' : ch = '\t'; break;
					case 'b' : ch = '\b'; break;
					case 'n' : ch = '\n'; break;
					case 'r' : ch = '\r'; break;
					case 'f' : ch = '\f'; break;
					case '\'': ch = '\''; break;
					case '"' : ch = '"' ; break;
					case '\\': ch = '\\'; break;
					case '`' : ch = '`' ; break;
					case '$' : ch = '$' ; break;
					}
				}
				sBuilder.append(ch);
			}
			return sBuilder.toString();
		}

		public String getValue() {
			return this.value;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * for string interpolation
	 * @author skgchxngsxyz-opensuse
	 *
	 */
	public static class StringExprNode extends ExprNode {
		private final List<ExprNode> elementList;

		protected StringExprNode(Token token) {
			super(token);
			this.elementList = new ArrayList<>();
		}

		public void addElementNode(ExprNode exprNode) {
			ExprNode elementNode = exprNode;
			if(!(exprNode instanceof StringValueNode) && !(exprNode instanceof StringExprNode) 
					&& !(exprNode instanceof InnerTaskNode)) {
				elementNode = new OperatorCallNode("$", elementNode);
			}
			this.elementList.add(this.setExprNodeAsChild(elementNode));
		}

		public List<ExprNode> getElementList() {
			return this.elementList;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents array literal.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class ArrayNode extends ExprNode {
		private final List<ExprNode> nodeList;

		public ArrayNode(Token token) {
			super(token);
			this.nodeList = new ArrayList<>();
		}

		public void addNode(ExprNode node) {
			this.nodeList.add(this.setExprNodeAsChild(node));
		}

		public List<ExprNode> getNodeList() {
			return this.nodeList;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents map literal.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class MapNode extends ExprNode {
		private final List<ExprNode> keyList;
		private final List<ExprNode> valueList;

		public MapNode(Token token) {
			super(token);
			this.keyList = new ArrayList<>();
			this.valueList = new ArrayList<>();
		}

		public void addEntry(ExprNode keyNode, ExprNode valueNode) {
			this.keyList.add(this.setExprNodeAsChild(keyNode));
			this.valueList.add(this.setExprNodeAsChild(valueNode));
		}

		public List<ExprNode> getKeyList() {
			return this.keyList;
		}

		public List<ExprNode> getValueList() {
			return this.valueList;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	public static class PairNode extends ExprNode {
		private final ExprNode leftNode;
		private final ExprNode rightNode;

		public PairNode(Token token, ExprNode leftNode, ExprNode rightNode) {
			super(token);
			this.leftNode = leftNode;
			this.rightNode = rightNode;
		}

		public ExprNode getLeftNode() {
			return this.leftNode;
		}

		public ExprNode getRightNode() {
			return this.rightNode;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * Represent left node of AssignNode.
	 * @author skgchxngsxyz-opensuse
	 *
	 */
	public static abstract class AssignableNode extends ExprNode {
		protected boolean isReadOnly;

		protected AssignableNode(Token token) {
			super(token);
		}

		public boolean isReadOnly() {
			return this.isReadOnly;
		}
	}

	/**
	 * This node represents local variable.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class SymbolNode extends AssignableNode {
		private final String symbolName;

		/**
		 * used for getting function object from static field.
		 */
		private StaticFieldHandle handle;

		public SymbolNode(Token token) {
			super(token);
			this.symbolName = resolveName(token);
		}

		public String getSymbolName() {
			return this.symbolName;
		}

		public void setSymbolEntry(SymbolEntry entry) {
			this.isReadOnly = entry.isReadOnly();
			DSType type = entry.getType();
			if(type instanceof FuncHolderType) {	// function field
				StaticFieldHandle handle = ((FuncHolderType)type).getFieldHandle();
				this.handle = handle;
				type = handle.getFieldType();
			}
			this.setType(type);
		}

		public StaticFieldHandle getHandle() {
			return this.handle;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents getting array element.
	 * recvNode[indexNode]
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class IndexNode extends AssignableNode {
		private final ExprNode recvNode;
		private final ExprNode indexNode;

		/**
		 * handle of get method.
		 */
		private MethodHandle getterHandle;

		/**
		 * handle of set method.
		 */
		private MethodHandle setterHandle;

		public IndexNode(Token token, ExprNode recvNode, ExprNode indexNode) {
			super(token);
			this.recvNode = this.setExprNodeAsChild(recvNode);
			this.indexNode = this.setExprNodeAsChild(indexNode);
		}

		public ExprNode getRecvNode() {
			return this.recvNode;
		}

		public ExprNode getIndexNode() {
			return this.indexNode;
		}

		public void setGetterHandle(MethodHandle getterHandle) {
			this.getterHandle = getterHandle;
		}

		public MethodHandle getGetterHandle() {
			return this.getterHandle;
		}

		public void setSetterHandle(MethodHandle setterHandle) {
			this.setterHandle = setterHandle;
		}

		public MethodHandle getSetterHandle() {
			return this.setterHandle;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents getting class field.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class AccessNode extends AssignableNode {
		private final ExprNode recvNode;
		private final String fieldName;
		private FieldHandle handle;

		public AccessNode(ExprNode recvNode, Token token) {
			super(token);
			this.recvNode = this.setExprNodeAsChild(recvNode);
			this.fieldName = this.token.getText();
		}

		public ExprNode getRecvNode() {
			return this.recvNode;
		}

		public String getFieldName() {
			return this.fieldName;
		}

		public void setHandle(FieldHandle handle) {
			this.handle = handle;
			this.isReadOnly = handle.isReadOnlyField();
		}

		public FieldHandle getHandle() {
			return this.handle;
		}
		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents type cast and primitive type boxing.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class CastNode extends ExprNode {
		/**
		 * cast op definition.
		 */
		public final static int NOP         = 0;
		public final static int BOX         = 1;
		public final static int INT_2_FLOAT = 2;
		public final static int FLOAT_2_INT = 3;
		public final static int TO_STRING   = 4;
		public final static int CHECK_CAST  = 5;

		private final TypeToken targetTypeSymbol;
		private String targetTypeName;	// used for string cast
		private final ExprNode exprNode;
		private int castOp = NOP;

		/**
		 * 
		 * @param exprNode
		 * @param token
		 * may be null
		 * @param targetTypeSymbol
		 * may be null
		 */
		public CastNode(ExprNode exprNode, Token token, TypeToken targetTypeSymbol) {
			super(token);
			this.targetTypeSymbol = targetTypeSymbol;
			this.exprNode = this.setExprNodeAsChild(exprNode);
		}

		private CastNode(ExprNode exprNode) {
			this(exprNode, null, null);
		}

		public DSType resolveTargetType(TypePool pool) {
			if(this.targetTypeSymbol != null) {
				return this.targetTypeSymbol.toType(pool);
			}
			return pool.parseTypeName(this.targetTypeName);
		}

		public ExprNode getExprNode() {
			return this.exprNode;
		}

		public void resolveCastOp(int castOp) {
			this.castOp = castOp;
		}

		public int getCastOp() {
			return this.castOp;
		}

		/**
		 * used for checkType
		 * @param exprNode
		 * @return
		 * typed cast node
		 */
		public static CastNode wrapPrimitive(ExprNode exprNode) {
			assert exprNode.getType() instanceof PrimitiveType;
			Node parentNode = exprNode.getParentNode();
			CastNode castNode = new CastNode(exprNode);
			castNode.setParentNode(parentNode);
			castNode.resolveCastOp(BOX);
			castNode.setType(exprNode.getType());
			return castNode;
		}

		/**
		 * used for checkType
		 * @param pool
		 * @param exprNode
		 * typed cast node
		 * @return
		 */
		public static CastNode intToFloat(TypePool pool, ExprNode exprNode) {
			assert exprNode.getType() instanceof PrimitiveType;
			Node parentNode = exprNode.getParentNode();
			CastNode castNode = new CastNode(exprNode);
			castNode.setParentNode(parentNode);
			castNode.resolveCastOp(INT_2_FLOAT);
			castNode.setType(pool.floatType);
			return castNode;
		}

		/**
		 * used for string interpolation
		 * must require type check
		 * @param exprNode
		 * @return
		 * untyped cast node
		 */
		public static CastNode toString(ExprNode exprNode) {
			CastNode castNode = new CastNode(exprNode);
			castNode.targetTypeName = "String";
			return castNode;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * Represents instanceof operator.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class InstanceofNode extends ExprNode {
		public static final int ALWAYS_FALSE = 0;
		public static final int COMP_TYPE    = 1;
		public static final int INSTANCEOF   = 2;

		private final ExprNode exprNode;
		private final TypeToken typeSymbol;
		private DSType targetType;
		private int opType;

		public InstanceofNode(ExprNode exprNode, Token token, TypeToken targetTypeSymbol) {
			super(token);
			this.exprNode = this.setExprNodeAsChild(exprNode);
			this.typeSymbol = targetTypeSymbol;
		}

		public ExprNode getExprNode() {
			return this.exprNode;
		}

		public void setTargetType(TypePool pool) {
			this.targetType = this.typeSymbol.toType(pool);
		}

		public DSType getTargetType() {
			return this.targetType;
		}

		public void resolveOpType(int opType) {
			this.opType = opType;
		}

		public int getOpType() {
			return this.opType;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * Represnts operator( binary op, unary op) call.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class OperatorCallNode extends ExprNode {
		private final String funcName;
		private final List<ExprNode> argNodeList;
		private OperatorHandle handle;

		/**
		 * For unary op
		 * @param token
		 * - operator token
		 * @param node
		 * - operand
		 */
		public OperatorCallNode(Token token, ExprNode node) {
			super(token);
			this.funcName = this.token.getText();
			this.argNodeList = new ArrayList<>(1);
			this.argNodeList.add(this.setExprNodeAsChild(node));
		}

		/**
		 * For binary op
		 * @param token
		 * - operator token
		 * @param leftNode
		 * - binary left
		 * @param rightNode
		 * - binary right
		 */
		public OperatorCallNode(ExprNode leftNode, Token token, ExprNode rightNode) {
			super(token);
			this.funcName = this.token.getText();
			this.argNodeList = new ArrayList<>(2);
			this.argNodeList.add(this.setExprNodeAsChild(leftNode));
			this.argNodeList.add(this.setExprNodeAsChild(rightNode));
		}

		/**
		 * for StringExprNode
		 * @param opName
		 * @param node
		 */
		public OperatorCallNode(String opName, ExprNode node) {
			super(null);
			this.funcName = opName;
			this.argNodeList = new ArrayList<>(1);
			this.argNodeList.add(this.setExprNodeAsChild(node));
		}

		public String getFuncName() {
			return this.funcName;
		}

		public List<ExprNode> getNodeList() {
			return this.argNodeList;
		}

		public void setHandle(OperatorHandle handle) {
			this.handle = handle;
		}

		public OperatorHandle getHandle() {
			return this.handle;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	public static class ApplyNode extends ExprNode {
		protected final ExprNode recvNode;
		protected final List<ExprNode> argList;

		/**
		 * if true, treat this as function call.
		 */
		protected boolean isFuncCall;
		protected MethodHandle handle;

		protected ApplyNode(ExprNode recvNode, Arguments args) {
			super(recvNode.getToken());
			this.recvNode = this.setExprNodeAsChild(recvNode);
			this.argList = new ArrayList<>();
			for(ExprNode argNode : args.nodeList) {
				this.argList.add(this.setExprNodeAsChild(argNode));
			}
			this.isFuncCall = false;
		}

		public ExprNode getRecvNode() {
			return this.recvNode;
		}

		public List<ExprNode> getArgList() {
			return this.argList;
		}

		public void setAsFuncCallNode() {
			this.isFuncCall = true;
		}

		public boolean isFuncCall() {
			return this.isFuncCall;
		}

		public void setHandle(MethodHandle handle) {
			this.handle = handle;
		}

		public MethodHandle getHandle() {
			return this.handle;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}
	/**
	 * This node represents class constructor call.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class ConstructorCallNode extends ExprNode {
		private final TypeToken typeSymbol;
		private final List<ExprNode> argNodeList;
		private ConstructorHandle handle;

		public ConstructorCallNode(Token token, TypeToken typeSymbol, Arguments args) {
			super(token);
			this.typeSymbol = typeSymbol;
			this.argNodeList = new ArrayList<>();
			for(ExprNode node : args.nodeList) {
				argNodeList.add(this.setExprNodeAsChild(node));
			}
		}

		public TypeToken getTypeSymbol() {
			return this.typeSymbol;
		}

		public List<ExprNode> getNodeList() {
			return this.argNodeList;
		}

		public void setHandle(ConstructorHandle handle) {
			this.handle = handle;
		}

		public ConstructorHandle getHandle() {
			return this.handle;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents conditional 'and' or 'or' operator.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class CondOpNode extends ExprNode {
		private final String condOp;
		private final ExprNode leftNode;
		private final ExprNode rightNode;

		public CondOpNode(ExprNode leftNode, Token token, ExprNode rightNode) {
			super(token);
			this.condOp = this.token.getText();
			this.leftNode = this.setExprNodeAsChild(leftNode);
			this.rightNode = this.setExprNodeAsChild(rightNode);
		}

		public String getConditionalOp() {
			return this.condOp;
		}

		public ExprNode getLeftNode() {
			return this.leftNode;
		}

		public ExprNode getRightNode() {
			return this.rightNode;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * represent for command expression
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class ProcessNode extends ExprNode {
		private final String commandPath;
		private final List<ArgumentNode> argNodeList;
		private final List<GenericPair<Integer, ExprNode>> redirOptionList;

		/**
		 * if true, enable system call trace
		 */
		private final boolean trace;

		protected ProcessNode(Token token, boolean trace) {
			super(token);
			this.argNodeList = new ArrayList<>();
			this.commandPath = Utils.resolveHome(ArgumentNode.unescapeCommandString(token));
			this.redirOptionList = new ArrayList<>(5);
			this.trace = trace;
		}

		public void setArg(ArgumentNode argNode) {
			this.argNodeList.add((ArgumentNode) this.setExprNodeAsChild(argNode));
		}

		public String getCommandPath() {
			return this.commandPath;
		}

		public List<ArgumentNode> getArgNodeList() {
			return this.argNodeList;
		}

		public void addRedirOption(GenericPair<Integer, ExprNode> optionPair) {
			this.redirOptionList.add(optionPair);
		}

		public List<GenericPair<Integer, ExprNode>> getRedirOptionList() {
			return this.redirOptionList;
		}

		public boolean isTracable() {
			return this.trace;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * represent for command argument
	 * may be string type or string array type
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class ArgumentNode extends ExprNode {
		/**
		 * contains segment of argument.
		 */
		private final List<ExprNode> segmentNodeList;

		protected ArgumentNode(Token token) {
			super(token);
			this.segmentNodeList = new ArrayList<>();
		}

		protected ArgumentNode(ExprNode node) {
			this(node.getToken());
			this.addArgSegment(node);
		}

		public static String unescapeCommandString(Token token) {
			String tokenText = token.getText();
			StringBuilder sBuilder = new StringBuilder();
			final int size = tokenText.length();
			for(int i = 0; i < size; i++) {
				char ch = tokenText.charAt(i);
				if(ch == '\\') {
					char nextCh = tokenText.charAt(++i);
					if(nextCh == '\r' || nextCh == '\n') {
						continue;
					}
					ch = nextCh;
				}
				sBuilder.append(ch);
			}
			return sBuilder.toString();
		}

		/**
		 * create string value node for command argument
		 * @param segmentToken
		 * @return
		 */
		public static StringValueNode createStringValueNode(Token token) {
			return new StringValueNode(unescapeCommandString(token));
		}

		/**
		 * 
		 * @param segmentNode
		 * if ArgumentNode, merge to it.
		 */
		public void addArgSegment(ExprNode segmentNode) {
			if(segmentNode instanceof ArgumentNode) {
				List<ExprNode> nodes = ((ArgumentNode) segmentNode).getSegmentNodeList();
				for(ExprNode node : nodes) {
					this.segmentNodeList.add((ExprNode) this.setNodeAsChild(node));
				}
				return;
			}
			this.segmentNodeList.add((ExprNode) this.setNodeAsChild(segmentNode));
		}

		public List<ExprNode> getSegmentNodeList() {
			return this.segmentNodeList;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * represent for special parameters to which they shall expand
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class SpecialCharNode extends ExprNode {
		public final static int at = 1;	// $@

		private final int expandType;

		protected SpecialCharNode(Token token) {
			super(token);
			String tokenText = resolveName(token);
			switch(tokenText) {
			case "@":
				this.expandType = at;
				break;
			default:
				throw new RuntimeException("unsupported special parameter: " + tokenText);
			}
		}

		public int getExpandType() {
			return this.expandType;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * represent for (piped) command expression
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class TaskNode extends ExprNode {	//TODO: timeout, trace
		private final List<ProcessNode> procNodeList;
		private final boolean isBackGround;

		/**
		 * variable entry of output buffer.
		 * may be null.
		 */
		private GenericPair<String, DSType> bufferEntry;

		protected TaskNode(List<ProcessNode> procNodeList, boolean isBackGround) {
			super(procNodeList.get(0).getToken());
			this.procNodeList = procNodeList;
			this.isBackGround = isBackGround;
			for(ProcessNode procNode : this.procNodeList) {
				this.setExprNodeAsChild(procNode);
			}
		}

		public List<ProcessNode> getProcNodeList() {
			return this.procNodeList;
		}

		public boolean isBackGround() {
			return this.isBackGround;
		}

		public void setBufferEntry(GenericPair<String, DSType> entry) {
			this.bufferEntry = entry;
		}

		/**
		 * may be null
		 * @return
		 */
		public GenericPair<String, DSType> getBufferEntry() {
			return this.bufferEntry;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * represent for inner command expression (command substitution).
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class InnerTaskNode extends ExprNode {
		private static int nameSuffixCount = -1;
		/**
		 * task node or cond op node.
		 */
		private final ExprNode exprNode;

		/**
		 * contains variable entry of output buffer
		 */
		private GenericPair<String, DSType> bufferEntry;

		protected InnerTaskNode(ExprNode exprNode) {
			super(exprNode.getToken());
			assert (exprNode instanceof TaskNode) || (exprNode instanceof CondOpNode);
			this.exprNode = this.setExprNodeAsChild(exprNode);
		}

		public ExprNode getExprNode() {
			return this.exprNode;
		}

		/**
		 * get buffer name.
		 * @return
		 * - unique name
		 */
		public String getBufferName() {
			return "@outputBuffer" + ++nameSuffixCount;
		}

		public void setEntry(GenericPair<String, DSType> bufferEntry) {
			this.bufferEntry = bufferEntry;
		}

		public GenericPair<String, DSType> getEntry() {
			return this.bufferEntry;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	// #################
	// #   statement   #
	// #################
	/**
	 * This node represents assertion.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class AssertNode extends Node {
		public final static String opName = "assert";
		private final ExprNode exprNode;
		private OperatorHandle handle;

		public AssertNode(Token token, ExprNode exprNode) {
			super(token);
			this.exprNode = this.setExprNodeAsChild(exprNode);
		}

		public Node getExprNode() {
			return this.exprNode;
		}

		public void setHandle(OperatorHandle handle) {
			this.handle = handle;
		}

		public OperatorHandle getHandle() {
			return this.handle;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents block.
	 * It contains several statements.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class BlockNode extends Node {
		private final List<Node> nodeList;

		public BlockNode(Block block) {
			super(null);
			this.nodeList = block.getNodeList();
			for(Node node : nodeList) {
				this.setNodeAsChild(node);
			}
		}

		public List<Node> getNodeList() {
			return this.nodeList;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * represent end of block stattement (break, continue, return throw)
	 * @author skgchxngsxyz-opensuse
	 *
	 */
	public abstract static class BlockEndNode extends Node {
		protected BlockEndNode(Token token) {
			super(token);
		}
	}

	/**
	 * This node represents break statement.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class BreakNode extends BlockEndNode {
		public BreakNode(Token token) {
			super(token);
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents continue statement.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class ContinueNode extends BlockEndNode {
		public ContinueNode(Token token) {
			super(token);
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents exporting variable as environmental variable.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class ExportEnvNode extends Node {
		private final String envName;
		private final ExprNode exprNode;
		private OperatorHandle handle;

		public ExportEnvNode(Token token, Token nameToken, ExprNode exprNode) {
			super(token);
			this.envName = nameToken.getText();
			this.exprNode = this.setExprNodeAsChild(exprNode);
		}

		public String getEnvName() {
			return this.envName;
		}

		public ExprNode getExprNode() {
			return this.exprNode;
		}

		public void setHandle(OperatorHandle handle) {
			this.handle = handle;
		}

		public OperatorHandle getHandle() {
			return this.handle;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents importing environmental variable.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class ImportEnvNode extends Node {
		private final String envName;
		private OperatorHandle handle;

		public ImportEnvNode(Token token) {
			super(token);
			this.envName = this.token.getText();
		}

		public String getEnvName() {
			return this.envName;
		}

		public void setHandle(OperatorHandle handle) {
			this.handle = handle;
		}

		public OperatorHandle getHandle() {
			return this.handle;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * represent loop statement.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static abstract class LoopNode extends Node {
		protected LoopNode(Token token) {
			super(token);
		}
	}

	/**
	 * This node represents for statement.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class ForNode extends LoopNode {
		/**
		 * May be EmptyNode
		 */
		private final Node initNode;

		/**
		 * May be EmptyNode
		 */
		private final ExprNode condNode;

		/**
		 * May be EmptyNode
		 */
		private final Node iterNode;
		private final BlockNode blockNode;

		public ForNode(Token token, Node initNode, ExprNode condNode, Node iterNode, Node blockNode) {
			super(token);
			this.initNode = this.setNodeAsChild(initNode);
			this.condNode = this.setExprNodeAsChild(condNode);
			this.iterNode = this.setNodeAsChild(iterNode);
			this.blockNode = (BlockNode) this.setNodeAsChild(blockNode);
		}

		public Node getInitNode() {
			return this.initNode;
		}

		public ExprNode getCondNode() {
			return this.condNode;
		}

		public Node getIterNode() {
			return this.iterNode;
		}

		public BlockNode getBlockNode() {
			return this.blockNode;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents for in statement.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class ForInNode extends LoopNode {
		private final String initName;
		private final ExprNode exprNode;
		private final BlockNode blockNode;

		// for iterator op.
		private MethodHandle resetHandle;
		private MethodHandle nextHandle;
		private MethodHandle hasNextHandle;

		public ForInNode(Token token, Token nameToken, ExprNode exprNode, Node blockNode) {
			super(token);
			this.initName = resolveName(nameToken);
			this.exprNode = this.setExprNodeAsChild(exprNode);
			this.blockNode = (BlockNode) this.setNodeAsChild(blockNode);
		}

		public String getInitName() {
			return this.initName;
		}

		public ExprNode getExprNode() {
			return this.exprNode;
		}

		public BlockNode getBlockNode() {
			return this.blockNode;
		}

		public void setIteratorHandles(MethodHandle reset, MethodHandle next, MethodHandle hasNext) {
			this.resetHandle = reset;
			this.nextHandle = next;
			this.hasNextHandle = hasNext;
		}

		public MethodHandle getResetHandle() {
			return this.resetHandle;
		}

		public MethodHandle getNextHandle() {
			return this.nextHandle;
		}

		public MethodHandle getHasNextHandle() {
			return this.hasNextHandle;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents while statement.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class WhileNode extends LoopNode {
		private final ExprNode condNode;
		private final BlockNode blockNode;

		private final boolean asDoWhile;

		public WhileNode(Token token, ExprNode condNode, Node blockNode) {
			this(token, condNode, blockNode, false);
		}

		public WhileNode(Token token, ExprNode condNode, Node blockNode, boolean asDoWhile) {
			super(token);
			this.condNode = this.setExprNodeAsChild(condNode);
			this.blockNode = (BlockNode) this.setNodeAsChild(blockNode);
			this.asDoWhile = asDoWhile;
		}

		public ExprNode getCondNode() {
			return this.condNode;
		}

		public BlockNode getBlockNode() {
			return this.blockNode;
		}

		public boolean isAsDoWhile() {
			return this.asDoWhile;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents if statement.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class IfNode extends Node {
		private final ExprNode condNode;
		private final BlockNode thenBlockNode;

		/**
		 * may be EmptyblockNode
		 */
		private final BlockNode elseBlockNode;

		public IfNode(Token token, ExprNode condNode, IfElseBlock block) {
			super(token);
			this.condNode = this.setExprNodeAsChild(condNode);
			this.thenBlockNode = (BlockNode) this.setNodeAsChild(block.getThenBlockNode());
			this.elseBlockNode = (BlockNode) this.setNodeAsChild(block.getElseBlockNode());
		}

		public ExprNode getCondNode() {
			return this.condNode;
		}

		public BlockNode getThenBlockNode() {
			return this.thenBlockNode;
		}

		public BlockNode getElseBlockNode() {
			return this.elseBlockNode;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents return statement.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class ReturnNode extends BlockEndNode {
		/**
		 * May be EmptyNode.
		 */
		private final ExprNode exprNode;

		public ReturnNode(Token token) {
			this(token, new EmptyNode());
			this.exprNode.setType(TypePool.voidType);
		}

		public ReturnNode(Token token, ExprNode exprNode) {
			super(token);
			this.exprNode = this.setExprNodeAsChild(exprNode);
		}

		public ExprNode getExprNode() {
			return this.exprNode;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents throw statement.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class ThrowNode extends BlockEndNode {
		private final ExprNode exprNode;

		public ThrowNode(Token token, ExprNode exprNode) {
			super(token);
			this.exprNode = this.setExprNodeAsChild(exprNode);
		}

		public ExprNode getExprNode() {
			return this.exprNode;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents try-catch statement.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class TryNode extends Node {
		private final BlockNode tryBlockNode;
		private final List<CatchNode> catchNodeList;

		/**
		 * May be EmptyNode.
		 */
		private final Node finallyNode;

		public TryNode(Token token, Node tryBlockNode, Node finallyNode) {
			super(token);
			this.tryBlockNode = (BlockNode) this.setNodeAsChild(tryBlockNode);
			this.catchNodeList = new ArrayList<>();
			this.finallyNode = this.setNodeAsChild(finallyNode);
		}

		public BlockNode getTryBlockNode() {
			return this.tryBlockNode;
		}

		/**
		 * Only called from dshellParser.
		 * Do not call it.
		 * @param node
		 */
		public void setCatchNode(CatchNode node) {
			this.catchNodeList.add((CatchNode) this.setNodeAsChild(node));
		}

		public List<CatchNode> getCatchNodeList() {
			return this.catchNodeList;
		}

		public Node getFinallyNode() {
			return this.finallyNode;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents catch statement.
	 * It always exists in TryNode.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class CatchNode extends Node {
		private ClassType exceptionType;
		private final String exceptionName;
		private final TypeToken exceptionTypeSymbol;
		private final BlockNode catchBlockNode;

		public CatchNode(Token token, String exceptionName, TypeToken typeSymbol, Node catchBlockNode) {
			super(token);
			this.exceptionName = exceptionName;
			this.exceptionTypeSymbol = typeSymbol;
			this.catchBlockNode = (BlockNode) this.setNodeAsChild(catchBlockNode);
		}

		/**
		 * 
		 * @return
		 * - return null, if has no type annotation.
		 */
		public TypeToken getTypeSymbol() {
			return this.exceptionTypeSymbol;
		}

		public void setExceptionType(ClassType exceptionType) {
			this.exceptionType = exceptionType;
		}

		public ClassType getExceptionType() {
			return this.exceptionType;
		}

		public String getExceptionVarName() {
			return this.exceptionName;
		}

		public BlockNode getCatchBlockNode() {
			return this.catchBlockNode;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	public static class FinallyNode extends Node {
		private final BlockNode blockNode;

		public FinallyNode(Token token, Node blockNode) {
			super(token);
			this.blockNode = (BlockNode) this.setNodeAsChild(blockNode);
		}

		public BlockNode getBlockNode() {
			return this.blockNode;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}
	/**
	 * This node represents variable declaration.
	 * It requires initial value.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class VarDeclNode extends Node {
		private final boolean isReadOnly;
		private boolean isGlobal;
		private final String varName;
		private final ExprNode initValueNode;

		public VarDeclNode(Token token, Token nameToken, ExprNode initValueNode) {
			super(token);
			this.varName = nameToken.getText();
			this.initValueNode = this.setExprNodeAsChild(initValueNode);
			this.isReadOnly = !token.getText().equals("var");
			this.isGlobal = false;
		}

		public String getVarName() {
			return this.varName;
		}

		public ExprNode getInitValueNode() {
			return this.initValueNode;
		}

		public void setGlobal(boolean isGlobal) {
			this.isGlobal = isGlobal;
		}

		public boolean isGlobal() {
			return this.isGlobal;
		}

		public boolean isReadOnly() {
			return this.isReadOnly;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents assign operation such as '=', '+=', '-=', '*=', '/=', '%=', '++', '--'.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class AssignNode extends Node {
		private final String assignOp;

		/**
		 * if assingOp is '=', it is null.
		 */
		private MethodHandle handle;

		/**
		 * requires SymbolNode, ElementGetterNode or FieldGetterNode.
		 */
		private final ExprNode leftNode;

		/**
		 * may be replaced due to type cast.
		 */
		private ExprNode rightNode;

		public AssignNode(ExprNode leftNode, Token token, ExprNode rightNode) {
			super(token);
			this.assignOp = this.token.getText();
			this.leftNode = this.setExprNodeAsChild(leftNode);
			this.rightNode = this.setExprNodeAsChild(rightNode);
		}

		/**
		 * represent suffix increment, '++', '--'
		 * @param token
		 * @param leftNode
		 */
		public AssignNode(ExprNode leftNode, Token token) {
			this(leftNode, token, new IntValueNode(1));
		}

		public String getAssignOp() {
			return this.assignOp;
		}

		public ExprNode getLeftNode() {
			return this.leftNode;
		}

		public ExprNode getRightNode() {
			return this.rightNode;
		}

		public void setRightNode(ExprNode rightNode) {
			this.rightNode = rightNode;
		}

		public void setHandle(MethodHandle handle) {
			this.handle = handle;
		}

		/**
		 * 
		 * @return
		 * return null, if operator is '='.
		 */
		public MethodHandle getHandle() {
			return this.handle;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents function.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class FunctionNode extends Node {
		private final String funcName;
		private final List<TypeToken> paramTypeSymbolList;
		private final List<SymbolNode> nodeList;
		private final BlockNode blockNode;
		private final TypeToken returnTypeSymbol;
		private FuncHolderType holderType;

		public FunctionNode(Token token, Token nameToken, TypeToken returnTypeSymbol, ArgsDecl decls, Node blockNode) {
			super(token);
			this.funcName = nameToken.getText();
			this.returnTypeSymbol = returnTypeSymbol;
			this.blockNode = (BlockNode) this.setNodeAsChild(blockNode);
			this.paramTypeSymbolList = new ArrayList<>();
			this.nodeList = new ArrayList<>();
			for(ArgDecl decl : decls.getDeclList()) {
				this.paramTypeSymbolList.add(decl.getTypeSymbol());
				this.nodeList.add((SymbolNode) this.setNodeAsChild(decl.getArgNode()));
			}
		}

		public String getFuncName() {
			return this.funcName;
		}

		public List<SymbolNode> getArgDeclNodeList() {
			return this.nodeList;
		}

		public List<TypeToken> getParamTypeSymbolList() {
			return this.paramTypeSymbolList;
		}

		public BlockNode getBlockNode() {
			return this.blockNode;
		}

		public TypeToken getReturnTypeSymbol() {
			return this.returnTypeSymbol;
		}

		public void setHolderType(FuncHolderType holderType) {
			this.holderType = holderType;
		}

		public FuncHolderType getHolderType() {
			return this.holderType;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * This node represents class.
	 * It contains class field, instance method, constructor.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class ClassNode extends Node {
		private final String className;
		/**
		 * may be null, if not super class.
		 */
		private final String superName;
		private ClassType classType;
		private final List<Node> classElementList;

		/**
		 * 
		 * @param token
		 * @param nameToken
		 * - class name token
		 * @param superName
		 * - super class name (may be null if had no super class).
		 * @param elementList
		 * - class elements
		 */
		public ClassNode(Token token, Token nameToken, String superName, List<Node> elementList) {
			super(token);
			this.className = nameToken.getText();
			this.superName = superName;
			this.classElementList = new ArrayList<>();
			for(Node node : elementList) {
				this.classElementList.add(this.setNodeAsChild(node));
			}
		}

		public String getClassName() {
			return this.className;
		}

		/**
		 * 
		 * @return
		 * - return null. if has no super class.
		 */
		public String getSuperName() {
			return this.superName;
		}

		public void setClassType(ClassType type) {
			this.classType = type;
		}

		public ClassType getClassType() {
			return this.classType;
		}

		public List<Node> getElementList() {
			return this.classElementList;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * Represents class constructor.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class ConstructorNode extends Node {
		private DSType recvType;
		private final List<TypeToken> typeSymbolList;
		private final List<SymbolNode> nodeList;
		private final BlockNode blockNode;

		public ConstructorNode(Token token, ArgsDecl decls, Node blockNode) {
			super(token);
			this.recvType = TypePool.unresolvedType;
			this.blockNode = (BlockNode) this.setNodeAsChild(blockNode);
			this.typeSymbolList = new ArrayList<>();
			this.nodeList = new ArrayList<>();
			for(ArgDecl decl : decls.getDeclList()) {
				this.typeSymbolList.add(decl.getTypeSymbol());
				this.nodeList.add((SymbolNode) this.setNodeAsChild(decl.getArgNode()));
			}
		}

		public void setRecvType(DSType type) {
			this.recvType = type;
		}

		public DSType getRecvType() {
			return this.recvType;
		}

		public List<SymbolNode> getArgDeclNodeList() {
			return this.nodeList;
		}

		public BlockNode getBlockNode() {
			return this.blockNode;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * used for the initialization of builtin global variable.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class GlobalVarNode extends Node {
		private final String varName;
		private final String typeName;
		private final Object value;
		private DSType valueType;

		public GlobalVarNode(String varName, String typeName, Object value) {
			super(null);
			this.varName = varName;
			this.typeName = typeName;
			this.value = value;
		}

		public void resolveValueType(TypePool pool) {
			this.valueType = pool.parseTypeName(this.typeName);
		}

		public String getVarName() {
			return this.varName;
		}

		public Object getValue() {
			return this.value;
		}

		public DSType getValueType() {
			return this.valueType;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * Represents empty expression. used for for statement and return statement.
	 * it is always void type
	 * It is ignored in code generation.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class EmptyNode extends ExprNode {
		public EmptyNode() {
			super(null);
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * Represent empty block.
	 * It id ignored in type checking or code generation.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class EmptyBlockNode extends BlockNode {
		public EmptyBlockNode() {
			super(new Block());
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) {
			return visitor.visit(this);
		}
	}

	/**
	 * Represent root node.
	 * @author skgchxngsxyz-osx
	 *
	 */
	public static class RootNode extends Node {
		public final static String opName = "printValue";
		private final List<Node> nodeList;

		/**
		 * for top level class generation. must be fully qualified class name
		 */
		private String intrenalToplevelClassName;

		/**
		 * used for interactive mode.
		 */
		private OperatorHandle handle;

		private Set<FunctionType> genTargetFuncTypeSet;

		public RootNode(Token token) {
			super(token);
			this.nodeList = new ArrayList<>();
		}

		public void addNode(Node node) {
			this.nodeList.add(this.setNodeAsChild(node));
		}

		public List<Node> getNodeList() {
			return this.nodeList;
		}

		public void setHandle(OperatorHandle handle) {
			this.handle = handle;
		}

		public OperatorHandle getHandle() {
			return this.handle;
		}

		/**
		 * 
		 * @param name
		 * fully qualidied class name
		 */
		public void setToplevelName(String name) {
			this.intrenalToplevelClassName = name;
		}

		/**
		 * get internal name of toplevel class
		 * @return
		 */
		public String getToplevelName() {
			return this.intrenalToplevelClassName;
		}

		/**
		 * for func type generation
		 * @param genTargetFuncTypeSet
		 * may be null
		 */
		public void setGenTargetFuncTypeSet(Set<FunctionType> genTargetFuncTypeSet) {
			this.genTargetFuncTypeSet = genTargetFuncTypeSet;
		}

		/**
		 * 
		 * @return
		 * may be null
		 */
		public Set<FunctionType> getGenTargetFuncTypeSet() {
			return this.genTargetFuncTypeSet;
		}

		@Override
		public <T> T accept(NodeVisitor<T> visitor) { // do not call it
			throw new RuntimeException("RootNode do not support NodeVisitor");
		}
	}

	// helper utilities
	public static String resolveName(Token token) {
		String name = token.getText();
		if(name.startsWith("${")) {
			return name.substring(2, name.length() - 1);
		}
		return name.startsWith("$") ? name.substring(1) : name;
	}
}
