package dshell.lang;

import java.util.Arrays;

import dshell.lang.annotation.ArrayOp;
import dshell.lang.annotation.ArrayOp.ArrayOpType;
import dshell.lang.annotation.Exportable;
import dshell.lang.annotation.GenericClass;
import dshell.lang.annotation.TypeParameter;

/**
 * Generic array for class Type value.
 * Can use it as a stack.
 * @author skgchxngsxyz-osx
 *
 */
@GenericClass
public class GenericArray {
	private final static int defaultArraySize = 16;

	/**
	 * contains array elements.
	 */
	private Object[] values;

	/**
	 * represents currently containing element size.
	 */
	private int size;

	public GenericArray(Object[] values) {
		this.size = values.length;
		this.values = new Object[this.size < defaultArraySize ? defaultArraySize : this.size];
		System.arraycopy(values, 0, this.values, 0, this.size);
	}

	/**
	 * called from clone()
	 */
	private GenericArray() {
	}

	private void throwIfIndexOutOfRange(long index) {
		if(index < 0 || index >= this.size()) {
			throw new OutOfIndexException("array size is " + this.size() + ", but index is " + index);
		}
	}

	private void expandIfNoFreeSpace() {
		if(this.size() == this.values.length) {
			Arrays.copyOf(this.values, this.values.length * 2);
		}
	}

	@Exportable
	public long size() {
		return this.size;
	}

	@Exportable
	public boolean isEmpty() {
		return this.size() == 0;
	}

	@Exportable
	public void clear() {
		this.size = 0;
	}

	@Exportable
	@ArrayOp(ArrayOpType.Getter)
	@TypeParameter()
	public Object get(long index) {
		this.throwIfIndexOutOfRange(index);
		return this.values[(int) index];
	}

	@Exportable
	@ArrayOp(ArrayOpType.Setter)
	public void set(long index, @TypeParameter() Object value) {
		this.throwIfIndexOutOfRange(index);
		this.values[(int) index] = value;
	}

	@Exportable
	public void add(@TypeParameter() Object value) {
		this.expandIfNoFreeSpace();
		this.values[this.size++] = value;
	}

	@Exportable
	public void insert(long index, @TypeParameter() Object value) {
		if(index == this.size()) {
			this.add(value);
			return;
		}
		this.throwIfIndexOutOfRange(index);
		this.expandIfNoFreeSpace();
		Object[] newValues = new Object[this.values.length];
		final int i = (int) index;
		System.arraycopy(this.values, 0, newValues, 0, i);
		newValues[i] = value;
		System.arraycopy(this.values, i, newValues, i + 1, this.size - i);
		this.values = newValues;
		this.size++;
	}

	@Exportable
	@TypeParameter()
	public Object remove(long index) {
		Object value = this.get(index);
		int i = (int) index;
		System.arraycopy(this.values, i + 1, this.values, i, this.size - 1);
		this.size--;
		return value;
	}

	@Exportable
	public void push(Object value) {
		this.add(value);
	}

	@Exportable
	@TypeParameter()
	public Object pop() {
		return this.values[--this.size];
	}

	@Exportable
	public GenericArray clone() {
		if(this.isEmpty()) {
			return new GenericArray(new Object[]{});
		}
		Object[] newValues = new Object[this.size];
		GenericArray clonedArray = new GenericArray();
		clonedArray.size = this.size;
		System.arraycopy(this.values, 0, newValues, 0, this.size);
		clonedArray.values = newValues;
		return clonedArray;
	}
}
