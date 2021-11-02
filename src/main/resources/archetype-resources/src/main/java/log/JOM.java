package ${package}.log;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.message.MultiformatMessage;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

/**
 * Handles messages that contain an Object as JSON.
 * JOM short of JsonObjectMessage to avoid long lines
 * @author alex
 */
public class JOM implements MultiformatMessage {
	private static final long serialVersionUID = 8178965523626519117L;
	private static final String[] FORMATS = {"JSON"};
	private transient Object[] obj;
    private transient String objectString;
    private transient String message;
    
    /**
     * Short of Tuple
     * @author alex
     *
     */
    public static final class Tuple {
    	public Tuple(String key, Object value) {
			this.key = key;
			this.value = value;
		}
		private String key;
    	private Object value;
    }
    
    /**
     * Creates a new Tuple. Shorter than <code>new Tuple(key,value)</code>
     * @param key
     * @param value
     * @return
     */
    public static final Tuple T(String key, Object value) {
    	return new Tuple(key,value);
    }

    /**
     * Creates the ObjectMessage.
     *
     * @param obj The Object to format.
     */
    public JOM(final Object obj) {
    	this.message= null;
        this.obj = obj == null ? new Object[]{"null"} : new Object[]{obj};
    }
    
    /**
     * Creates the ObjectMessage.
     *
     * @param obj The Object to format.
     */
    public JOM(final String message, final Object obj) {
    	this.message= message;
        this.obj = obj == null ? new Object[]{"null"} : new Object[] {obj};
    }
    
    /**
     * Creates the ObjectMessage.
     *
     * @param obj The Object to format.
     */
    public JOM(final String message, final Object... obj) {
    	this.message= message;
        this.obj = obj == null ? new Object[]{"null"} : obj;
    }

    /**
     * Returns the formatted object message.
     *
     * @return the formatted object message.
     */
    @Override
    public String getFormattedMessage() {
    	// Safe to call Json.encode if object is a JsonObject
        // LOG4J2-763: cache formatted string in case obj changes later
        if (objectString == null) {
        	if (obj.length==1 && message == null) {
        		objectString= Json.encode(obj[0]);
        	} else {
	        	final StringBuilder sb= new StringBuilder("{");
	        	
	        	List<Object> objects= new ArrayList<>();
	        	
	        	if (message != null) {
	        		sb.append("\"message\":\"").append(message).append("\",");
	        	}
	        	boolean atLeastOne= false;
	        	for (Object o: obj) {
	        		if (o instanceof Tuple) {
	        			atLeastOne= true;
	        			final Tuple t= (Tuple) o;
	        			sb.append("\"" + t.key).append("\":").append(Json.encode(t.value)).append(",");
	        		} else {
	        			objects.add(o);
	        		}
	        	}
	        	
	        	if (objects.size() > 0) {
	        		sb.append("\"additional_data\":[");
	        		sb.append(Json.encode(objects.get(0)));
	        		for (int i=1; i<objects.size(); i++) {
	        			sb.append(',').append(Json.encode(objects.get(i)));
	        		}
	        		sb.append(']');
	        	} else if (atLeastOne) {
		        	sb.deleteCharAt(sb.length()-1);
	        	}
	        	sb.append('}');
	        	objectString= sb.toString();
        	}
        }
        return objectString;
    }

    /**
     * Returns the object formatted using its toString method.
     *
     * @return the String representation of the object.
     */
    @Override
    public String getFormat() {
        return getFormattedMessage();
    }

    /**
     * Returns the object parameter.
     *
     * @return The object.
     * @since 2.7
     */
    public Object getParameter() {
        return obj;
    }

    /**
     * Returns the object as if it were a parameter.
     *
     * @return The object.
     */
    @Override
    public Object[] getParameters() {
        return new Object[] {obj};
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final JOM that = (JOM) o;
        return obj == null ? that.obj == null : equalObjectsOrStrings(obj, that.obj);
    }

    private boolean equalObjectsOrStrings(final Object left, final Object right) {
    	//No need to map here
        return left.equals(right) || String.valueOf(left).equals(String.valueOf(right));
    }

    @Override
    public int hashCode() {
        return obj != null ? obj.hashCode() : 0;
    }

    @Override
    public String toString() {
        return getFormattedMessage();
    }

    /**
     * Gets the message if it is a throwable.
     *
     * @return the message if it is a throwable.
     */
    @Override
    public Throwable getThrowable() {
        return obj[obj.length-1] instanceof Throwable ? (Throwable) obj[obj.length-1] : null;
    }

	@Override
	public String getFormattedMessage(String[] formats) {
		return getFormattedMessage();
	}

	@Override
	public String[] getFormats() {
		return FORMATS;
	}
}
