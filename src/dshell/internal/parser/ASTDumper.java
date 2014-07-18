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
import dshell.internal.parser.Node.ExprNode;
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

	private int fileNamePrefix = -1;

	private final Map<Class<?>, ObjectHandler> handlerMap;

	private final ObjectHandler primitiveHandler = new PrimitiveHandler();
	private final ObjectHandler defaultHandler = new DefaultObjectHandler();
	private final ObjectHandler referenceHandler = new ReferenceHandler();

	private ASTDumper() {
		this.handlerMap = new HashMap<>();

		// set handler
		NodeHandler nodeHandler = new NodeHandler();
		this.handlerMap.put(Node.class, nodeHandler);
		this.handlerMap.put(ExprNode.class, nodeHandler);
		this.handlerMap.put(String.class, new StringHandler());
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
		String fileName = "ast" + ++this.fileNamePrefix + "_" + new Random().nextInt() + ".txt";
		try(FileOutputStream output = new FileOutputStream(fileName)) {
			System.err.println("dump ast @@@ " + fileName + " @@@");
			this.convertToJson(node, output, true);
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	public void convertToJson(RootNode node, OutputStream output, boolean closable) {
		this.sBuilder = new StringBuilder();
		this.currentIndentLevel = 0;
		this.getHanlder(RootNode.class).encode(node);
		try(OutputStreamWriter writer = new OutputStreamWriter(output)) {
			writer.write(this.sBuilder.toString());
			this.sBuilder = null;
			if(closable) {
				writer.close();
			}
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	private String quote(Object value) {
		StringBuilder sBuilder = new StringBuilder();
		sBuilder.append('"');
		sBuilder.append(value);
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
		sBuilder.append(quote(field.getName() + "@" + field.getType().getCanonicalName()));
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
	 * encode string value
	 * @author skgchxngsxyz-osx
	 *
	 */
	class StringHandler extends ObjectHandler {
		@Override
		protected void encodeImpl(Object value) {
			sBuilder.append(quote(value));
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
			sBuilder.append(quote(value));
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
			sBuilder.append(quote("@NodeType"));
			sBuilder.append(" : ");
			sBuilder.append(quote(value.getClass().getSimpleName()));
			sBuilder.append(",\n");

			// add hash code
			appendIndent();
			sBuilder.append(quote("@HashCode"));
			sBuilder.append(" : ");
			sBuilder.append(quote(value.hashCode()));
			sBuilder.append(",\n");

			// encode fields
			List<Field> instanceFileds = new ArrayList<>();
			Class<?> clazz = value.getClass();
			while(clazz != null) {
				Field[] fields = clazz.getDeclaredFields();
				for(Field field : fields) {
					if(!Modifier.isStatic(field.getModifiers())) {
						instanceFileds.add(field);
					}
				}
				clazz = clazz.getSuperclass();
			}
			int size = instanceFileds.size();
			for(int i = 0; i < size; i++) {
				appendIndent();
				appendField(value, instanceFileds.get(i));
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
			sBuilder.append(quote("ref->" + value.hashCode()));
		}
	}

	class TokenHandler extends ObjectHandler {
		@Override
		protected void encodeImpl(Object value) {
			sBuilder.append(quote(((Token)value).getText()));
		}
	}
}
