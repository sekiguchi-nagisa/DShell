package dshell.internal.console;

/**
 * definition of console operation.
 * @author skgchxngsxyz-osx
 *
 */
public abstract class AbstractConsole {
	/**
	 * represent current brace level
	 */
	protected int level;
	protected int lineNumber;

	/**
	 * get line number. line number start at 1.
	 * @return
	 */
	public int getLineNumber() {
		return this.lineNumber;
	}

	/**
	 * 
	 * @param line
	 */
	public void incrementLineNum(String line) {
		int size = line.length();
		int count = 1;
		for(int i = 0; i < size; i++) {
			char ch = line.charAt(i);
			if(ch == '\n') {
				count++;
			}
		}
		this.lineNumber += count;
	}

	/**
	 * read string from console.
	 * @return
	 * - return null, if End of IO or IO problem.
	 */
	public abstract String readLine();

	/**
	 * print prompt and read line.
	 * @param prompt
	 * @return
	 */
	protected abstract String readLine(String prompt);

	protected String readLineImpl(String prompt1, String prompt2) {
		StringBuilder lineBuilder = new StringBuilder();
		String line = this.readLine(prompt1);
		lineBuilder.append(line);
		this.level = 0;
		while(this.checkLineContinuation(line)) {
			line = this.readLine(prompt2);
			lineBuilder.append('\n');
			lineBuilder.append(line);
		}
		if(this.level < 0) {
			if(line == null) {
				return null;
			}
			System.out.println(" .. canceled");
			return "";
		}
		return lineBuilder.toString().trim();
	}

	protected boolean checkLineContinuation(String text) {
		if(text == null) {
			this.level = -1;
			return false;
		}
		boolean foundDoubleQuote = false;
		boolean foundSingleQuote = false;
		int size = text.length();
		for(int i = 0; i < size; i++) {
			char ch = text.charAt(i);
			if(!foundSingleQuote && !foundDoubleQuote) {
				switch(ch) {
				case '{':
				case '[':
				case '(':
					this.level++;
					break;
				case '}':
				case ']':
				case ')':
					this.level--;
					break;
				case '\'':
					foundSingleQuote = true;
					break;
				case '"':
					foundDoubleQuote = true;
					break;
				case '\\':
					if(i == size - 1) {
						return true;
					}
					break;
				}
			}
			else {
				switch(ch) {
				case '\\':
					i++;
					continue;
				case '\'':
					foundSingleQuote = !foundSingleQuote;
					break;
				case '"':
					foundDoubleQuote = !foundDoubleQuote;
					break;
				}
			}
		}
		return this.level > 0;
	}
}
