lexer grammar dshellLexer;

@header {
package dshell.internal.parser;
import dshell.internal.parser.Node;
import dshell.internal.parser.ParserUtils;
import dshell.internal.parser.TypeSymbol;
}

@members {
private boolean trace = false;

@Override
public Token nextToken() {
	Token token = super.nextToken();
	if(this.trace) {
		System.err.println("@nextToken: " + token);
	}
	return token;
}

public void setTrace(boolean trace) {
	this.trace = trace;
}
}

// ######################
// #        lexer       #
// ######################

// reserved keyword
Assert      : 'assert';
Break       : 'break';
Boolean     : 'boolean';
Catch       : 'catch';
Continue    : 'continue';
Class       : 'class';
Constructor : 'constructor';
Do          : 'do';
Else        : 'else';
Extends     : 'extends';
ExportEnv   : 'export-env';
Func        : 'Func';
Function    : 'function';
Finally     : 'finally';
Float       : 'float';
For         : 'for';
If          : 'if';
ImportCmd   : 'import-command';
ImportEnv   : 'import-env';
In          : 'in';
Int         : 'int';
Instanceof  : 'instanceof';
Let         : 'let';
New         : 'new';
Return      : 'return';
Super       : 'super';
Try         : 'try';
Throw       : 'throw';
Var         : 'var';
Void        : 'void';
While       : 'while';

LeftParenthese  : '(';
RightParenthese : ')';
LeftBracket     : '[';
RightBracket    : ']';
LeftBrace       : '{';
RightBrace      : '}';

Colon           : ':';
Semicolon       : ';';
Comma           : ',';
Period          : '.';
BackSlash       : '\\';
Dollar          : '$';
At              : '@';

// operator
// binary op
ADD           : '+';
SUB           : '-';
MUL           : '*';
DIV           : '/';
MOD           : '%';
LT            : '<';
GT            : '>';
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

// prefix op
BIT_NOT       : '~';
NOT           : '!';

// suffix op
INC           : '++';
DEC           : '--';

// assign op
ASSIGN        : '=';
ADD_ASSIGN    : '+=';
SUB_ASSIGN    : '-=';
MUL_ASSIGN    : '*=';
DIV_ASSIGN    : '/=';
MOD_ASSIGN    : '%=';


// literal
// int literal	//TODO: hex, oct number
fragment
Number
	: '0'
	| [1-9] [0-9]*
	;
IntLiteral
	: Number
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
	: 'true'
	| 'false'
	;

// String literal	//TODO: interpolation
StringLiteral
	: '"' DoubleQuoteStringChar* '"'
	| '\'' SingleQuoteStringChar* '\''
	;

fragment
DoubleQuoteStringChar
	: ~["\\]
	| EscapeSequence
	;

fragment
SingleQuoteStringChar
	: ~['\\]
	| EscapeSequence
	;

fragment
EscapeSequence	// TODO: unicode escape
	: '\\' [btnfr"'\\]
	;

// symbol , class and command name
Identifier
	: [_a-zA-Z] [_0-9a-zA-Z]*
	;

// back quoted command
BackquotedLiteral
	: '`' BackquotedChar+ '`'
	;

fragment
BackquotedChar
	: '\\' '`'
	| ~['`']
	;

Dollar_At
	: '$@'
	;

// unicode character
UTF8Chars
	: UTF8Char+
	;

fragment
UTF8Char
	: [\u0080-\u07FF]
	| [\u0800-\uFFFF]
	;

// comment & space
Comment
	: '#' ~[\r\n\u2028\u2029]* -> skip
	;

fragment
WhiteSpaceFragment
	: [\t\u000B\u000C\u0020\u00A0]
	;

WhiteSpace
	: WhiteSpaceFragment+
	;

fragment
LineEndFragment
	:[\r\n\u2028\u2029]
	;

LineEnd
	: LineEndFragment+
	;

EscapedSymbol
	: '\\' '#'
	| '\\' LineEndFragment
	| '\\' WhiteSpaceFragment
	;
