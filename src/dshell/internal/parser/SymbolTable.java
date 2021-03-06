package dshell.internal.parser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import dshell.internal.lib.Utils;
import dshell.internal.parser.SymbolTable.SymbolEntry;
import dshell.internal.type.DSType.UnresolvedType;
import dshell.internal.type.DSType.VoidType;
import dshell.internal.type.ParametricType;
import dshell.internal.type.ParametricType.ParametricGenericType;
import dshell.internal.type.DSType;

/**
 * contains defined variable and function symbol type.
 * @author skgchxngsxyz-opensuse
 *
 */

interface SymbolTableOp {
	/**
	 * get entry from it.
	 * @param symbolName
	 * @return
	 * if entry does not exist, search parent symbol table.
	 * return null, if entry does not exist.
	 */
	public SymbolEntry getEntry(String symbolName);

	/**
	 * add new entry
	 * @param symbolName
	 * @param type
	 * - must not void type, parametric type or unresolved type.
	 * @param isReadOnly
	 * @return
	 * - if entry has already existed , return false.
	 */
	public boolean addEntry(String symbolName, DSType type, boolean isReadOnly);
}

public class SymbolTable implements SymbolTableOp {
	private final Deque<SymbolTableOp> tableStack;

	public SymbolTable() {
		this.tableStack = new ArrayDeque<>();
		this.tableStack.push((new RootTable()));
	}

	@Override
	public SymbolEntry getEntry(String symbolName) {
		return this.tableStack.peek().getEntry(symbolName);
	}

	@Override
	public boolean addEntry(String symbolName, DSType type, boolean isReadOnly) {
		if((type instanceof UnresolvedType) || (type instanceof ParametricType) || 
				(type instanceof ParametricGenericType) || (type instanceof VoidType)) {
			Utils.fatal(1, "unacceptable type: " + type);
		}
		return this.tableStack.peek().addEntry(symbolName, type, isReadOnly);
	}

	/**
	 * create child symbol table.
	 * child table contains reference of this table.
	 * @return
	 */
	public void enterScope() {
		this.tableStack.push(new ChildTable(this.tableStack.peek()));
	}

	/**
	 * remove current symbol table.
	 */
	public void exitScope() {
		if(this.tableStack.size() > 1) {
			this.tableStack.pop();
		}
	}

	/**
	 * pop all local symbol table.
	 */
	public void popAllLocal() {
		int size = this.tableStack.size();
		for(int i = 0; i < size; i++) {
			this.exitScope();
		}
	}

	public void clearEntryCache() {
		assert this.tableStack.size() == 1;
		((RootTable) this.tableStack.peek()).clearEntryCache();
	}

	public void removeCachedEntries() {
		assert this.tableStack.size() == 1;
		((RootTable) this.tableStack.peek()).removeCachedEntries();
	}

	private static class ChildTable implements SymbolTableOp {
		/**
		 * parent symbol table reference.
		 */
		private final SymbolTableOp parentTable;

		/**
		 * contain symbol entry.
		 * key is symbol name.
		 */
		private final Map<String, SymbolEntry> entryMap;

		private ChildTable(SymbolTableOp parentTable) {
			this.parentTable = parentTable;
			this.entryMap = new HashMap<>();
		}

		private SymbolTableOp getParentTable() {
			return this.parentTable;
		}

		@Override
		public SymbolEntry getEntry(String symbolName) {
			SymbolEntry entry = this.entryMap.get(symbolName);
			if(entry == null) {
				return this.getParentTable().getEntry(symbolName);
			}
			return entry;
		}

		@Override
		public boolean addEntry(String symbolName, DSType type, boolean isReadOnly) {
			if(this.entryMap.containsKey(symbolName)) {
				return false;
			}
			SymbolEntry entry = new SymbolEntry(type, isReadOnly, false);
			this.entryMap.put(symbolName, entry);
			return true;
		}
	}

	/**
	 * contains global variable symbol
	 * @author skgchxngsxyz-osx
	 *
	 */
	private static class RootTable implements SymbolTableOp {
		/**
		 * contain symbol entry.
		 * key is symbol name.
		 */
		private final Map<String, SymbolEntry> entryMap;

		private final List<String> entryCache;

		private RootTable() {
			this.entryMap = new HashMap<>();
			this.entryCache = new LinkedList<>();
		}

		@Override
		public SymbolEntry getEntry(String symbolName) {
			return this.entryMap.get(symbolName);
		}

		@Override
		public boolean addEntry(String symbolName, DSType type, boolean isReadOnly) {
			if(this.entryMap.containsKey(symbolName)) {
				return false;
			}
			SymbolEntry entry = new SymbolEntry(type, isReadOnly, true);
			this.entryMap.put(symbolName, entry);
			this.entryCache.add(symbolName);
			return true;
		}

		public void clearEntryCache() {
			this.entryCache.clear();
		}

		public void removeCachedEntries() {
			for(String entryName : this.entryCache) {
				this.entryMap.remove(entryName);
			}
		}
	}

	public static class SymbolEntry {
		/**
		 * represent read only symbol (constant variable, function).
		 */
		private final boolean isReadOnly;
		private final boolean isGlobal;
		private final DSType type;

		protected SymbolEntry(DSType type, boolean isReadOnly, boolean isGlobal) {
			this.type = type;
			this.isReadOnly = isReadOnly;
			this.isGlobal = isGlobal;
		}

		public DSType getType() {
			return this.type;
		}

		public boolean isReadOnly() {
			return this.isReadOnly;
		}

		public boolean isGlobal() {
			return this.isGlobal;
		}
	}
}
