parser grammar dshellParser;

options { tokenVocab=dshellLexer; }


@header {
package dshell.internal.parser;
import dshell.internal.parser.Node;
import dshell.internal.parser.ParserUtils;
import dshell.internal.parser.TypeToken;
}

@members {
// parser entry point.
public Node.RootNode parseToplevel() {
	ToplevelContext ctx = this.toplevel();
	if(this.inspect) {
		ctx.inspect(this);
	}
	return ctx.node;
}

// parser one statement
public Node.RootNode parseStatement() {
	ToplevelStatementContext ctx = this.toplevelStatement();
		if(this.inspect) {
		ctx.inspect(this);
	}
	Node.RootNode node = new Node.RootNode(ctx.node.getToken());
	node.addNode(ctx.node);
	return node;
}

private boolean inspect = false;
public void setInspect(boolean enableInspect) {
	this.inspect = enableInspect;
}

private boolean hasNewLine() {
	int prevTokenIndex = this.getCurrentToken().getTokenIndex() - 1;
	Token prevToken = this._input.get(prevTokenIndex);
	return (prevToken.getChannel() == Lexer.HIDDEN) && (prevToken.getType() == NewLine);
}
}

// ######################
// #        parse       #
// ######################

// separator definition
comma: {!hasNewLine()}? ',';

// statement definition
toplevel returns [Node.RootNode node]
	: (a+=toplevelStatement)* EOF
		{
			$node = new Node.RootNode(_input.get(0));
			for(int i = 0; i < $a.size(); i++) {
				$node.addNode($a.get(i).node);
			}
		}
	;

toplevelStatement returns [Node node]
	: functionDeclaration {$node = $functionDeclaration.node;}
//	| classDeclaration {$node = $classDeclaration.node;}	//TODO:
	| statement {$node = $statement.node;}
	;

statementEnd
	: EOF
	| LineEnd
	| NewLine
	| {hasNewLine()}?
	;

functionDeclaration returns [Node node]
	: Function VarName LeftParenthese argumentsDeclaration RightParenthese returnType block
		{$node = new Node.FunctionNode($Function, $VarName, $returnType.type, $argumentsDeclaration.decl, $block.node);}
	;

returnType returns [TypeToken type]
	: typeAnnoPrefix typeNameWithVoid {$type = $typeNameWithVoid.type;}
	| { $type = TypeToken.toVoid(); }
	;

typeAnnoPrefix: {!hasNewLine()}? ':' {!hasNewLine()}?;
typeSep       : {!hasNewLine()}? ',' {!hasNewLine()}?;

argumentsDeclaration returns [ParserUtils.ArgsDecl decl]
	: a+=argumentDeclarationWithType (comma a+=argumentDeclarationWithType)*
		{
			$decl = new ParserUtils.ArgsDecl();
			for(int i = 0; i < $a.size(); i++) {
				$decl.addArgDecl($a.get(i).arg);
			}
		}
	| { $decl = new ParserUtils.ArgsDecl();}
	;

argumentDeclarationWithType returns [ParserUtils.ArgDecl arg]
	: AppliedName typeAnnoPrefix typeName {$arg = new ParserUtils.ArgDecl($AppliedName, $typeName.type);}
	;

typeName returns [TypeToken type]
	: Int        {$type = TypeToken.toPrimitive($Int);}
	| Float      {$type = TypeToken.toPrimitive($Float);}
	| Boolean    {$type = TypeToken.toPrimitive($Boolean);}
	| Identifier {$type = TypeToken.toClass($Identifier);}
	| Func openType r=typeNameWithVoid {$type = new TypeToken.FuncTypeToken($Func, $r.type);}
		(typeSep openParamType a=typeName {((TypeToken.FuncTypeToken)$type).addParamTypeToken($a.type);}
			( typeSep b=typeName 
				{((TypeToken.FuncTypeToken)$type).addParamTypeToken($b.type);}
			)* closeParamType
		)? closeType
	| Identifier openType {$type = new TypeToken.GenericTypeToken($Identifier);} 
		a=typeName {((TypeToken.GenericTypeToken)$type).addElementTypeToken($a.type);}
			(comma b=typeName
				{((TypeToken.GenericTypeToken)$type).addElementTypeToken($b.type);}
			)* closeType
	;

typeNameWithVoid returns [TypeToken type]
	: typeName {$type = $typeName.type;}
	| Void {$type = TypeToken.toVoid($Void);}
	;

openType       : {!hasNewLine()}? LeftAngleBracket  {!hasNewLine()}?;
closeType      : {!hasNewLine()}? RightAngleBracket;
openParamType  :                  LeftBracket       {!hasNewLine()}?;
closeParamType : {!hasNewLine()}? RightBracket;

block returns [Node node] locals [ParserUtils.Block blockModel]
	:  LeftBrace b+=statement+ RightBrace 
		{
			$blockModel = new ParserUtils.Block();
			for(int i = 0; i < $b.size(); i++) {
				$blockModel.addNode($b.get(i).node);
			}
			$node = new Node.BlockNode($blockModel);
		}
	;

classDeclaration returns [Node node] locals [String superName]	//FIXME:
	: Class name=Identifier (Extends a+=Identifier)? classBody
		{
			$superName = null;
			if($a.size() == 1) {
				$superName = $a.get(0).getText();
			}
			$node = new Node.ClassNode($Class, $name, $superName, $classBody.body.getNodeList());
		}
	;

classBody returns [ParserUtils.ClassBody body]	//FIXME:
	:  LeftBrace (a+=classElement statementEnd)+ RightBrace 
		{
			$body = new ParserUtils.ClassBody();
			for(int i = 0; i < $a.size(); i++) {
				$body.addNode($a.get(i).node);
			}
		}
	;

classElement returns [Node node]	//FIXME:
	: fieldDeclaration {$node = $fieldDeclaration.node;}
	| functionDeclaration {$node = $functionDeclaration.node;}
	| constructorDeclaration {$node = $constructorDeclaration.node;}
	;

fieldDeclaration returns [Node node]	//FIXME:
	: variableDeclaration
	;

constructorDeclaration returns [Node node]	//FIXME:
	: Constructor LeftParenthese argumentsDeclaration RightParenthese block
		{$node = new Node.ConstructorNode($Constructor, $argumentsDeclaration.decl, $block.node);}
	;

statement returns [Node node]
	: emptyStatement {$node = $emptyStatement.node;}
	| assertStatement statementEnd {$node = $assertStatement.node;}
	| breakStatement statementEnd {$node = $breakStatement.node;}
	| continueStatement statementEnd {$node = $continueStatement.node;}
	| exportEnvStatement statementEnd {$node = $exportEnvStatement.node;}
	| forStatement {$node = $forStatement.node;}
	| ifStatement {$node = $ifStatement.node;}
	| importEnvStatement statementEnd {$node = $importEnvStatement.node;}
	| returnStatement statementEnd {$node = $returnStatement.node;}
	| throwStatement statementEnd {$node = $throwStatement.node;}
	| whileStatement {$node = $whileStatement.node;}
	| doWhileStatement { $node = $doWhileStatement.node;}
	| tryCatchStatement {$node = $tryCatchStatement.node;}
	| variableDeclaration statementEnd {$node = $variableDeclaration.node;}
	| commandListExpression statementEnd {$node = $commandListExpression.node;}
	| assignStatement statementEnd {$node = $assignStatement.node;}
	| expression statementEnd {$node = $expression.node;}
	;

emptyStatement returns [Node node]
	: LineEnd {$node = new Node.EmptyNode();}
	;

assertStatement returns [Node node]
	: Assert  LeftParenthese condExpression RightParenthese 
		{$node = new Node.AssertNode($Assert, $condExpression.node);}
	;

breakStatement returns [Node node]
	: Break {$node = new Node.BreakNode($Break);}
	;

continueStatement returns [Node node]
	: Continue {$node = new Node.ContinueNode($Continue);}
	;

exportEnvStatement returns [Node node]
	: ExportEnv VarName {!hasNewLine()}? '=' expression 
		{$node = new Node.ExportEnvNode($ExportEnv, $VarName, $expression.node);}
	;

forStatement returns [Node node]
	: For LeftParenthese
		(
			AppliedName {!hasNewLine()}? In expression RightParenthese block 
				{$node = new Node.ForInNode($For, $AppliedName, $expression.node, $block.node);}
		|	forInit LineEnd forCond LineEnd forIter RightParenthese block 
				{$node = new Node.ForNode($For, $forInit.node, $forCond.node, $forIter.node, $block.node);}
	 	)
	;

forInit returns [Node node]
	: variableDeclaration {$node = $variableDeclaration.node;}
	| assignStatement {$node = $assignStatement.node;}
	| expression {$node = $expression.node;}
	| {$node = new Node.EmptyNode();}
	;

forCond returns [Node.ExprNode node]
	: expression {$node = $expression.node;}
	| {$node = new Node.EmptyNode();}
	;

forIter returns [Node node]
	: assignStatement {$node = $assignStatement.node;}
	| expression {$node = $expression.node;}
	| {$node = new Node.EmptyNode();}
	;

condExpression returns [Node.ExprNode node]
	: commandListExpression {$node = $commandListExpression.node;}
	| expression {$node = $expression.node;}
	;

ifStatement returns [Node node] locals [ParserUtils.IfElseBlock ifElseBlock]
	: If LeftParenthese condExpression RightParenthese b+=block (Else ei+=ifStatement | Else b+=block)?
		{
			$ifElseBlock = new ParserUtils.IfElseBlock($b.get(0).node);
			if($b.size() > 1) {
				$ifElseBlock.setElseBlockNode($b.get(1).node);
			}
			if($ei.size() > 0) {
				$ifElseBlock.setElseBlockNode($ei.get(0).node);
			}
			$node = new Node.IfNode($If, $condExpression.node, $ifElseBlock);
		}
	;

importEnvStatement returns [Node node]
	: ImportEnv VarName {$node = new Node.ImportEnvNode($VarName);}
	;

returnStatement returns [Node node]
	: Return ({!hasNewLine()}? e+=expression)?
		{
			if($e.size() == 1) {
				$node = new Node.ReturnNode($Return, $e.get(0).node);
			} else {
				$node = new Node.ReturnNode($Return);
			}
		}
	;

throwStatement returns [Node node]
	: Throw expression {$node = new Node.ThrowNode($Throw, $expression.node);}
	;

whileStatement returns [Node node]
	: While LeftParenthese condExpression RightParenthese block
		{$node = new Node.WhileNode($While, $condExpression.node, $block.node);}
	;

doWhileStatement returns [Node node]
	: Do block While LeftParenthese condExpression RightParenthese
		{$node = new Node.WhileNode($Do, $condExpression.node, $block.node, true);}
	;

tryCatchStatement returns [Node node] locals [Node.TryNode tryNode]
	: Try block c+=catchStatement* finallyBlock
		{
			$tryNode = new Node.TryNode($Try, $block.node, $finallyBlock.node);
			for(int i = 0; i < $c.size(); i++) {
				$tryNode.setCatchNode($c.get(i).node);
			}
			$node = $tryNode;
		}
	;

finallyBlock returns [Node node]
	: Finally block {$node = new Node.FinallyNode($Finally, $block.node);}
	| {$node = new Node.EmptyNode();}
	;

catchStatement returns [Node.CatchNode node]
	: Catch LeftParenthese exceptDeclaration RightParenthese block
		{
			$node = new Node.CatchNode($Catch, $exceptDeclaration.except.getName(), 
				$exceptDeclaration.except.getTypeSymbol(), $block.node
			);
		}
	;

exceptDeclaration returns [ParserUtils.CatchedException except]
	: AppliedName (typeAnnoPrefix t+=typeName)?
		{
			$except = new ParserUtils.CatchedException($AppliedName);
			if($t.size() == 1) {
				$except.setTypeSymbol($t.get(0).type);
			}
		}
	;

variableDeclaration returns [Node node]
	: flag=(Let | Var) VarName {!hasNewLine()}? '=' assingRightExpression
		{
			$node = new Node.VarDeclNode($flag, $VarName, $assingRightExpression.node);
		}
	;

assingRightExpression returns [Node.ExprNode node]
	: commandExpression {$node = $commandExpression.node;}
	| expression {$node = $expression.node;}
	;

assignStatement returns [Node node]
	: left=applyOrGetExpression
		({!hasNewLine()}? (
			op=('=' | '+=' | '-=' | '*=' | '/=' | '%=') right=assingRightExpression
				{$node = new Node.AssignNode($left.node, $op, $right.node);}
		|	op=('++' | '--')
				{$node = new Node.AssignNode($left.node, $op);}
		))
	;


// expression definition.
// command expression
commandListExpression returns [Node.ExprNode node]
	: orListCommand {$node = $orListCommand.node;}
	;

orListCommand returns [Node.ExprNode node]
	: l=andListCommand {$node = $l.node;}
		(CmdSep? op=OrList r=andListCommand
			{$node = new Node.CondOpNode($node, $op, $r.node);}
		)*
	;

andListCommand returns [Node.ExprNode node]
	: l=commandExpression {$node = $l.node;}
		(CmdSep? op=AndList r=commandListExpression
			{$node = new Node.CondOpNode($node, $op, $r.node);}
		)*
	;

commandExpression returns [Node.ExprNode node] locals [List<Node.ProcessNode> procList]
	: a+=singleCommandExpr (CmdSep? Pipe a+=singleCommandExpr)* CmdSep? b+=Background?
		{
			$procList = new ArrayList<Node.ProcessNode>($a.size());
			for(int i = 0; i < $a.size(); i++) {
				$procList.add($a.get(i).node);
			}
			$node = new Node.TaskNode($procList, $b.size() == 1);
		}
	;

singleCommandExpr returns [Node.ProcessNode node]	//FIXME:
	: t+=Trace? Command (CmdSep a+=commandArg)* (CmdSep b+=redirOption)*
		{
			$node = new Node.ProcessNode($Command, $t.size() == 1);
			for(int i = 0; i < $a.size(); i++) {
				$node.setArg($a.get(i).node);
			}
			for(int i = 0; i < $b.size(); i++) {
				$node.addRedirOption($b.get(i).option);
			}
		}
	;

commandArg returns [Node.ArgumentNode node]
	: a=commandArgSeg {$node = new Node.ArgumentNode($a.node);}
		(e=commandArgSeg
			{$node.addArgSegment($e.node);}
		)*
	;

commandArgSeg returns [Node.ExprNode node]
	: CmdArgPart {$node = Node.ArgumentNode.createStringValueNode($CmdArgPart);}
	| StringLiteral { $node = new Node.StringValueNode($StringLiteral);}
	| substitutedCommand {$node = $substitutedCommand.node;}
	| interpolation {$node = $interpolation.node;}
	| stringExpr {$node = $stringExpr.node;}
	;

redirOption returns [ParserUtils.RedirOption option]
	: CmdSep? RedirectOp CmdSep? commandArg
		{ $option = new ParserUtils.RedirOption($RedirectOp, $commandArg.node); }
	| CmdSep? RedirectOpNoArg { $option = new ParserUtils.RedirOption($RedirectOpNoArg); }
	;


// normal expression
expression returns [Node.ExprNode node]
	: condOrExpression {$node = $condOrExpression.node;}
	;

condOrExpression returns [Node.ExprNode node]
	: l=condAndExpression {$node = $l.node;}
		({!hasNewLine()}? op=COND_OR e=condAndExpression
			{$node = new Node.CondOpNode($node, $op, $e.node);}
		)*
	;

condAndExpression returns [Node.ExprNode node]
	: l=orExpression {$node = $l.node;}
		({!hasNewLine()}? op=COND_AND e=orExpression
			{$node = new Node.CondOpNode($node, $op, $e.node);}
		)*
	;

orExpression returns [Node.ExprNode node]
	: l=xorExpression {$node = $l.node;}
		({!hasNewLine()}? op=OR e=xorExpression
			{$node = new Node.OperatorCallNode($node, $op, $e.node);}
		)*
	;

xorExpression returns [Node.ExprNode node]
	: l=andExpression {$node = $l.node;}
		({!hasNewLine()}? op=XOR e=andExpression
			{$node = new Node.OperatorCallNode($node, $op, $e.node);}
		)*
	;

andExpression returns [Node.ExprNode node]
	: l=equalityExpression {$node = $l.node;}
		({!hasNewLine()}? op=AND e=equalityExpression
			{$node = new Node.OperatorCallNode($node, $op, $e.node);}
		)*
	;

equalityExpression returns [Node.ExprNode node]
	: l=typeExpression {$node = $l.node;}
		({!hasNewLine()}? op=('==' | '!=' | '=~' | '!~') e=typeExpression
			{$node = new Node.OperatorCallNode($node, $op, $e.node);}
		)*
	;

typeExpression returns [Node.ExprNode node]
	: l=relationalExpression {$node = $l.node;}
		({!hasNewLine()}? (
			Is typeName {$node = new Node.InstanceofNode($node, $As, $typeName.type);}
		|	As typeName {$node = new Node.CastNode($node, $As, $typeName.type);}
		))*
	;

relationalExpression returns [Node.ExprNode node]
	: l=addExpression {$node = $l.node;}
		({!hasNewLine()}? op=(LeftAngleBracket | RightAngleBracket | LE | GE) e=addExpression
			{$node = new Node.OperatorCallNode($node, $op, $e.node);}
		)*
	;

addExpression returns [Node.ExprNode node]
	: l=mulExpression {$node = $l.node;}
		({!hasNewLine()}? op=(PLUS | MINUS) e=mulExpression
			{$node = new Node.OperatorCallNode($node, $op, $e.node);}
		)*
	;

mulExpression returns [Node.ExprNode node]
	: l=unaryExpression {$node = $l.node;}
		({!hasNewLine()}? op=(MUL | DIV | MOD) e=unaryExpression
			{$node = new Node.OperatorCallNode($node, $op, $e.node);}
		)*
	;

unaryExpression returns [Node.ExprNode node]
	: (op+=(PLUS | MINUS | Not) {!hasNewLine()}?)* r=applyOrGetExpression
		{
			$node = $r.node;
			for(int i = $op.size() - 1; i > -1; i--) {
				$node = new Node.OperatorCallNode($op.get(i), $node);
			}
		}
	;

applyOrGetExpression returns [Node.ExprNode node]
	: l=primaryExpression {$node = $l.node;}
		({!hasNewLine()}? (
			Accessor VarName {$node = new Node.AccessNode($node, $VarName);}
		|	LeftBracket i=expression RightBracket
				{$node = new Node.IndexNode($LeftBracket, $node, $i.node);}
		|	arguments {$node = new Node.ApplyNode($node, $arguments.args);}
		))*
	;

primaryExpression returns [Node.ExprNode node]
	: literal {$node = $literal.node;}
	| New {!hasNewLine()}? typeName arguments
		{$node = new Node.ConstructorCallNode($New, $typeName.type, $arguments.args);}
	| symbol {$node = $symbol.node;}
	| substitutedCommand {$node = $substitutedCommand.node;}
	| LeftParenthese expression RightParenthese {$node = $expression.node;}
	| stringExpr {$node = $stringExpr.node;}
	;

literal returns [Node.ExprNode node]
	: IntLiteral {$node = new Node.IntValueNode($IntLiteral);}
	| FloatLiteral {$node = new Node.FloatValueNode($FloatLiteral);}
	| BooleanLiteral {$node = new Node.BooleanValueNode($BooleanLiteral);}
	| StringLiteral {$node = new Node.StringValueNode($StringLiteral);}
	| SpecialName {$node = new Node.SpecialCharNode($SpecialName);}
	| arrayLiteral {$node = $arrayLiteral.node;}
	| mapLiteral {$node = $mapLiteral.node;}
	| pairLiteral {$node = $pairLiteral.node;}
	;

symbol returns [Node.ExprNode node]
	: AppliedName { $node = new Node.SymbolNode($AppliedName);}
	;

substitutedCommand returns [Node.ExprNode node]
	: BackquoteLiteral {$node = ParserUtils.parseBackquotedLiteral($BackquoteLiteral, this);}
	| StartSubCmd commandListExpression RightParenthese
		{ $node = new Node.InnerTaskNode($commandListExpression.node);}
	;

arrayLiteral returns [Node.ExprNode node] locals [Node.ArrayNode arrayNode]
	: LeftBracket expr+=expression (comma expr+=expression)* RightBracket
		{	$arrayNode = new Node.ArrayNode($LeftBracket);
			for(int i = 0; i < $expr.size(); i++) {
				$arrayNode.addNode($expr.get(i).node);
			}
			$node = $arrayNode;
		}
	;

mapLiteral returns [Node.ExprNode node] locals [Node.MapNode mapNode]
	: LeftBrace entrys+=mapEntry (comma entrys+=mapEntry)* RightBrace
		{
			$mapNode = new Node.MapNode($LeftBrace);
			for(int i = 0; i < $entrys.size(); i++) {
				$mapNode.addEntry($entrys.get(i).entry.keyNode, $entrys.get(i).entry.valueNode);
			}
			$node = $mapNode;
		}
	;

mapEntry returns [ParserUtils.MapEntry entry]
	: key=expression {!hasNewLine()}? Colon value=expression
		{$entry = new ParserUtils.MapEntry($key.node, $value.node);}
	;

pairLiteral returns [Node.ExprNode node]
	: LeftParenthese left=expression comma right=expression RightParenthese
		{
			$node = new Node.PairNode($LeftParenthese, $left.node, $right.node);
		}
	;

arguments returns [ParserUtils.Arguments args]
	: LeftParenthese a+=argumentList? RightParenthese
		{
			$args = new ParserUtils.Arguments();
			if($a.size() == 1) {
				$args = $a.get(0).args;
			}
		}
	;

argumentList returns [ParserUtils.Arguments args]
	: a= expression {$args = new ParserUtils.Arguments(); $args.addNode($a.node);}
		(comma b=expression {$args.addNode($b.node);})*
	;

interpolation returns [Node.ExprNode node]
	: symbol { $node = $symbol.node; }
	| BooleanLiteral { $node = new Node.BooleanValueNode($BooleanLiteral); }
	| SpecialName { $node = new Node.SpecialCharNode($SpecialName);}
	| StartInterp expression RightBrace { $node = $expression.node; }
	;

stringExpr returns [Node.StringExprNode node]
	: OpenDoubleQuote {$node = new Node.StringExprNode($OpenDoubleQuote);}
		(a=stringElement
			{$node.addElementNode($a.node);}
		)* CloseDoubleQuote
	;

stringElement returns [Node.ExprNode node]
	: StringElement { $node = new Node.StringValueNode($StringElement, false);}
	| interpolation { $node = $interpolation.node; }
	| substitutedCommand { $node = $substitutedCommand.node; }
	;

