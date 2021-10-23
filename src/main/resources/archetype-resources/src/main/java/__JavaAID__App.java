package ${package};

import ${package}.verticle.${JavaAID}Verticle;
import io.vertx.reactivex.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.micrometer.VertxPrometheusOptions;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ${JavaAID}App {
	public static void main(String[] args) {
		log.info("Starting vertx");
		Vertx vertx= Vertx.vertx(new VertxOptions().setMetricsOptions(
				new MicrometerMetricsOptions()
					.setPrometheusOptions(new VertxPrometheusOptions().setEnabled(true))
					.setEnabled(true)));
		${JavaAID}Verticle a= new ${JavaAID}Verticle();
		vertx.deployVerticle(a);
	}
}