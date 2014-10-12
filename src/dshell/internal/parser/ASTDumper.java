package dshell.internal.parser;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.antlr.v4.runtime.Token;

import dshell.annotation.ObjectReference;
import dshell.internal.lib.Utils;
import dshell.internal.parser.Node.RootNode;

/**
 * dump ast as json like format.
 * @author skgchxngsxyz-osx
 *
 */
public class ASTDumper {
	/**
	 * contains json strings.
	 */
	private StringBuilder sBuilder;

	private int currentIndentLevel = 0;

	/**
	 * for file name generation
	 */
	private Random rnd = new Random();

	private int fileNamePrefix = -1;

	private final Map<Class<?>, ObjectHandler> handlerMap;

	private final ObjectHandler primitiveHandler;
	private final ObjectHandler defaultHandler;
	private final ObjectHandler referenceHandler;

	private ASTDumper() {
		this.primitiveHandler = new PrimitiveHandler();
		this.defaultHandler = new DefaultObjectHandler();
		this.referenceHandler = new ReferenceHandler();
		this.handlerMap = new HashMap<>();

		// set handler
		this.handlerMap.put(Node.class, new NodeHandler());
		this.handlerMap.put(List.class, new ListHandler());
		this.handlerMap.put(Token.class, new TokenHandler());
	}

	private ObjectHandler getHandler(Field field) {
		ObjectReference anno = field.getAnnotation(ObjectReference.class);
		if(anno != null) {
			return this.referenceHandler;
		}
		return this.getHanlder(field.getType());
	}

	private ObjectHandler getHanlder(Class<?> fieldClass) {
		if(fieldClass.isPrimitive()) {
			return this.primitiveHandler;
		}
		while(fieldClass != null) {
			ObjectHandler handler = this.handlerMap.get(fieldClass);
			if(handler != null) {
				return handler;
			}
			fieldClass = fieldClass.getSuperclass();
		}
		return defaultHandler;
	}

	/**
	 * convert ast to json string
	 * @param node
	 * - typed node
	 */
	public void convertToJson(RootNode node) {
		String fileName = "ast" + ++this.fileNamePrefix + "_" + this.rnd.nextInt() + ".txt";
		try(FileOutputStream output = new FileOutputStream(fileName)) {
			System.err.println("@@@ Dump AST " + fileName + " @@@");
			this.convertToJson(node, output, true);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	public void convertToJson(RootNode node, OutputStream output, boolean closeable) {
		this.sBuilder = new StringBuilder();
		this.currentIndentLevel = 0;
		this.getHanlder(RootNode.class).encode(node);
		try(OutputStreamWriter writer = new OutputStreamWriter(output)) {
			writer.write(this.sBuilder.toString());
			this.sBuilder = null;
			if(closeable) {
				writer.close();
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private String stringify(Object value) {	//FIXME: unicode escape
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append('"');
		String str = value.toString();
		final int size = str.length();
		for(int i = 0; i < size; i++) {
			char ch = str.charAt(i);
			switch(ch) {
			case '"':
			case '\\':
			case '/':
				sBuilder.append('\\');
				break;
			case '\b': ch = 'b'; sBuilder.append('\\'); break;
			case '\f': ch = 'f'; sBuilder.append('\\'); break;
			case '\n': ch = 'n'; sBuilder.append('\\'); break;
			case '\r': ch = 'r'; sBuilder.append('\\'); break;
			case '\t': ch = 't'; sBuilder.append('\\'); break;
			}
			sBuilder.append(ch);
		}
		sBuilder.append('"');
		return sBuilder.toString();
	}

	/**
	 * append indent
	 */
	private void appendIndent() {
		for(int i = 0; i < this.currentIndentLevel; i++) {
			sBuilder.append("  ");
		}
	}

	private void appendField(Object owner, Field field) {
		sBuilder.append(stringify(field.getName() + "@" + field.getType().getCanonicalName()));
		sBuilder.append(" : ");
		try {
			field.setAccessible(true);
			Object fieldValue = field.get(owner);
			this.getHandler(field).encode(fieldValue);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			Utils.fatal(1, e.getClass() + ":" + e.getMessage());
		}
	}

	private static ASTDumper INSTANCE = new ASTDumper();

	public static ASTDumper getInstance() {
		return INSTANCE;
	}

	private abstract class ObjectHandler {
		/**
		 * 
		 * @param value
		 * - may be null
		 */
		public final void encode(Object value) {
			if(value == null) {
				sBuilder.append(value);
				return;
			}
			this.encodeImpl(value);
		}

		/**
		 * 
		 * @param value
		 * - not null
		 */
		protected abstract void encodeImpl(Object value);
	}

	/**
	 * encode primitive value
	 * @author skgchxngsxyz-osx
	 *
	 */
	class PrimitiveHandler extends ObjectHandler {
		@Override
		protected void encodeImpl(Object value) {
			sBuilder.append(value);
		}
	}

	/**
	 * encode object
	 * @author skgchxngsxyz-osx
	 *
	 */
	class DefaultObjectHandler extends ObjectHandler {
		@Override
		protected void encodeImpl(Object value) {
			sBuilder.append(stringify(value));
		}
	}

	/**
	 * encode node
	 * @author skgchxngsxyz-osx
	 *
	 */
	class NodeHandler extends ObjectHandler {
		@Override
		protected void encodeImpl(Object value) {
			sBuilder.append('{');
			sBuilder.append('\n');
			currentIndentLevel++;

			// add node type
			appendIndent();
			sBuilder.append(stringify("@NodeType"));
			sBuilder.append(" : ");
			sBuilder.append(stringify(value.getClass().getSimpleName()));
			sBuilder.append(",\n");

			// add hash code
			appendIndent();
			sBuilder.append(stringify("@HashCode"));
			sBuilder.append(" : ");
			sBuilder.append(stringify(value.hashCode()));
			sBuilder.append(",\n");

			// encode fields
			List<Field> instanceFields = new ArrayList<>();
			Class<?> clazz = value.getClass();
			while(clazz != null) {
				Field[] fields = clazz.getDeclaredFields();
				for(Field field : fields) {
					if(!Modifier.isStatic(field.getModifiers())) {
						instanceFields.add(field);
					}
				}
				clazz = clazz.getSuperclass();
			}
			int size = instanceFields.size();
			for(int i = 0; i < size; i++) {
				appendIndent();
				appendField(value, instanceFields.get(i));
				if(i != size - 1) {
					sBuilder.append(',');
				}
				sBuilder.append('\n');
			}
			currentIndentLevel--;
			appendIndent();
			sBuilder.append('}');
		}
	}

	/**
	 * encode java.lang.List
	 * @author skgchxngsxyz-osx
	 *
	 */
	class ListHandler extends ObjectHandler {
		@Override
		protected void encodeImpl(Object value) {
			List<?> list = (List<?>) value;
			int size = list.size();
			if(size == 0) {
				sBuilder.append("[]");
				return;
			}
			sBuilder.append('[');
			sBuilder.append('\n');
			currentIndentLevel++;
			for(int i = 0; i < size; i++) {
				appendIndent();
				Object element = list.get(i);
				ObjectHandler handler = getHanlder(element.getClass());
				handler.encode(element);
				if(i != size - 1) {
					sBuilder.append(',');
				}
				sBuilder.append('\n');
			}
			currentIndentLevel--;
			appendIndent();
			sBuilder.append(']');
		}
	}

	/**
	 * encode annotated field. (ObjectReference)
	 * @author skgchxngsxyz-opensuse
	 *
	 */
	class ReferenceHandler extends ObjectHandler {
		@Override
		protected void encodeImpl(Object value) {
			sBuilder.append(stringify("ref->" + value.hashCode()));
		}
	}

	class TokenHandler extends ObjectHandler {
		@Override
		protected void encodeImpl(Object value) {
			sBuilder.append(stringify(((Token)value).getText()));
		}
	}
}
