package dshell.internal.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Pair;

import dshell.internal.parser.Node.BlockNode;
import dshell.internal.parser.Node.ExprNode;
import dshell.internal.parser.Node.IfNode;
import dshell.internal.parser.Node.SymbolNode;
import dshell.internal.parser.TypeSymbol.VoidTypeSymbol;
import dshell.internal.parser.error.ParserErrorListener;
import dshell.lang.GenericPair;

/**
 * helper utilities for node generation.
 * @author skgchxngsxyz-opensuse
 *
 */
public class ParserUtils {
	public static class MapEntry {
		public final ExprNode keyNode;
		public final ExprNode valueNode;

		public MapEntry(ExprNode keyNode, ExprNode valueNode) {
			this.keyNode = keyNode;
			this.valueNode = valueNode;
		}
	}

	public static class Arguments {
		public final ArrayList<ExprNode> nodeList;

		public Arguments() {
			this.nodeList = new ArrayList<>();
		}

		public void addNode(Node node) {
			this.nodeList.add((ExprNode) node);
		}
	}

	public static class Block {
		private final ArrayList<Node> nodeList;

		public Block() {
			this.nodeList = new ArrayList<>();
		}

		public ArrayList<Node> getNodeList() {
			return this.nodeList;
		}

		public void addNode(Node node) {
			this.nodeList.add(node);
		}
	}

	public static class IfElseBlock {
		private BlockNode thenBlockNode;
		private BlockNode elseBlockNode;

		public IfElseBlock(Node node) {
			this.thenBlockNode = (BlockNode) node;
			this.elseBlockNode = Node.EmptyBlockNode.INSTANCE;
		}

		public void setElseBlockNode(Node node) {
			if(node instanceof IfNode) {
				Block block = new Block();
				block.addNode(node);
				this.elseBlockNode = new BlockNode(block);
				return;
			}
			this.elseBlockNode = (BlockNode) node;
		}

		public BlockNode getThenBlockNode() {
			return this.thenBlockNode;
		}

		public BlockNode getElseBlockNode() {
			return this.elseBlockNode;
		}
	}

	public static class CatchedException {
		private TypeSymbol typeSymbol;
		private final String name;

		public CatchedException(Token token) {
			this.name = token.getText();
			this.typeSymbol = null;
		}

		public void setTypeSymbol(TypeSymbol typeSymbol) {
			this.typeSymbol = typeSymbol;
		}

		public String getName() {
			return this.name;
		}

		/**
		 * 
		 * @return
		 * - return null, if has no type annotation
		 */
		public TypeSymbol getTypeSymbol() {
			return this.typeSymbol;
		}
	}

	public static class ArgsDecl {
		private final List<ArgDecl> declList;

		public ArgsDecl() {
			this.declList = new ArrayList<>();
		}

		public List<ArgDecl> getDeclList() {
			return this.declList;
		}

		public void addArgDecl(ArgDecl decl) {
			this.declList.add(decl);
		}
	}

	public static class ArgDecl {
		private final SymbolNode argDeclNode;
		private final TypeSymbol typeSymbol;

		public ArgDecl(Token token, TypeSymbol typeSymbol) {
			this.argDeclNode = new SymbolNode(token);
			this.typeSymbol = typeSymbol;
		}

		public SymbolNode getArgNode() {
			return this.argDeclNode;
		}

		public TypeSymbol getTypeSymbol() {
			return this.typeSymbol;
		}
	}

	public static class ClassBody {
		private final List<Node> nodeList;

		public ClassBody() {
			this.nodeList = new ArrayList<>();
		}

		public void addNode(Node node) {
			this.nodeList.add(node);
		}

		public List<Node> getNodeList() {
			return this.nodeList;
		}
	}

	public static class ParamTypeResolver {
		private final List<TypeSymbol> symbolList;

		public ParamTypeResolver() {
			this.symbolList = new ArrayList<>();
		}

		public void addTypeSymbol(TypeSymbol typeSymbol) {
			if(!(typeSymbol instanceof VoidTypeSymbol)) {
				this.symbolList.add(typeSymbol);
			}
		}

		public TypeSymbol[] getTypeSymbols() {
			int size = this.symbolList.size();
			TypeSymbol[] symbols = new TypeSymbol[size];
			for(int i = 0; i < size; i++) {
				symbols[i] = this.symbolList.get(i);
			}
			return symbols;
		}
	}

	public static class JoinedToken extends CommonToken {
		private static final long serialVersionUID = 1L;

		public JoinedToken(Token startToken, Token stopToken) {
			super(new Pair<>(startToken.getTokenSource(), startToken.getInputStream()), 
					0, 
					startToken.getChannel(), 
					startToken.getStartIndex(), 
					stopToken.getStopIndex());
		}
	}

	public static class RedirOption extends GenericPair<Integer, ExprNode> {
		// definition of redirect option.
		public final static int FromFile        = 0; // <
		public final static int To1File         = 1; // 1> >
		public final static int To1FileAppend   = 2; // 1>> >>
		public final static int To2File         = 3; // 2>
		public final static int To2FileAppend   = 4; // 2>>
		public final static int Merge2To1       = 5; // 2>&1
		public final static int ToFileAnd       = 6; // >& &>
		public final static int AndToFileAppend = 7; // &>>

		public final static Map<String, Integer> optionMap = new HashMap<>();

		static {
			optionMap.put("<",    FromFile);
			optionMap.put("1>",   To1File);
			optionMap.put(">",    To1File);
			optionMap.put("1>>",  To1FileAppend);
			optionMap.put(">>",   To1FileAppend);
			optionMap.put("2>",   To2File);
			optionMap.put("2>>",  To2FileAppend);
			optionMap.put("2>&1", Merge2To1);
			optionMap.put(">&",   ToFileAnd);
			optionMap.put("&>",   ToFileAnd);
			optionMap.put("&>>",  AndToFileAppend);
		}

		public RedirOption(Token startToken, Token stopToken) {
			this(startToken, stopToken, new Node.StringValueNode(""));
		}

		public RedirOption(Token startToken, Token stopToken, ExprNode targetNode) {
			this(new JoinedToken(startToken, stopToken), targetNode);
		}

		public RedirOption(Token token, ExprNode targetNode) {
			super(optionMap.get(token.getText()), targetNode);
		}
	}

	public static ExprNode parseBackquotedLiteral(Token token, dshellParser parser) {
		// init child parser
		dshellLexer childLexer = new dshellLexer(null);
		childLexer.removeErrorListeners();
		childLexer.addErrorListener(ParserErrorListener.getInstance());
		dshellParser childParser = new dshellParser(null);
		childParser.removeErrorListeners();
		childParser.addErrorListener(ParserErrorListener.getInstance());
		childParser.setCmdScope(parser.getCmdScope());

		// init intput
		StringBuilder sBuilder = new StringBuilder();
		String tokenText = token.getText();
		final int size = tokenText.length();
		for(int i = 1; i < size - 1; i++) {
			char ch = tokenText.charAt(i);
			switch(ch) {
			case '\\': {
				char nextCh = tokenText.charAt(i + 1);
				if(nextCh == '`') {
					ch = nextCh;
					i++;
				}
				break;
			}
			}
			sBuilder.append(ch);
		}
		ANTLRInputStream input = new ANTLRInputStream(sBuilder.toString());
		input.name = token.getInputStream().getSourceName();

		// start parsing
		childLexer.setLine(token.getLine());
		childLexer.setInputStream(input);
		CommonTokenStream tokenStream = new CommonTokenStream(childLexer);
		childParser.setTokenStream(tokenStream);
		return new Node.QuotedTaskNode(childParser.commandListExpression().node);
	}
}
