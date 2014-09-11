package dshell.internal.lib;

/**
 * for string interpolation
 * @author skgchxngsxyz-opensuse
 *
 */
public class StringContext {
	private final StringBuilder sBuilder = new StringBuilder();

	public static StringContext newContext() {
		return new StringContext();
	}

	public StringContext append(String value) {
		this.sBuilder.append(value);
		return this;
	}

	@Override
	public String toString() {
		return this.sBuilder.toString();
	}
}
