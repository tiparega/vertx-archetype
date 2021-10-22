package ${package}.api;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ${JavaAID}Api {
	private Vertx vertx;

	public ${JavaAID}Api(Vertx vertx) {
		this.vertx = vertx;
	}

	public Router getRouter() {
		Router ${zJavaAIDVar}Api = Router.router(vertx);
		${zJavaAIDVar}Api.post().handler(BodyHandler.create());
		${zJavaAIDVar}Api.post("/changeThis").handler(this::action);

		return ${zJavaAIDVar}Api;
	}

	private void action(RoutingContext routingContext) {
		int parameter = routingContext.getBodyAsJson().getInteger("parameter");
		log.info("Returning " + parameter);

		HttpServerResponse response = routingContext.response();
		response.end(String.valueOf(parameter));
	}
}