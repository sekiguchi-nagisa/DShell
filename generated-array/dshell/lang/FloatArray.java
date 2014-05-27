// auto generated source file. do not edit me.
package dshell.lang;

import java.util.Arrays;

import dshell.lang.annotation.ArrayOp;
import dshell.lang.annotation.ArrayOp.ArrayOpType;
import dshell.lang.annotation.Exportable;

/**
 * Float array for class Type value.
 * Can use it as a stack.
 * @author skgchxngsxyz-osx
 *
 */
public class FloatArray {
	private final static int defaultArraySize = 16;

	/**
	 * contains array elements.
	 */
	private double[] values;

	/**
	 * represents currently containing element size.
	 */
	private int size;

	public FloatArray(double[] values) {
		this.size = values.length;
		this.values = new double[this.size < defaultArraySize ? defaultArraySize : this.size];
		System.arraycopy(values, 0, this.values, 0, this.size);
	}

	/**
	 * called from clone()
	 */
	private FloatArray() {
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
	
	public double get(long index) {
		this.throwIfIndexOutOfRange(index);
		return this.values[(int) index];
	}

	@Exportable
	@ArrayOp(ArrayOpType.Setter)
	public void set(long index,  double value) {
		this.throwIfIndexOutOfRange(index);
		this.values[(int) index] = value;
	}

	@Exportable
	public void add( double value) {
		this.expandIfNoFreeSpace();
		this.values[this.size++] = value;
	}

	@Exportable
	public void insert(long index,  double value) {
		if(index == this.size()) {
			this.add(value);
			return;
		}
		this.throwIfIndexOutOfRange(index);
		this.expandIfNoFreeSpace();
		double[] newValues = new double[this.values.length];
		final int i = (int) index;
		System.arraycopy(this.values, 0, newValues, 0, i);
		newValues[i] = value;
		System.arraycopy(this.values, i, newValues, i + 1, this.size - i);
		this.values = newValues;
		this.size++;
	}

	@Exportable
	
	public double remove(long index) {
		double value = this.get(index);
		int i = (int) index;
		System.arraycopy(this.values, i + 1, this.values, i, this.size - 1);
		this.size--;
		return value;
	}

	@Exportable
	public void push(double value) {
		this.add(value);
	}

	@Exportable
	
	public double pop() {
		return this.values[--this.size];
	}

	@Exportable
	public FloatArray clone() {
		if(this.isEmpty()) {
			return new FloatArray(new double[]{});
		}
		double[] newValues = new double[this.size];
		FloatArray clonedArray = new FloatArray();
		clonedArray.size = this.size;
		System.arraycopy(this.values, 0, newValues, 0, this.size);
		clonedArray.values = newValues;
		return clonedArray;
	}
}
