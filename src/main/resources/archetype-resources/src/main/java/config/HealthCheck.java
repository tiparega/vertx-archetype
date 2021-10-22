package ${package}.config;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;

public class HealthCheck {
	private static HealthCheck liveness;
	private static HealthCheck readiness;
	
	private HealthCheck() {
	}

	public static HealthCheckHandler createLiveness(Vertx vertx) {
		liveness= new HealthCheck();
		HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(vertx);
	
		healthCheckHandler.register("basic", liveness::basic);
		
		return healthCheckHandler;
	}

	public static HealthCheckHandler createReadyness(Vertx vertx) {
		readiness= new HealthCheck();
		HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(vertx);
	
		healthCheckHandler.register("basic", readiness::basic);
		
		return healthCheckHandler;
	}
	
	private void basic(Promise<Status> promise) {
		promise.complete(Status.OK());
	}
}
