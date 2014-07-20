parser grammar dshellParser;

options { tokenVocab=dshellLexer; }


@header {
package dshell.internal.parser;
import dshell.internal.parser.Node;
import dshell.internal.parser.ParserUtils;
import dshell.internal.parser.TypeSymbol;
import java.util.ArrayList;
}

@members {
// parser entry point.
public ToplevelContext startParser() {
	cmdScope.popAllScope();
	return this.toplevel();
}

private boolean isLineEnd() {
	int lineEndIndex = this.getCurrentToken().getTokenIndex() - 1;
	Token lineEndToken = _input.get(lineEndIndex);
	if(lineEndToken.getChannel() != Lexer.HIDDEN) {
		return false;
	}
	int type = lineEndToken.getType();
	return type == LineEnd;
}

private boolean here(final int type) {
	int index = this.getCurrentToken().getTokenIndex() - 1;
	Token ahead = _input.get(index);
	return (ahead.getChannel() == Lexer.HIDDEN) && (ahead.getType() == type);
}

private final CommandScope cmdScope = new CommandScope();

public CommandScope getCmdScope() {
	return this.cmdScope;
}

// ';' | '|' | '&' | '>' | '<' | '(' | ')' | '{' | '}' | WhiteSpace | LineEnd | Comment)+
private boolean matchCmdEnd(Token token) {
	switch(token.getType()) {
	case Semicolon:
	case OR:
	case AND:
	case GT:
	case LT:
	case LeftParenthese:
	case RightParenthese:
	case LeftBrace:
	case RightBrace:
	case WhiteSpace:
	case LineEnd:
	case Comment:
	case EOF:
		return true;
	}
	return false;
}

private boolean isCommand() {
	int i = 1;
	Token curToken = _input.LT(i);
	ArrayList<Token> tokenList = new ArrayList<Token>();
	while(!this.matchCmdEnd(curToken)) {
		tokenList.add(curToken);
		curToken = _input.LT(++i);
	}
	int size = tokenList.size();
	if(size == 0) {
		return false;
	}
	Token token;
	if(size == 1) {
		token = tokenList.get(0);
		int type = token.getType();
		switch(token.getType()) {
		// reserved key word
		case Assert     :
		case Break      :
		case Boolean    :
		case Catch      :
		case Continue   :
		case Class      :
		case Constructor:
		case Do         :
		case Else       :
		case Extends    :
		case ExportEnv  :
		case Func       :
		case Function   :
		case Finally    :
		case Float      :
		case For        :
		case If         :
		case ImportCmd  :
		case ImportEnv  :
		case In         :
		case Int        :
		case Instanceof :
		case Let        :
		case New        :
		case Return     :
		case Super      :
		case Try        :
		case Throw      :
		case Var        :
		case Void       :
		case While      :
		// literal
		case IntLiteral :
		case FloatLiteral:
		case StringLiteral:
		case BooleanLiteral:
			return false;
		}
	} else {
		token = new ParserUtils.JoinedToken(tokenList.get(0), tokenList.get(size - 1));
	}
	return this.cmdScope.isCommand(token.getText());
}
}

// ######################
// #        parse       #
// ######################

// separator definition
ws : (WhiteSpace | LineEnd)+ ;

lParenthese returns [Token token] : ws? t='('  ws? {$token = $t;};
rParenthese returns [Token token] : ws? t=')'  ws? {$token = $t;};
lBracket    returns [Token token] : ws? t='['  ws? {$token = $t;};
rBracket    returns [Token token] : ws? t=']'  ws? {$token = $t;};
lBrace      returns [Token token] : ws? t='{'  ws? {$token = $t;};
rBrace      returns [Token token] : ws? t='}'  ws? {$token = $t;};
lAngle      returns [Token token] : ws? t='<'  ws? {$token = $t;};
rAngle      returns [Token token] : ws? t='>'  ws? {$token = $t;};

colon       returns [Token token] : ws? t=':'  ws? {$token = $t;};
semicolon   returns [Token token] : ws? t=';'  ws? {$token = $t;};
comma       returns [Token token] : ws? t=','  ws? {$token = $t;};
period      returns [Token token] : ws? t='.'  ws? {$token = $t;};

assign      returns [Token token] : ws? t='='  ws? {$token = $t;};
assign_ops  returns [Token token] : ws? t=('=' | '+=' | '-=' | '*=' | '/=' | '%=') ws? {$token = $t;};

add_ops     returns [Token token] : ws? t=('+' | '-') ws? {$token = $t;};
mul_ops     returns [Token token] : ws? t=('*' | '/' | '%')  ws? {$token = $t;};
lt_ops      returns [Token token] : ws? t=('<' | '>' | '<=' | '>=')  ws? {$token = $t;};
eq_ops      returns [Token token] : ws? t=('==' | '!=' | '=~' | '!~') ws? {$token = $t;};
suffix_ops  returns [Token token] : t=('++' | '--') {$token = $t;};
prefix_ops  returns [Token token] : t=('+' | '-' | '~' | '!')  ws? {$token = $t;};

and         returns [Token token] : ws? t='&'  ws? {$token = $t;};
or          returns [Token token] : ws? t='|'  ws? {$token = $t;};
xor         returns [Token token] : ws? t='^'  ws? {$token = $t;};
condAnd     returns [Token token] : ws? t='&&' ws? {$token = $t;};
condOr      returns [Token token] : ws? t='||' ws? {$token = $t;};


// statement definition
toplevel returns [Node.RootNode node]
	: ws? (ws? a+=toplevelStatement)* ws? EOF
	 {
	 	$node = new Node.RootNode(_input.get(0));
	 	for(int i = 0; i < $a.size(); i++) {
	 		$node.addNode($a.get(i).node);
	 	}
	 }
	;

toplevelStatement returns [Node node]
	: functionDeclaration {$node = $functionDeclaration.node;}
	| classDeclaration {$node = $classDeclaration.node;}
	| statement {$node = $statement.node;}
	;

statementEnd
	: EOF
	| WhiteSpace? LineEnd ws?
	| WhiteSpace? ';' ws?
	;

functionDeclaration returns [Node node]
	: Function ws Identifier lParenthese argumentsDeclaration rParenthese returnType block
		{$node = new Node.FunctionNode($Function, $Identifier, $returnType.type, $argumentsDeclaration.decl, $block.node);}
	;

returnType returns [TypeSymbol type]
	: colon typeName { $type = $typeName.type;}
	| { $type = TypeSymbol.toVoid(); }
	;

argumentsDeclaration returns [ParserUtils.ArgsDecl decl]
	: a+=variableDeclarationWithType (comma a+=variableDeclarationWithType)*
		{
			$decl = new ParserUtils.ArgsDecl();
			for(int i = 0; i < $a.size(); i++) {
				$decl.addArgDecl($a.get(i).arg);
			}
		}
	| { $decl = new ParserUtils.ArgsDecl();}
	;

variableDeclarationWithType returns [ParserUtils.ArgDecl arg]
	: Identifier colon typeName {$arg = new ParserUtils.ArgDecl($Identifier, $typeName.type);}
	;

typeName returns [TypeSymbol type] locals [TypeSymbol[] types]
	: Int {$type = TypeSymbol.toPrimitive($Int);}
	| Float {$type = TypeSymbol.toPrimitive($Float);}
	| Boolean {$type = TypeSymbol.toPrimitive($Boolean);}
	| Void {$type = TypeSymbol.toVoid($Void);}
	| Identifier {$type = TypeSymbol.toClass($Identifier);}
	| Func lAngle aa=typeName paramTypes rAngle
		{$type = TypeSymbol.toFunc($Func, $aa.type, $paramTypes.types);}
	| Identifier lAngle a+=typeName (comma a+=typeName)* rAngle
		{
			$types = new TypeSymbol[$a.size()];
			for(int i = 0; i < $types.length; i++) {
				$types[i] = $a.get(i).type;
			}
			$type = TypeSymbol.toGeneric($Identifier, $types);
		}
	;

paramTypes returns [TypeSymbol[] types] locals [ParserUtils.ParamTypeResolver resolver]
	: comma lBracket a+=typeName (comma a+=typeName)* rBracket
		{
			$resolver = new ParserUtils.ParamTypeResolver();
			for(int i = 0; i < $a.size(); i++) {
				$resolver.addTypeSymbol($a.get(i).type);
			}
			$types = $resolver.getTypeSymbols();
		}
	| { $resolver = new ParserUtils.ParamTypeResolver(); $types = $resolver.getTypeSymbols();}
	;

block returns [Node node] locals [ParserUtils.Block blockModel]
	: {cmdScope.createNewScope();} lBrace b+=statement+ rBrace {cmdScope.removeCurrentScope();}
		{
			$blockModel = new ParserUtils.Block();
			for(int i = 0; i < $b.size(); i++) {
				$blockModel.addNode($b.get(i).node);
			}
			$node = new Node.BlockNode($blockModel);
		}
	;

classDeclaration returns [Node node] locals [String superName]
	: Class ws name=Identifier (Extends a+=Identifier)? classBody
		{
			$superName = null;
			if($a.size() == 1) {
				$superName = $a.get(0).getText();
			}
			$node = new Node.ClassNode($Class, $name, $superName, $classBody.body.getNodeList());
		}
	;

classBody returns [ParserUtils.ClassBody body]
	: lBrace (a+=classElement statementEnd)+ rBrace
		{
			$body = new ParserUtils.ClassBody();
			for(int i = 0; i < $a.size(); i++) {
				$body.addNode($a.get(i).node);
			}
		}
	;

classElement returns [Node node]
	: fieldDeclaration {$node = $fieldDeclaration.node;}
	| functionDeclaration {$node = $functionDeclaration.node;}
	| constructorDeclaration {$node = $constructorDeclaration.node;}
	;

fieldDeclaration returns [Node node]
	: variableDeclaration
	;

constructorDeclaration returns [Node node]
	: Constructor lParenthese argumentsDeclaration rParenthese block
		{$node = new Node.ConstructorNode($Constructor, $argumentsDeclaration.decl, $block.node);}
	;

statement returns [Node node]
	: assertStatement statementEnd {$node = $assertStatement.node;}
	| emptyStatement {$node = $emptyStatement.node;}
	| breakStatement statementEnd {$node = $breakStatement.node;}
	| continueStatement statementEnd {$node = $continueStatement.node;}
	| exportEnvStatement statementEnd {$node = $exportEnvStatement.node;}
	| forStatement {$node = $forStatement.node;}
	| foreachStatement {$node = $foreachStatement.node;}
	| ifStatement {$node = $ifStatement.node;}
	| importEnvStatement statementEnd {$node = $importEnvStatement.node;}
	| importCommandStatement importCommandEnd {$node = $importCommandStatement.node;}
	| returnStatement statementEnd {$node = $returnStatement.node;}
	| throwStatement statementEnd {$node = $throwStatement.node;}
	| whileStatement {$node = $whileStatement.node;}
	| doWhileStatement { $node = $doWhileStatement.node;}
	| tryCatchStatement {$node = $tryCatchStatement.node;}
	| variableDeclaration statementEnd {$node = $variableDeclaration.node;}
	| assignStatement statementEnd {$node = $assignStatement.node;}
	| suffixStatement statementEnd {$node = $suffixStatement.node;}
//	| commandExpression statementEnd {$node = $commandExpression.node;}
//	| expression statementEnd {$node = $expression.node;}
	| extendedExpression {$node = $extendedExpression.node;}
	;

assertStatement returns [Node node]
	: Assert lParenthese expression rParenthese 
		{$node = new Node.AssertNode($Assert, $expression.node);}
	;

breakStatement returns [Node node]
	: Break {$node = new Node.BreakNode($Break);}
	;

continueStatement returns [Node node]
	: Continue {$node = new Node.ContinueNode($Continue);}
	;

exportEnvStatement returns [Node node]
	: ExportEnv ws Identifier assign expression 
		{$node = new Node.ExportEnvNode($ExportEnv, $Identifier, $expression.node);}
	;

forStatement returns [Node node]
	: For lParenthese forInit semicolon forCond semicolon forIter rParenthese block 
		{$node = new Node.ForNode($For, $forInit.node, $forCond.node, $forIter.node, $block.node);}
	;

forInit returns [Node node]
	: variableDeclaration {$node = $variableDeclaration.node;}
	| expression {$node = $expression.node;}
	| assignStatement {$node = $assignStatement.node;}
	| {$node = new Node.EmptyNode();}
	;

forCond returns [Node.ExprNode node]
	: expression {$node = $expression.node;}
	| {$node = new Node.EmptyNode();}
	;

forIter returns [Node node]
	: expression {$node = $expression.node;}
	| assignStatement {$node = $assignStatement.node;}
	| suffixStatement {$node = $suffixStatement.node;}
	| {$node = new Node.EmptyNode();}
	;

foreachStatement returns [Node node]
	: For lParenthese Identifier ws? 'in' ws? expression rParenthese block 
		{$node = new Node.ForInNode($For, $Identifier, $expression.node, $block.node);}
	;

ifStatement returns [Node node] locals [ParserUtils.IfElseBlock ifElseBlock]
	: If lParenthese expression rParenthese b+=block (Else ws ei+=ifStatement | Else b+=block)?
		{
			$ifElseBlock = new ParserUtils.IfElseBlock($b.get(0).node);
			if($b.size() > 1) {
				$ifElseBlock.setElseBlockNode($b.get(1).node);
			}
			if($ei.size() > 0) {
				$ifElseBlock.setElseBlockNode($ei.get(0).node);
			}
			$node = new Node.IfNode($If, $expression.node, $ifElseBlock);
		}
	;

importEnvStatement returns [Node node]
	: ImportEnv ws Identifier {$node = new Node.ImportEnvNode($Identifier);}
	;

importCommandStatement returns [Node node]	//FIXME:
	: ImportCmd (WhiteSpace a+=commandName)+
		{
			$node = new Node.EmptyNode();
			for(int i = 0; i < $a.size(); i++) {
				cmdScope.setCommandPath($a.get(i).getText());
			}
		}
	;

importCommandEnd
	: EOF
	| ';'
//	| LineEndInCmd
	;

returnStatement returns [Node node] locals [ParserUtils.ReturnExpr returnExpr]
	: Return (ws e+=expression)?
		{
			$returnExpr = new ParserUtils.ReturnExpr();
			if($e.size() == 1) {
				$returnExpr.setNode($e.get(0).node);
			}
			$node = new Node.ReturnNode($Return, $returnExpr.getExprNode());
		}
	;

throwStatement returns [Node node]
	: Throw ws expression {$node = new Node.ThrowNode($Throw, $expression.node);}
	;

whileStatement returns [Node node]
	: While lParenthese expression rParenthese block {$node = new Node.WhileNode($While, $expression.node, $block.node);}
	;

doWhileStatement returns [Node node]
	: Do block While lParenthese expression rParenthese {$node = new Node.WhileNode($Do, $expression.node, $block.node, true);}
	;

tryCatchStatement returns [Node node] locals [Node.TryNode tryNode]
	: Try block c+=catchStatement+ finallyBlock
		{
			$tryNode = new Node.TryNode($Try, $block.node, $finallyBlock.node);
			for(int i = 0; i < $c.size(); i++) {
				$tryNode.setCatchNode($c.get(i).node);
			}
			$node = $tryNode;
		}
	;

finallyBlock returns [Node node]
	: Finally block {$node = $block.node;}
	| {$node = Node.EmptyBlockNode.INSTANCE;}
	;

catchStatement returns [Node.CatchNode node]
	: Catch lParenthese exceptDeclaration rParenthese block
		{
			$node = new Node.CatchNode($Catch, $exceptDeclaration.except.getName(), $exceptDeclaration.except.getTypeSymbol(), $block.node);
		}
	;

exceptDeclaration returns [ParserUtils.CatchedException except]
	: Identifier (colon t+=typeName)?
		{
			$except = new ParserUtils.CatchedException($Identifier);
			if($t.size() == 1) {
				$except.setTypeSymbol($t.get(0).type);
			}
		}
	;

variableDeclaration returns [Node node]
	: flag=(Let | Var) ws Identifier assign expression
		{
			$node = new Node.VarDeclNode($flag, $Identifier, $expression.node);
		}
	;

assignStatement returns [Node node]
	: left=expression assign_ops right=expression
		{
			$node = new Node.AssignNode($assign_ops.token, $left.node, $right.node);
		}
	;

emptyStatement returns [Node node]
	: semicolon {$node = new Node.EmptyNode();}
	;

suffixStatement returns [Node node]
	: expression suffix_ops {$node = new Node.AssignNode($expression.node, $suffix_ops.token);}
	;

// expression definition.
// command expression
extendedExpression returns [Node.ExprNode node]
	: commandExpression {$node = $commandExpression.node;}
	| expression {$node = $expression.node;}
	;

commandName returns [Token token]
	: a+=~(';' | '|' | '&' | '>' | '<' | '(' | ')' | '{' | '}' | WhiteSpace | LineEnd | Comment)+
		{
			if($a.size() == 1) {
				$token = $a.get(0);
			} else {
				$token = new ParserUtils.JoinedToken($a.get(0), $a.get($a.size() - 1));
			}
		}
	;

commandExpression returns [Node.ExprNode node]
	: singleCommandExpr {$node = $singleCommandExpr.node;}
	;

singleCommandExpr returns [Node.CommandNode node]
	: {isCommand()}? commandName (WhiteSpace a+=commandArg)*
		{
			$node = new Node.CommandNode($commandName.token, cmdScope.resolveCommandPath($commandName.token.getText()));
			for(int i = 0; i < $a.size(); i++) {
				$node.setArg($a.get(i).node);
			}
		}
	;

commandArg returns [Node.ExprNode node] locals [Token token]
	: commandName {$node = new Node.StringValueNode($commandName.token);}
	;

// normal expression
expression returns [Node.ExprNode node]
	: primaryExpression {$node = $primaryExpression.node;}
	| a=expression arguments {$node = new Node.ApplyNode($a.node, $arguments.args);}
	| r=expression lBracket i=expression rBracket {$node = new Node.ElementGetterNode($lBracket.token, $r.node, $i.node);}
	| a=expression period Identifier {$node = new Node.FieldGetterNode($a.node, $Identifier);}
	| New ws typeName arguments {$node = new Node.ConstructorCallNode($New, $typeName.type, $arguments.args);}
	| lParenthese typeName rParenthese right=expression {$node = new Node.CastNode($typeName.type, $right.node);}
	| prefix_ops right=expression {$node = new Node.OperatorCallNode($prefix_ops.token, $right.node);}
	| left=expression mul_ops right=expression {$node = new Node.OperatorCallNode($mul_ops.token, $left.node, $right.node);}
	| left=expression add_ops right=expression {$node = new Node.OperatorCallNode($add_ops.token, $left.node, $right.node);}
	| left=expression lt_ops right=expression {$node = new Node.OperatorCallNode($lt_ops.token, $left.node, $right.node);}
	| left=expression ws Instanceof ws typeName {$node = new Node.InstanceofNode($Instanceof, $left.node, $typeName.type);}
	| left=expression eq_ops right=expression {$node = new Node.OperatorCallNode($eq_ops.token, $left.node, $right.node);}
	| left=expression and right=expression {$node = new Node.OperatorCallNode($and.token, $left.node, $right.node);}
	| left=expression xor right=expression {$node = new Node.OperatorCallNode($xor.token, $left.node, $right.node);}
	| left=expression or right=expression {$node = new Node.OperatorCallNode($or.token, $left.node, $right.node);}
	| left=expression condAnd right=expression {$node = new Node.CondOpNode($condAnd.token, $left.node, $right.node);}
	| left=expression condOr right=expression {$node = new Node.CondOpNode($condOr.token, $left.node, $right.node);}
	;

primaryExpression returns [Node.ExprNode node]
	: literal {$node = $literal.node;}
	| symbol {$node = $symbol.node;}
	| lParenthese expression rParenthese {$node = $expression.node;}
	;

symbol returns [Node.ExprNode node]
	: Identifier {$node = new Node.SymbolNode($Identifier);}
	;

literal returns [Node.ExprNode node]
	: IntLiteral {$node = new Node.IntValueNode($IntLiteral);}
	| FloatLiteral {$node = new Node.FloatValueNode($FloatLiteral);}
	| BooleanLiteral {$node = new Node.BooleanValueNode($BooleanLiteral);}
	| StringLiteral {$node = new Node.StringValueNode($StringLiteral);}
	| arrayLiteral {$node = $arrayLiteral.node;}
	| mapLiteral {$node = $mapLiteral.node;}
	| pairLiteral {$node = $pairLiteral.node;}
	;

arrayLiteral returns [Node.ExprNode node] locals [Node.ArrayNode arrayNode]
	: lBracket expr+=expression (comma expr+=expression)* rBracket
		{	$arrayNode = new Node.ArrayNode($lBracket.token);
			for(int i = 0; i < $expr.size(); i++) {
				$arrayNode.addNode($expr.get(i).node);
			}
			$node = $arrayNode;
		}
	;

mapLiteral returns [Node.ExprNode node] locals [Node.MapNode mapNode]
	: lBrace entrys+=mapEntry (comma entrys+=mapEntry)* rBrace
		{
			$mapNode = new Node.MapNode($lBrace.token);
			for(int i = 0; i < $entrys.size(); i++) {
				$mapNode.addEntry($entrys.get(i).entry.keyNode, $entrys.get(i).entry.valueNode);
			}
			$node = $mapNode;
		}
	;

mapEntry returns [ParserUtils.MapEntry entry]
	: key=expression colon value=expression {$entry = new ParserUtils.MapEntry($key.node, $value.node);}
	;

pairLiteral returns [Node.ExprNode node]
	: lParenthese left=expression comma right=expression rParenthese
		{
			$node = new Node.PairNode($lParenthese.token, $left.node, $right.node);
		}
	;

arguments returns [ParserUtils.Arguments args]
	: lParenthese a+=argumentList? rParenthese
		{
			$args = new ParserUtils.Arguments();
			if($a.size() == 1) {
				$args = $a.get(0).args;
			}
		}
	;

argumentList returns [ParserUtils.Arguments args]
	: a+= expression (comma a+=expression)* 
		{
			$args = new ParserUtils.Arguments();
			for(int i = 0; i < $a.size(); i++) {
				$args.addNode($a.get(i).node);
			}
		}
	;

