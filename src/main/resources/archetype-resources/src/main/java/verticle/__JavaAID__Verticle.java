package ${package}.verticle;

import java.time.Instant;

import com.tiparega.hyperqueue.queuehandler.log.JOM;

import ${package}.api.${JavaAID}Api;
import ${package}.config.ConfigUtil;
import ${package}.config.Constants;
import ${package}.config.HealthCheck;
import ${package}.log.JOM;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.micrometer.PrometheusScrapingHandler;
import io.vertx.micrometer.backends.BackendRegistries;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class $ {
	JavaAID}Verticle extends AbstractVerticle{
	private int serverPort;

	@Override
	public void start() {
		Instant start = Instant.now();
#if (${springConfigServer} == 'true' || ${springConfigServer} == 'yes' || ${springConfigServer} == 'y')
		ConfigUtil.readSpringConfig(vertx, configHandler -> {
#else
		ConfigUtil.readConfig(vertx).subscribe(config -> {
#end
			HttpServerOptions options = new HttpServerOptions().setLogActivity(true);
			HttpServer server = vertx.createHttpServer(options);

			Router router = Router.router(vertx);
			new ${JavaAID}Api(vertx).getRouter().subscribe(apiRouter -> {
				router.route("/api/*")
						// .consumes("application/json")
						.produces("application/json").subRouter(apiRouter);
				router.route("/alive").handler(HealthCheck.createLiveness(vertx));
				router.route("/health").handler(HealthCheck.createReadyness(vertx));
				router.route("/metrics").handler(PrometheusScrapingHandler.create());

				MeterRegistry registry = BackendRegistries.getDefaultNow();
				new JvmMemoryMetrics().bindTo(registry);
				new JvmThreadMetrics().bindTo(registry);

				router.route().handler(routingContext -> {
					routingContext.fail(Constants.HTTP_RESPONSE_NOT_FOUND);
				});

				server.requestHandler(router).exceptionHandler(e -> {
					log.error("Unhandled error", e);
				}).rxListen(config.getInteger(Constants.PROP_SERVER_PORT)).subscribe(listening -> {
					serverPort = server.actualPort();
					log.info(new JOM("Server started", JOM.T("port", serverPort),
							JOM.T("millisToStart", Instant.now().toEpochMilli() - start.toEpochMilli())));
				}, err -> {
					log.error("Error starting server" + err);
				});
			}, err -> {
				log.error("Error configuring API", err);
			});
		}, err -> {
			log.error("Error retrieving configuration", err);
		});
	}

	@Override
	public void stop() {
	}
}