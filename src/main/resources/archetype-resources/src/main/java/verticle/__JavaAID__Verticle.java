package ${package}.verticle;

import java.time.Instant;

import ${package}.api.${JavaAID}Api;
import ${package}.config.ConfigUtil;
import ${package}.config.Constants;
import ${package}.config.HealthCheck;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.micrometer.PrometheusScrapingHandler;
import io.vertx.micrometer.backends.BackendRegistries;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ${JavaAID}Verticle extends AbstractVerticle {
	private int serverPort;

	@Override
	public void start() {
		Instant start= Instant.now();
		#if (${springConfigServer} == 'true' || ${springConfigServer} == 'yes' || ${springConfigServer} == 'y')
		ConfigUtil.readSpringConfig(vertx, configHandler -> {
		#else
		ConfigUtil.readConfig(vertx, configHandler -> {
		#end
			if (configHandler.succeeded()) {
				ConfigUtil config= configHandler.result();
		
				HttpServerOptions options = new HttpServerOptions().setLogActivity(true);
				HttpServer server = vertx.createHttpServer(options);
		
				Router router = Router.router(vertx);
				
				router.route("/api/*")
					//.consumes("application/json")
					.produces("application/json")
					.subRouter(new ${JavaAID}Api(vertx).getRouter());
				router.route("/alive").handler(HealthCheck.createLiveness(vertx));
				router.route("/health").handler(HealthCheck.createReadyness(vertx));
				router.route("/metrics").handler(PrometheusScrapingHandler.create());
				
				MeterRegistry registry = BackendRegistries.getDefaultNow();
				new JvmMemoryMetrics().bindTo(registry);
				new JvmThreadMetrics().bindTo(registry);
				
				router.route().handler(routingContext -> {
					routingContext.fail(Constants.HTTP_RESPONSE_NOT_FOUND);
				});
		
				server.requestHandler(router)
					.exceptionHandler(e -> {
						log.error("Unhandled error", e);
					})
				.listen(config.getInteger(Constants.PROP_SERVER_PORT), listening -> {
					serverPort= server.actualPort();
					log.info("Server started on port " + serverPort + " in " + (Instant.now().toEpochMilli() - start.toEpochMilli())  + " milliseconds");
				});
			} else {
				log.error("Error retrieving configuration", configHandler.cause());
			}
		});
	}
	
	@Override
	public void stop() {
	}
}