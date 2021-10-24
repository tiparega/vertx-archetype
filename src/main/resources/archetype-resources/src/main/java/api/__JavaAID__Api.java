package ${package}.api;

#if (${openApi} == 'true' || ${openApi} == 'yes' || ${openApi} == 'y')
import ${package}.api.model.Category;
import ${package}.api.model.Pet;
#end
import ${package}.config.Constants;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.vertx.core.json.Json;
#if (${openApi} != 'true' && ${openApi} != 'yes' && ${openApi} != 'y')
import io.vertx.core.json.JsonObject;
#end
import io.vertx.ext.web.openapi.RouterBuilderOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.openapi.RouterBuilder;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ${JavaAID}Api {
	private Vertx vertx;

	public ${JavaAID}Api(Vertx vertx) {
		this.vertx = vertx;
	}

	public Single<Router> getRouter() {
		${JavaAID}Api api= this;
		return Single.<Router>create(new SingleOnSubscribe<Router>() {

			@Override
			public void subscribe(SingleEmitter<Router> emitter) throws Exception {
				RouterBuilder.rxCreate(vertx, "api.yaml").subscribe(routerBuilder -> {
					routerBuilder.setOptions(new RouterBuilderOptions().setRequireSecurityHandlers(false)); // TODO: AUTH
					routerBuilder.operation("getPetById").handler(api::action).failureHandler(api::error);
					emitter.onSuccess(routerBuilder.createRouter());
				},err -> {
					emitter.onError(err);
				});
			}
		});
	}

	private void action(RoutingContext routingContext) {
		HttpServerResponse response = routingContext.response();
#if (${openApi} == 'true' || ${openApi} == 'yes' || ${openApi} == 'y')
		Pet pet = new Pet().id(Long.valueOf(routingContext.pathParam("petId"))).name("Test")
				.category(new Category().id(1L).name("Pojo"));
		response.end(Json.encode(pet));
#else
		response.end(new JsonObject().put("petId", routingContext.pathParam("petId")).encode());
#end
	}

	private void error(RoutingContext routingContext) {
		log.info("Request error: " + routingContext.failure());

		HttpServerResponse response = routingContext.response();
		response.setStatusCode(Constants.HTTP_RESPONSE_BAD_REQUEST).putHeader("Content-Type", "text/html")
				.end("<h1>" + Constants.HTTP_RESPONSE_BAD_REQUEST + " Bad request</h1><br />"
						+ routingContext.failure().getLocalizedMessage());
	}
}