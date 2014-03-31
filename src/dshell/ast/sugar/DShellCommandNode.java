package dshell.ast.sugar;

import java.util.ArrayList;

import libbun.parser.ast.ZDesugarNode;
import libbun.parser.ast.ZNode;
import libbun.parser.ast.ZSugarNode;
import libbun.parser.ZGenerator;
import libbun.parser.ZToken;
import libbun.parser.ZTypeChecker;
import libbun.type.ZType;
import libbun.util.Field;
import libbun.util.Var;

public class DShellCommandNode extends ZSugarNode {
	@Field private final ArrayList<ZNode> ArgList;
	@Field private ZType RetType = ZType.VarType;
	@Field public ZNode PipedNextNode;

	public DShellCommandNode(ZNode ParentNode, ZToken Token, String Command) {
		super(ParentNode, Token, 0);
		this.PipedNextNode = null;
		this.ArgList = new ArrayList<ZNode>();
		this.AppendArgNode(new DShellArgNode(ParentNode, Command));
	}

	public void AppendArgNode(ZNode Node) {
		this.ArgList.add(this.SetChild(Node, true));
	}

	public ZNode AppendPipedNextNode(DShellCommandNode Node) {
		@Var DShellCommandNode CurrentNode = this;
		while(CurrentNode.PipedNextNode != null) {
			CurrentNode = (DShellCommandNode) CurrentNode.PipedNextNode;
		}
		CurrentNode.PipedNextNode = CurrentNode.SetChild(Node, false);
		return this;
	}

	public int GetArgSize() {
		return this.ArgList.size();
	}

	public void SetArgAt(int Index, ZNode ArgNode) {
		this.ArgList.set(Index, ArgNode);
	}

	public ZNode GetArgAt(int Index) {
		return this.ArgList.get(Index);
	}

	public void SetType(ZType Type) {
		this.RetType = Type;
	}

	public ZType RetType() {
		return this.RetType;
	}

	@Override
	public ZDesugarNode DeSugar(ZGenerator Generator, ZTypeChecker TypeChekcer) {
		return null;
	}
}