package ${package}.api;

import ${package}.config.Constants;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.openapi.RouterBuilderOptions;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ${JavaAID}Api {
	private Vertx vertx;

	public ${JavaAID}Api(Vertx vertx) {
		this.vertx = vertx;
	}

	public void getRouter(Handler<AsyncResult<Router>> handle) {
		RouterBuilder.create(vertx, "api.yaml").onSuccess(routerBuilder -> {
			routerBuilder.setOptions(new RouterBuilderOptions().setRequireSecurityHandlers(false)); // TODO: AUTH
			routerBuilder.operation("getPetById").handler(this::action).failureHandler(this::error);
			handle.handle(Future.succeededFuture(routerBuilder.createRouter()));
		}).onFailure(err -> {
			handle.handle(Future.failedFuture(err));
		});
	}

	private void action(RoutingContext routingContext) {
		HttpServerResponse response = routingContext.response();
		response.end(new JsonObject().put("petId", routingContext.pathParam("petId")).encode());
	}

	private void error(RoutingContext routingContext) {
		log.info("Request error: " + routingContext.failure());

		HttpServerResponse response = routingContext.response();
		response.setStatusCode(Constants.HTTP_RESPONSE_BAD_REQUEST).putHeader("Content-Type", "text/html")
				.end("<h1>" + Constants.HTTP_RESPONSE_BAD_REQUEST + " Bad request</h1><br />"
						+ routingContext.failure().getLocalizedMessage());
	}
}