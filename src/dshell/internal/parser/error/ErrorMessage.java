package dshell.internal.parser.error;

import java.io.Serializable;

import org.antlr.v4.runtime.Token;

import dshell.internal.parser.SourceStream;

/**
 * represent for dshell error parse or type message
 * @author skgchxngsxyz-osx
 *
 */
public class ErrorMessage implements Serializable {
	private static final long serialVersionUID = 3138133432359489660L;

	public final static int SYNTAX_ERROR = 0;
	public final static int TYPE_ERROR   = 1;

	/**
	 * may be null
	 */
	private String fileName;
	private int lineNum = -1;
	private int pos = -1;

	/**
	 * 0 or 1
	 */
	private int errorType;

	/**
	 * may be null
	 */
	private String message;

	/**
	 * may be null
	 */
	private String lineText;

	/**
	 * may be null
	 */
	private String lineMarker;

	/**
	 * 
	 * @param fileName
	 * may be null
	 * @param lineNum
	 * @param pos
	 * @return
	 * this
	 */
	public ErrorMessage setErrorLocation(String fileName, int lineNum, int pos) {
		this.fileName = fileName;
		this.lineNum = lineNum;
		this.pos = pos;
		return this;
	}

	/**
	 * 
	 * @param token
	 * may be null
	 * @return
	 * this
	 */
	public ErrorMessage setErrorLocation(Token token) {
		if(token == null) {
			return this;
		}
		SourceStream input = (SourceStream) token.getInputStream();
		return this.setErrorLocation(token.getTokenSource().getSourceName(), 
				token.getLine(), token.getCharPositionInLine() + input.getOffset());
	}

	/**
	 * 
	 * @param errorType
	 * @return
	 * this
	 * @throws IllegalArgumentException
	 * if error type is invalid
	 */
	public ErrorMessage setErrorType(int errorType) throws IllegalArgumentException {
		switch(errorType) {
		case SYNTAX_ERROR:
		case TYPE_ERROR:
			this.errorType = errorType;
			return this;
		}
		throw new IllegalArgumentException("illegal error type: " + errorType);
	}

	/**
	 * 
	 * @param message
	 * may be null
	 * @return
	 * this
	 */
	public ErrorMessage setMessage(String message) {
		this.message = message;
		return this;
	}

	public ErrorMessage setLineText(String lineText) {
		this.lineText = lineText;
		return this;
	}

	/**
	 * 
	 * @param lineMarker
	 * may be null
	 * @return
	 * this
	 */
	public ErrorMessage setLineMarker(String lineMarker) {
		this.lineMarker = lineMarker;
		return this;
	}

	/**
	 * format message
	 */
	@Override
	public String toString() {
		StringBuilder sBuilder = new StringBuilder();

		// append error location
		if(this.fileName == null) {
			sBuilder.append("??");
		} else {
			sBuilder.append(this.fileName);
		}
		sBuilder.append(':');
		sBuilder.append(this.lineNum);
		sBuilder.append(':');
		sBuilder.append(this.pos);
		sBuilder.append(':');

		// append error type
		switch(this.errorType) {
		case SYNTAX_ERROR:
			sBuilder.append(" [SyntaxError] ");
			break;
		case TYPE_ERROR:
			sBuilder.append(" [TypeError] ");
			break;
		default:
			throw new IllegalArgumentException("illegal error type: " + errorType);
		}

		// append message
		if(this.message != null) {
			sBuilder.append(this.message);
		}

		// append line text
		if(this.lineText != null) {
			sBuilder.append('\n');
			sBuilder.append(this.lineText);
		}

		// append line marker
		if(this.lineMarker != null) {
			sBuilder.append('\n');
			sBuilder.append(this.lineMarker);
		}
		return sBuilder.toString();
	}

	/**
	 * compare the result of toString()
	 * @param target
	 * not null
	 * @return
	 */
	public boolean equals(Object target) {
		if(target instanceof ErrorMessage) {
			return this.toString().equals(((ErrorMessage)target).toString());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}
}
