package ${package}.config;

public interface Constants {
	public static final int HTTP_RESPONSE_OK= 200;
	public static final int HTTP_RESPONSE_CREATED= 201;
	public static final int HTTP_RESPONSE_NO_CONTENT= 204;
	public static final int HTTP_RESPONSE_BAD_REQUEST= 400;
	public static final int HTTP_RESPONSE_NOT_AUTHORIZED= 401;
	public static final int HTTP_RESPONSE_FORBIDDEN= 403;
	public static final int HTTP_RESPONSE_NOT_FOUND= 404;
	public static final int HTTP_RESPONSE_CONFLICT= 409;
	public static final int HTTP_RESPONSE_INTERNAL_SERVER_ERROR= 500;
	
	public static final String HEADER_CONTENT_TYPE = "Content-Type";
	public static final String MIME_JSON = "application/json";
	
	public static final String BASE_PROP="${artifactId}";
	public static final String PROP_SERVER_PORT = "server.port";
	public static final String PROP_APP_NAME = "spring.application.name";
}
