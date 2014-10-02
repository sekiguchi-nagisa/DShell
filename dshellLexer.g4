lexer grammar dshellLexer;

@header {
package dshell.internal.parser;
import java.util.EmptyStackException;
}

@members {
private boolean trace = false;

@Override
public Token nextToken() {
	Token token = super.nextToken();
	if(this.trace) {
		System.err.println("@nextToken: " + token);
	}
	this.stmtMode = this.enterStmt;
	this.enterStmt = false;
	return token;
}

public void setTrace(boolean trace) {
	this.trace = trace;
}

private boolean stmtMode = true;
private boolean enterStmt = false;

private boolean isStmt() {
	return this.stmtMode;
}

@Override
public int popMode() {
	try {
		return super.popMode();
	} catch(EmptyStackException e) {
		return DEFAULT_MODE;
	}
}

@Override
public void reset() {
	super.reset();
	this.stmtMode = true;
	this.enterStmt = false;
}
}


// ##########################
// ##     default mode     ##
// ##########################

// reserved key word
As          : {!isStmt()}? 'as';
Assert      :  {isStmt()}? 'assert';
Boolean     : {!isStmt()}? 'boolean';
Break       :  {isStmt()}? 'break';
Catch       : 'catch';
Class       :  {isStmt()}? 'class';
Continue    :  {isStmt()}? 'continue';
Constructor : 'constructor';
Do          : 'do';
Else        : 'else';
Extends     : 'extends';
ExportEnv   :  {isStmt()}? 'export-env' -> pushMode(NameMode);
Finally     : 'finally';
Float       : {!isStmt()}? 'float';
For         :  {isStmt()}? 'for';
Func        : {!isStmt()}? 'Func';
Function    :  {isStmt()}? 'function' -> pushMode(NameMode);
If          : 'if';
ImportEnv   :  {isStmt()}? 'import-env' -> pushMode(NameMode);
In          : {!isStmt()}? 'in';
Int         : {!isStmt()}? 'int';
Is          : {!isStmt()}? 'is';
Let         :  {isStmt()}? 'let' -> pushMode(NameMode);
New         : 'new';
Not         : 'not';
Return      :  {isStmt()}? 'return';
Try         :  {isStmt()}? 'try';
Throw       :  {isStmt()}? 'throw';
Var         :  {isStmt()}? 'var' -> pushMode(NameMode);
Void        : {!isStmt()}? 'void';
While       : 'while';

PLUS        : '+';
MINUS       : '-';

// literal
// integer literal //TODO: hex. oct number
IntLiteral
	: Number
	;

fragment
Number
	: '0'
	| [1-9] [0-9]*
	;

// float literal
FloatLiteral
	: Number '.' Number FloatSuffix?
	;

fragment
FloatSuffix
	: [eE] [+-]? Number
	;

// boolean literal
BooleanLiteral
	: '$true'
	| '$false'
	;

// String literal
StringLiteral
	: '\'' SingleQuoteStringChar* '\''
	;

OpenDoubleQuote
	: '"' -> pushMode(DoubleQuoteStringMode)
	;

fragment
SingleQuoteStringChar
	: ~[\r\n'\\]
	| SingleEscapeSequence
	;

fragment
SingleEscapeSequence	// TODO: unicode escape
	: '\\' [btnfr'\\]
	;

// variable or function name
AppliedName
	: '$' PermittedName
	;

SpecialName
	: '$$'
	| '$@'
	| '$*'
	;

// command literal
BackquoteLiteral
	: '`' BackquoteChar+ '`'
	;

fragment
BackquoteChar
	: '\\' '`'
	| ~[`\n\r]
	;

StartSubCmd
	: '$(' {this.enterStmt = true;} -> pushMode(DEFAULT_MODE)
	;


// command
Command
	: {isStmt()}? CommandStartChar CommandChar* -> pushMode(CommandMode) 
	;

fragment
CommandChar
	: '\\' .
	| ~[ \t\r\n;'"`|&<>(){}$#![\]]
	;

fragment
CommandStartChar
	: '\\' .
	| ~[ \t\r\n;'"`|&<>(){}$#![\]0-9+\-]
	;

// bracket
LeftParenthese    : '(' {this.enterStmt = true;} -> pushMode(DEFAULT_MODE);
RightParenthese   : ')' -> popMode;
LeftBracket       : '[';
RightBracket      : ']';
LeftBrace         : '{' {this.enterStmt = true;} -> pushMode(DEFAULT_MODE);
RightBrace        : '}' -> popMode;
LeftAngleBracket  : '<';
RightAngleBracket : '>';

// separator
Colon             : ':';
Comma             : ',';

// operator
// binary op
//ADD           : '+'; -> PLUS
//SUB           : '-'; -> MINUS
MUL           : '*';
DIV           : '/';
MOD           : '%';
//LT            : '<'; -> LeftAngleBracket
//GT            : '>'; -> RightAngleBracket
LE            : '<=';
GE            : '>=';
EQ            : '==';
NE            : '!=';
AND           : '&';
OR            : '|';
XOR           : '^';
COND_AND      : '&&';
COND_OR       : '||';
REGEX_MATCH   : '=~';
REGEX_UNMATCH : '!~';

// suffix op
INC           : '++';
DEC           : '--';

// assign op
ASSIGN        : '='  {this.enterStmt = true;};
ADD_ASSIGN    : '+=' {this.enterStmt = true;};
SUB_ASSIGN    : '-=' {this.enterStmt = true;};
MUL_ASSIGN    : '*=' {this.enterStmt = true;};
DIV_ASSIGN    : '/=' {this.enterStmt = true;};
MOD_ASSIGN    : '%=' {this.enterStmt = true;};


// identifier
Identifier
	: PermittedName
	;

// field accessor
Accessor
	: '.' -> pushMode(NameMode);

// line end
LineEnd
	: ';' [ \t\r\n]* {this.enterStmt = true;}
	;

NewLine
	: [\n\r] [ \t\r\n]* {this.enterStmt = true;} -> channel(HIDDEN)
	;

// comment and space
Comment
	: '#' ~[\r\n]* -> skip
	;

WhiteSpace
	: WhiteSpaceFragment+ -> skip
	;

fragment
WhiteSpaceFragment
	: '\\' [\r\n]
	| [ \t]
	;

Other: .;

// #################################################
// ##     variable name or function name mode     ##
// #################################################

mode NameMode;
ReseredName
	: 'this'
	| 'super'
	| 'true'
	| 'false'
	;

VarName
	: PermittedName -> popMode
	;

fragment
PermittedName
	: [a-zA-Z] [_0-9a-zA-Z]*
	| '_' [_0-9a-zA-Z]+
	;

SkipChars
	: SkipChar+ -> skip;

fragment
SkipChar
	: '\\' [\r\n]
	| [ \t]
	;

Name_Other: . ;

// ######################################
// ##     double quote string mode     ##
// ######################################

mode DoubleQuoteStringMode;
CloseDoubleQuote : '"' -> popMode;

StringElement
	: DoubleQuoteStringChar+
	;

fragment
DoubleQuoteStringChar
	: ~[\r\n`$"\\]
	| DoubleEscapeSequence
	;

fragment
DoubleEscapeSequence	// TODO: unicode escape
	: '\\' [$btnfr"`\\]
	;

StartInterp
	: '${' -> pushMode(DEFAULT_MODE)
	;

String_StartSubCmd
	: StartSubCmd {this.enterStmt = true;} -> pushMode(DEFAULT_MODE), type(StartSubCmd)
	;

InnerCmdBackQuote
	: BackquoteLiteral -> type(BackquoteLiteral)
	;

InnerName
	: AppliedName -> type(AppliedName)
	;

InnerSpecialName
	: SpecialName -> type(SpecialName)
	;

String_Other: .;

// ##########################
// ##     command mode     ##
// ##########################

mode CommandMode;
// part of command argument
CmdArgPart
	: CommandChar+
	;

CmdArgPart_String
	: StringLiteral -> type(StringLiteral)
	;

CmdArgPart_OpenDoubleQuote
	: '"' -> type(OpenDoubleQuote), pushMode(DoubleQuoteStringMode)
	;

CmdArgPart_StartInterp
	: StartInterp -> type(StartInterp), pushMode(DEFAULT_MODE)
	;

CmdArgPArt_StartSubCmd
	: StartSubCmd {this.enterStmt = true;} -> pushMode(DEFAULT_MODE), type(StartSubCmd)
	;

CmdArgPart_BackquoteLiteral
	: BackquoteLiteral -> type(BackquoteLiteral)
	;

CmdArgPart_AppliedName
	: AppliedName -> type(AppliedName)
	;

CmdArgPart_SpecialName
	: SpecialName -> type(SpecialName)
	;

CmdSep
	: [ \t]+
	;

RedirectOp
	: '<'
	| '>'
	| '1>'
	| '1>>'
	| '>>'
	| '2>'
	| '2>>'
	| '>&'
	| '&>'
	| '&>>'
	;

RedirectOpNoArg
	: '2>&1'
	;

Pipe       : '|' {this.enterStmt = true;} -> popMode;
Background : '&';
OrList     : '||' {this.enterStmt = true;} -> popMode;
AndList    : '&&' {this.enterStmt = true;} -> popMode;

Cmd_LineEnd
	: LineEnd {this.enterStmt = true;} -> type(LineEnd), popMode
	;

Cmd_Comment
	: Comment -> skip
	;

Cmd_NewLine
	: NewLine {this.enterStmt = true;} -> type(NewLine), popMode
	;

Cmd_RightParenthese
	: RightParenthese -> type(RightParenthese), popMode, popMode
	;

Cmd_Other: .;
