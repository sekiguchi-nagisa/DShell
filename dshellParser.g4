parser grammar dshellParser;

options { tokenVocab=dshellLexer; }


@header {
package dshell.internal.parser;
import dshell.internal.parser.Node;
import dshell.internal.parser.ParserUtils;
import dshell.internal.parser.TypeSymbol;
}

@members {
// parser entry point.
public ToplevelContext startParser() {
	return this.toplevel();
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

returnType returns [TypeSymbol type]
	: typeAnnoPrefix typeNameWithVoid {$type = $typeNameWithVoid.type;}
	| { $type = TypeSymbol.toVoid(); }
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

typeName returns [TypeSymbol type] locals [TypeSymbol[] types]
	: Int        {$type = TypeSymbol.toPrimitive($Int);}
	| Float      {$type = TypeSymbol.toPrimitive($Float);}
	| Boolean    {$type = TypeSymbol.toPrimitive($Boolean);}
	| Identifier {$type = TypeSymbol.toClass($Identifier);}
	| Func openType aa=typeNameWithVoid paramTypes closeType
	             {$type = TypeSymbol.toFunc($Func, $aa.type, $paramTypes.types);}
	| Identifier openType a+=typeName (comma a+=typeName)* closeType
		{
			$types = new TypeSymbol[$a.size()];
			for(int i = 0; i < $types.length; i++) {
				$types[i] = $a.get(i).type;
			}
			$type = TypeSymbol.toGeneric($Identifier, $types);
		}
	;

typeNameWithVoid returns [TypeSymbol type]
	: typeName {$type = $typeName.type;}
	| Void {$type = TypeSymbol.toVoid($Void);}
	;

paramTypes returns [TypeSymbol[] types] locals [ParserUtils.ParamTypeResolver resolver]
	: typeSep openParamType a+=typeName ( typeSep a+=typeName)* closeParamType
		{
			$resolver = new ParserUtils.ParamTypeResolver();
			for(int i = 0; i < $a.size(); i++) {
				$resolver.addTypeSymbol($a.get(i).type);
			}
			$types = $resolver.getTypeSymbols();
		}
	| { $resolver = new ParserUtils.ParamTypeResolver(); $types = $resolver.getTypeSymbols();}
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
	: left=assignLeftExpression
		({!hasNewLine()}? (
			op=('=' | '+=' | '-=' | '*=' | '/=' | '%=') right=assingRightExpression
				{$node = new Node.AssignNode($op, $left.node, $right.node);}
		|	op=('++' | '--')
				{$node = new Node.AssignNode($left.node, $op);}
		))
	;

assignLeftExpression returns [Node.ExprNode node]
	: symbol {$node = $symbol.node;}
		({!hasNewLine()}? (
			Accessor VarName 
				{$node = new Node.FieldGetterNode($node, $VarName);}
		|	LeftBracket i=expression RightBracket
				{$node = new Node.ElementGetterNode($LeftBracket, $node, $i.node);}
		))*
	;


// expression definition.
// command expression
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
	: Command (CmdSep a+=commandArg)* (CmdSep b+=redirOption)*
		{
			$node = new Node.ProcessNode($Command);
			for(int i = 0; i < $a.size(); i++) {
				$node.setArg($a.get(i).node);
			}
			for(int i = 0; i < $b.size(); i++) {
				$node.addRedirOption($b.get(i).option);
			}
		}
	;

commandArg returns [Node.ArgumentNode node]
	: a+=commandArgSeg+
		{
			$node = new Node.ArgumentNode($a.get(0).node);
			for(int i = 1; i < $a.size(); i++) {
				$node.addArgSegment($a.get(i).node);
			}
		}
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

commandListExpression returns [Node.ExprNode node]
	: commandExpression { $node = $commandExpression.node; }
	| left=commandListExpression CmdSep? AndList right=commandListExpression
		{ $node = new Node.CondOpNode($AndList, $left.node, $right.node); }
	| left=commandListExpression CmdSep? OrList right=commandListExpression
		{ $node = new Node.CondOpNode($OrList, $left.node, $right.node); }
	;

// normal expression
expression returns [Node.ExprNode node]
	: condOrExpression {$node = $condOrExpression.node;}
	;

condOrExpression returns [Node.ExprNode node]
	: l=condAndExpression ({!hasNewLine()}? op+=COND_OR e+=condAndExpression)*
		{
			$node = $l.node;
			for(int i = 0; i < $e.size(); i++) {
				$node = new Node.CondOpNode($op.get(i), $node, $e.get(i).node);
			}
		}
	;

condAndExpression returns [Node.ExprNode node]
	: l=orExpression ({!hasNewLine()}? op+=COND_AND e+=orExpression)*
		{
			$node = $l.node;
			for(int i = 0; i < $e.size(); i++) {
				$node = new Node.CondOpNode($op.get(i), $node, $e.get(i).node);
			}
		}
	;

orExpression returns [Node.ExprNode node]
	: l=xorExpression ({!hasNewLine()}? op+=OR e+=xorExpression)*
		{
			$node = $l.node;
			for(int i = 0; i < $e.size(); i++) {
				$node = new Node.OperatorCallNode($op.get(i), $node, $e.get(i).node);
			}
		}
	;

xorExpression returns [Node.ExprNode node]
	: l=andExpression ({!hasNewLine()}? op+=XOR e+=andExpression)*
		{
			$node = $l.node;
			for(int i = 0; i < $e.size(); i++) {
				$node = new Node.OperatorCallNode($op.get(i), $node, $e.get(i).node);
			}
		}
	;

andExpression returns [Node.ExprNode node]
	: l=equalityExpression ({!hasNewLine()}? op+=AND e+=equalityExpression)*
		{
			$node = $l.node;
			for(int i = 0; i < $e.size(); i++) {
				$node = new Node.OperatorCallNode($op.get(i), $node, $e.get(i).node);
			}
		}
	;

equalityExpression returns [Node.ExprNode node]
	: l=instanceofExpression ({!hasNewLine()}? op+=('==' | '!=' | '=~' | '!~') e+=instanceofExpression)*
		{
			$node = $l.node;
			for(int i = 0; i < $e.size(); i++) {
				$node = new Node.OperatorCallNode($op.get(i), $node, $e.get(i).node);
			}
		}
	;
	
instanceofExpression returns [Node.ExprNode node]
	: l=relationalExpression ({!hasNewLine()}? op+=Instanceof t+=typeName)*
		{
			$node = $l.node;
			for(int i = 0; i < $t.size(); i++) {
				$node = new Node.InstanceofNode($op.get(i), $node, $t.get(i).type);
			}
		}
	;

relationalExpression returns [Node.ExprNode node]
	: l=addExpression ({!hasNewLine()}? op+=(LeftAngleBracket | RightAngleBracket | LE | GE) e+=addExpression)*
		{
			$node = $l.node;
			for(int i = 0; i < $e.size(); i++) {
				$node = new Node.OperatorCallNode($op.get(i), $node, $e.get(i).node);
			}
		}
	;

addExpression returns [Node.ExprNode node]
	: l=mulExpression ({!hasNewLine()}? op+=(PLUS | MINUS) e+=mulExpression)*
		{
			$node = $l.node;
			for(int i = 0; i < $e.size(); i++) {
				$node = new Node.OperatorCallNode($op.get(i), $node, $e.get(i).node);
			}
		}
	;

mulExpression returns [Node.ExprNode node]
	: l=unaryExpression ({!hasNewLine()}? op+=(MUL | DIV | MOD) e+=unaryExpression)*
		{
			$node = $l.node;
			for(int i = 0; i < $e.size(); i++) {
				$node = new Node.OperatorCallNode($op.get(i), $node, $e.get(i).node);
			}
		}
	;

unaryExpression returns [Node.ExprNode node]
	: (op+=(PLUS | MINUS | Not) {!hasNewLine()}?)* r=castExpression
		{
			$node = $r.node;
			for(int i = $op.size() - 1; i > -1; i--) {
				$node = new Node.OperatorCallNode($op.get(i), $node);
			}
		}
	;

castExpression returns [Node.ExprNode node]
	: l=applyOrGetExpression ({!hasNewLine()}? As t+=typeName)*
		{
			$node = $l.node;
			for(int i = 0; i < $t.size(); i++) {
				$node = new Node.CastNode($node, $t.get(i).type);
			}
		}
	;

applyOrGetExpression returns [Node.ExprNode node]
	: l=primaryExpression {$node = $l.node;}
		({!hasNewLine()}? (
			Accessor VarName {$node = new Node.FieldGetterNode($node, $VarName);}
		|	LeftBracket i=expression RightBracket
				{$node = new Node.ElementGetterNode($LeftBracket, $node, $i.node);}
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
	: a+= expression (comma a+=expression)* 
		{
			$args = new ParserUtils.Arguments();
			for(int i = 0; i < $a.size(); i++) {
				$args.addNode($a.get(i).node);
			}
		}
	;

interpolation returns [Node.ExprNode node]
	: symbol { $node = Node.CastNode.toString($symbol.node); }
	| SpecialName { $node = new Node.SpecialCharNode($SpecialName);}
	| StartInterp expression RightBrace { $node = Node.CastNode.toString($expression.node); }
	;

stringExpr returns [Node.StringExprNode node]
	: OpenDoubleQuote a+=stringElement* CloseDoubleQuote
		{
			$node = new Node.StringExprNode($OpenDoubleQuote);
			if($a.size() > 0) {
				for(int i = 0; i < $a.size(); i++) {
					$node.addElementNode($a.get(i).node);
				}
			}
		}
	;

stringElement returns [Node.ExprNode node]
	: StringElement { $node = new Node.StringValueNode($StringElement, false);}
	| interpolation { $node = $interpolation.node; }
	| substitutedCommand { $node = $substitutedCommand.node; }
	;

