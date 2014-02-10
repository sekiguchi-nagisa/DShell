package zen.codegen.jvm;

import java.util.ArrayList;

import zen.codegen.jvm.JavaEngine;
import zen.codegen.jvm.JavaSolution;
import dshell.ast.DShellCommandNode;
import dshell.ast.DShellTryNode;
import dshell.lang.ModifiedTypeSafer;
import dshell.lib.TaskBuilder;

public class ModifiedJavaEngine extends JavaEngine {	//TODO: implement unsupported visit api
	public ModifiedJavaEngine(ModifiedTypeSafer TypeChecker, JavaSolution Generator) {
		super(TypeChecker, Generator);
	}

	public void VisitCommandNode(DShellCommandNode Node) {
		ArrayList<DShellCommandNode> nodeList = new ArrayList<DShellCommandNode>();
		DShellCommandNode node = Node;
		while(node != null) {
			nodeList.add(node);
			node = (DShellCommandNode) node.PipedNextNode;
		}
		int size = nodeList.size();
		String[][] values = new String[size][];
		for(int i = 0; i < size; i++) {
			DShellCommandNode currentNode = nodeList.get(i);
			int listSize = currentNode.GetListSize();
			values[i] = new String[listSize];
			for(int j = 0; j < listSize; j++) {
				values[i][j] = (String)this.Eval(currentNode.GetListAt(j));
			}
		}
		if(Node.Type.IsBooleanType()) {
			this.EvaledValue = TaskBuilder.ExecCommandBoolTopLevel(values);
		}
		else if(Node.Type.IsIntType()) {
			this.EvaledValue = TaskBuilder.ExecCommandIntTopLevel(values);
		}
		else if(Node.Type.IsStringType()) {
			this.EvaledValue = TaskBuilder.ExecCommandStringTopLevel(values);
		}
		else if(Node.Type.ShortName.equals("Task")) {
			this.EvaledValue = TaskBuilder.ExecCommandTaskTopLevel(values);
		}
		else {
			TaskBuilder.ExecCommandVoid(values);
		}
	}

	public void VisitTryNode(DShellTryNode Node) {
		this.Unsupported(Node, "try");
	}
}