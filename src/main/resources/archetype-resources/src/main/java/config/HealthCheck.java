package ${package}.config;

import io.vertx.reactivex.core.Promise;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.healthchecks.HealthCheckHandler;
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
