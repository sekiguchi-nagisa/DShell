package dshell.internal.lib;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import dshell.lang.GenericPair;

/**
 * contain global variable.
 * @author skgchxngsxyz-osx
 *
 */
public class GlobalVariableTable {
	private final static int MAX_INDEX = Integer.MAX_VALUE;
	private final static int defaultTableSize = 16;

	/**
	 * global variable table for long value.
	 */
	public static long[]    longVarTable;

	/**
	 * global variable table for double value.
	 */
	public static double[]  doubleVarTable;

	/**
	 * global variable table for boolean value.
	 */
	public static boolean[] booleanVarTable;

	/**
	 * global variable table for object value.
	 */
	public static Object[]  objectVarTable;

	private static int longVarIndexCount    = 0;
	private static int doubleVarIndexCount  = 0;
	private static int booleanVarIndexCount = 0;
	private static int objectVarIndexCount  = 0;

	private static Map<String, GenericPair<Integer, Class<?>>> indexMap = new HashMap<>();

	private static Deque<Integer> unusedLongIndexStack    = new ArrayDeque<>();
	private static Deque<Integer> unusedDoubleIndexStack  = new ArrayDeque<>();
	private static Deque<Integer> unusedBooleanIndexStack = new ArrayDeque<>();
	private static Deque<Integer> unusedObjectIndexStack  = new ArrayDeque<>();

	static {
		longVarTable = new long[defaultTableSize];
		doubleVarTable = new double[defaultTableSize];
		booleanVarTable = new boolean[defaultTableSize];
		objectVarTable = new Object[defaultTableSize];
	}

	// create new global variable entry
	public static int newLongVarEntry(String varName) {
		checkDuplicatedEntry(varName);
		if(!unusedLongIndexStack.isEmpty()) {
			return unusedLongIndexStack.pop();
		}

		final int size = longVarTable.length;
		if(longVarIndexCount == size) {
			checkIndexRange(size);
			longVarTable = Arrays.copyOf(longVarTable, size * 2);
		}
		final int varIndex = longVarIndexCount++;
		indexMap.put(varName, new GenericPair<Integer, Class<?>>(varIndex, long.class));
		return varIndex;
	}

	public static int newDoubleVarEntry(String varName) {
		checkDuplicatedEntry(varName);
		if(!unusedDoubleIndexStack.isEmpty()) {
			return unusedDoubleIndexStack.pop();
		}

		final int size = doubleVarTable.length;
		if(doubleVarIndexCount == size) {
			checkIndexRange(size);
			doubleVarTable = Arrays.copyOf(doubleVarTable, size * 2);
		}
		final int varIndex = doubleVarIndexCount++;
		indexMap.put(varName, new GenericPair<Integer, Class<?>>(varIndex, double.class));
		return varIndex;
	}

	public static int newBooleanVarEntry(String varName) {
		checkDuplicatedEntry(varName);
		if(!unusedBooleanIndexStack.isEmpty()) {
			return unusedBooleanIndexStack.pop();
		}

		final int size = booleanVarTable.length;
		if(booleanVarIndexCount == size) {
			checkIndexRange(size);
			booleanVarTable = Arrays.copyOf(booleanVarTable, size * 2);
		}
		final int varIndex = booleanVarIndexCount++;
		indexMap.put(varName, new GenericPair<Integer, Class<?>>(varIndex, boolean.class));
		return varIndex;
	}

	public static int newObjectVarEntry(String varName) {
		checkDuplicatedEntry(varName);
		if(!unusedObjectIndexStack.isEmpty()) {
			return unusedObjectIndexStack.pop();
		}

		final int size = objectVarTable.length;
		if(objectVarIndexCount == size) {
			checkIndexRange(size);
			objectVarTable = Arrays.copyOf(objectVarTable, size * 2);
		}
		final int varIndex = objectVarIndexCount++;
		indexMap.put(varName, new GenericPair<Integer, Class<?>>(varIndex, Object.class));
		return varIndex;
	}

	private static void checkDuplicatedEntry(String varName) {
		GenericPair<Integer, Class<?>> pair = indexMap.get(varName);
		if(pair != null) {
			// find duplicated entry and push to unused index stack
			int index = pair.getLeft();
			Class<?> clazz = pair.getRight();
			if(clazz == long.class) {
				unusedLongIndexStack.push(index);
			} else if(clazz == double.class) {
				unusedDoubleIndexStack.push(index);
			} else if(clazz == boolean.class) {
				unusedBooleanIndexStack.push(index);
			} else if(clazz == Object.class) {
				unusedObjectIndexStack.push(index);
			} else {
				Utils.fatal(1, "illegal class: " + clazz);
			}
		}
	}

	/**
	 * force terminate if index out of max integer
	 * @param size
	 */
	private static void checkIndexRange(int size) {
		if(size >= MAX_INDEX) {
			Utils.fatal(1, "too many global variable");
		}
	}

	private static GenericPair<Integer, Class<?>> getIndexPair(String varName) {
		GenericPair<Integer, Class<?>> indexPair = indexMap.get(varName);
		if(indexPair == null) {
			Utils.fatal(1, "undefined global variable: " + varName);
		}
		return indexPair;
	}
	/**
	 * get var index
	 * @param varName
	 * @return
	 * - force terminate if has no var entry
	 */
	public static int getVarIndex(String varName) {
		return getIndexPair(varName).getLeft();
	}

	private static void checkVarClass(GenericPair<Integer, Class<?>> indexPair, Class<?> targetClass) {
		if(!indexPair.getRight().equals(targetClass)) {
			Utils.fatal(1, "require " + targetClass + ", but is " + indexPair.getRight());
		}
	}

	// get global variable
	public static long getLongVar(String varName) {
		GenericPair<Integer, Class<?>> indexPair = getIndexPair(varName);
		checkVarClass(indexPair, long.class);
		return longVarTable[indexPair.getLeft()];
	}

	public static double getDoubleVar(String varName) {
		GenericPair<Integer, Class<?>> indexPair = getIndexPair(varName);
		checkVarClass(indexPair, double.class);
		return doubleVarTable[indexPair.getLeft()];
	}
	
	public static boolean getBooleanVar(String varName) {
		GenericPair<Integer, Class<?>> indexPair = getIndexPair(varName);
		checkVarClass(indexPair, boolean.class);
		return booleanVarTable[indexPair.getLeft()];
	}

	@SuppressWarnings("unchecked")
	public static <T> T getObjectVar(String varName, Class<T> clazz) {
		GenericPair<Integer, Class<?>> indexPair = getIndexPair(varName);
		checkVarClass(indexPair, Object.class);
		return (T) objectVarTable[indexPair.getLeft()];
	}

	// add new global variable
	public static void addNewLongVar(String varName, long value) {
		longVarTable[newLongVarEntry(varName)] = value;
	}

	public static void addNewDoubleVar(String varName, double value) {
		doubleVarTable[newDoubleVarEntry(varName)] = value;
	}

	public static void addNewBooleanVar(String varName, boolean value) {
		booleanVarTable[newBooleanVarEntry(varName)] = value;
	}

	public static void addNewObjectVar(String varName, Object value) {
		objectVarTable[newObjectVarEntry(varName)] = value;
	}

	public static boolean checkVarExistence(String varName) {
		return indexMap.containsKey(varName);
	}
}
