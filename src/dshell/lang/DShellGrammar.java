package dshell.lang;

import java.util.ArrayList;

import zen.util.ZMatchFunction;
import zen.grammar.ComparatorPatternFunction;
import zen.ast.ZBlockNode;
import zen.ast.ZStringNode;
import zen.codegen.jvm.JavaImportPattern;
import zen.lang.konoha.ContinuePatternFunction;
import zen.lang.zen.ZenPrecedence;
import zen.lang.zen.ZenGrammar;
import zen.parser.ZNameSpace;
import zen.parser.ZSyntax;
import zen.parser.ZToken;
import zen.parser.ZTokenContext;
import dshell.DShell;
import dshell.grammar.CommandArgPattern;
import dshell.grammar.CommandSymbolToken;
import dshell.grammar.ExportEnvPattern;
import dshell.grammar.ForPattern;
import dshell.grammar.ImportCommandPattern;
import dshell.grammar.DShellCatchPattern;
import dshell.grammar.DShellImportPattern;
import dshell.grammar.CommandSymbolPattern;
import dshell.grammar.DShellTryPattern;
import dshell.grammar.ImportEnvPattern;
import dshell.grammar.ForeachPattern;
import dshell.grammar.LocationDefinePattern;
import dshell.grammar.PrefixOptionPattern;
import dshell.grammar.RedirectPattern;
import dshell.grammar.ShellStyleCommentToken;
import dshell.grammar.SuffixOptionPattern;
import dshell.lib.BuiltinCommand;

public class DShellGrammar {
	// suffix option symbol
	public final static String background = "&";
	// prefix option symbol 
	public final static String timeout = "timeout";
	public final static String trace = "trace";
	public final static String location = "location";

	public static String toCommandSymbol(String Symbol) {
		return "__$" + Symbol;
	}

	public static String toLocationSymbol(String Symbol) {
		return "__@$" + Symbol;
	}

	public static boolean MatchStopToken(ZTokenContext TokenContext) { // ;,)]}&&||
		ZToken Token = TokenContext.GetToken();
		if(!TokenContext.HasNext()) {
			return true;
		}
		if(Token.IsIndent() || Token.EqualsText(";")) {
			return true;
		}
		if(Token.EqualsText(",") || Token.EqualsText(")") || Token.EqualsText("]") || 
				Token.EqualsText("}") || Token.EqualsText("&&") || Token.EqualsText("||")) {
			return true;
		}
		return false;
	}

	public static void ImportGrammar(ZNameSpace NameSpace) {
		// import ZenGrammer
		ZenGrammar.ImportGrammar(NameSpace);
		overrideSyntaxPattern(NameSpace, "import", new JavaImportPattern(), true);
		overrideSyntaxPattern(NameSpace, "continue", new ContinuePatternFunction(), true);

		// import DShell Specific Grammar
		CommandSymbolToken commandSymbolToken = new CommandSymbolToken();
		ImportCommandPattern importCommandPattern = new ImportCommandPattern();
		CommandSymbolPattern commandSymbolPattern = new CommandSymbolPattern();
		ComparatorPatternFunction comparatorPattern = new ComparatorPatternFunction();
		PrefixOptionPattern prefixOptionPattern = new PrefixOptionPattern();

		NameSpace.AppendTokenFunc("#", new ShellStyleCommentToken());
		NameSpace.AppendTokenFunc("Aa_", commandSymbolToken);
		NameSpace.AppendTokenFunc("1", commandSymbolToken);

		NameSpace.DefineStatement("import", new DShellImportPattern());
		NameSpace.DefineExpression(ImportEnvPattern.PatternName, new ImportEnvPattern());
		NameSpace.DefineStatement("command", importCommandPattern);
		NameSpace.DefineExpression(ImportCommandPattern.PatternName, importCommandPattern);
		NameSpace.DefineExpression(CommandArgPattern.PatternName, new CommandArgPattern());
		NameSpace.DefineExpression(RedirectPattern.PatternName, new RedirectPattern());
		NameSpace.DefineExpression(SuffixOptionPattern.PatternName, new SuffixOptionPattern());
		NameSpace.DefineExpression(CommandSymbolPattern.PatternName, commandSymbolPattern);
		NameSpace.DefineRightExpression("=~", ZenPrecedence._CStyleEquals, comparatorPattern);
		NameSpace.DefineRightExpression("!~", ZenPrecedence._CStyleEquals, comparatorPattern);
		overrideSyntaxPattern(NameSpace, "try", new DShellTryPattern(), true);
		overrideSyntaxPattern(NameSpace, DShellTryPattern.CatchPatternName, new DShellCatchPattern(), true);
		NameSpace.DefineStatement(location, new LocationDefinePattern());
		NameSpace.DefineExpression(timeout, prefixOptionPattern);
		NameSpace.DefineExpression(trace, prefixOptionPattern);
		NameSpace.DefineExpression(PrefixOptionPattern.PatternName, prefixOptionPattern);
		NameSpace.DefineStatement("for", new ForPattern());
		NameSpace.DefineStatement("for", new ForeachPattern());
		NameSpace.DefineStatement(ExportEnvPattern.PatternName, new ExportEnvPattern());

		// from BultinCommandMap
		ArrayList<String> symbolList = BuiltinCommand.getCommandSymbolList();
		for(String symbol : symbolList) {
			setOptionalSymbol(NameSpace, symbol);
		}
		NameSpace.Generator.LangInfo.AppendGrammarInfo("dshell" + DShell.version);
	}

	private static void setOptionalSymbol(ZNameSpace NameSpace, String symbol) { // FIXME: null
		NameSpace.SetGlobalSymbol(DShellGrammar.toCommandSymbol(symbol), new ZStringNode(new ZBlockNode(null, NameSpace), null, symbol));
	}

	private static void overrideSyntaxPattern(ZNameSpace NameSpace, String PatternName, ZMatchFunction MatchFunc, boolean isStatement) {
		ZSyntax Pattern = new ZSyntax(NameSpace, PatternName, MatchFunc);
		Pattern.IsStatement = isStatement;
		NameSpace.SetSyntaxPattern(PatternName, Pattern);
	}
}
