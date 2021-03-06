package ${package}.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import io.reactivex.Single;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.vertx.config.ConfigChange;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.Vertx;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ConfigUtil {
	private static ConfigUtil instance;
	private static final long UPDATE_TIME = 15000L;
	private JsonObject json;
	private Map<String, List<Handler<? extends Object>>> listeners; //TODO: Change to Observable/Maybe/Completable
	private Map<Handler<? extends Object>, Object> defaults;
	private ConfigUtil baseConf;

	private ConfigUtil(JsonObject json) {
		this.json= json;
		listeners= new HashMap<>();
		defaults= new HashMap<>();
	}

	public ConfigUtil(ConfigUtil baseConf, JsonObject springConfig) {
		this(springConfig);
		this.baseConf= baseConf;
	}

	public static Single<ConfigUtil> readConfig(Vertx vertx) {
		return Single.<ConfigUtil>create(new SingleOnSubscribe<ConfigUtil>() {

			@Override
			public void subscribe(SingleEmitter<ConfigUtil> emitter) throws Exception {
				ConfigRetrieverOptions options = new ConfigRetrieverOptions().setScanPeriod(UPDATE_TIME);
				getLocalStores().forEach(options::addStore);
				ConfigRetriever retriever = ConfigRetriever.create(vertx, options);
				retriever.rxGetConfig().subscribe(retrieved -> {
					ConfigUtil configUtil = new ConfigUtil(retrieved);
					instance = configUtil;
					retriever.listen(configUtil::onChange);
					emitter.onSuccess(configUtil);
				}, err -> {
					emitter.onError(err);
				});
			}
		});
	}

	private static List<ConfigStoreOptions> getLocalStores() {
		List<ConfigStoreOptions> stores= new ArrayList<>();
		stores.add(new ConfigStoreOptions()
				.setType("file")
				.setFormat("yaml")
				.setOptional(true)
				.setConfig(new JsonObject()
						.put("path", "conf/config.yaml")
				));
		stores.add(new ConfigStoreOptions()
				.setType("file")
				.setFormat("yaml")
				.setOptional(true)
				.setConfig(new JsonObject()
						.put("path", "conf/config.yml")
				));
		stores.add(new ConfigStoreOptions()
				.setType("file")
				.setFormat("properties")
				.setOptional(true)
				.setConfig(new JsonObject()
						.put("path", "/conf/config.properties")
				));
		stores.add(new ConfigStoreOptions()
				.setType("file")
				.setFormat("yaml")
				.setOptional(true)
				.setConfig(new JsonObject()
						.put("path", "/conf/config-" + Constants.BASE_PROP + ".yaml")
				));
		stores.add(new ConfigStoreOptions()
				.setType("file")
				.setFormat("yaml")
				.setOptional(true)
				.setConfig(new JsonObject()
						.put("path", "/conf/config-" + Constants.BASE_PROP + ".yml")
				));
		stores.add(new ConfigStoreOptions()
				.setType("file")
				.setFormat("properties")
				.setOptional(true)
				.setConfig(new JsonObject()
						.put("path", "/conf/config-" + Constants.BASE_PROP + ".properties")
				));
		stores.add(new ConfigStoreOptions().setType("sys"));
		stores.add(new ConfigStoreOptions().setType("env"));
		
		return stores;
	}

	/**
	 * Reads configuration from Spring Config Server.
	 * Local configuration is read only to get Spring Config Server properties, if found, local configuration
	 * is then removed.
	 * @param vertx
	 * @param handler
	 */
	public static Single<ConfigUtil> readSpringConfig(Vertx vertx) {

		return Single.<ConfigUtil>create(new SingleOnSubscribe<ConfigUtil>() {

			@Override
			public void subscribe(SingleEmitter<ConfigUtil> emitter) throws Exception {
				// First read local config
				readConfig(vertx).subscribe(localConfHandler -> {
					ConfigUtil localConf = localConfHandler;
					String name = localConf.getString("spring.application.name");
					String uri = localConf.getString("spring.cloud.config.uri");
					String profile = localConf.getString("spring.profiles.active");
					if (profile != null) {
						profile = profile.split(",")[0];
					}
					String fullUri = uri + "/" + name + "/" + profile;
					if (uri == null || name == null || profile == null) {
						// Fallback
						log.error("Can't load Spring Config from " + fullUri);
						emitter.onSuccess(localConf);
					} else {
						// If we have an uri, we call Spring Config Server
						log.info("Retrieving Spring Config from " + fullUri);
						ConfigStoreOptions springConfigStore = new ConfigStoreOptions().setType("spring-config-server")
								.setConfig(new JsonObject().put("url", fullUri));
						ConfigRetrieverOptions options = new ConfigRetrieverOptions().setScanPeriod(UPDATE_TIME)
								.addStore(springConfigStore);
						ConfigRetriever retriever = ConfigRetriever.create(vertx, options);
						retriever.rxGetConfig().subscribe(springConfig -> {
							springConfig = propertiesToJson(springConfig);
							// Create new ConfigUtil with local as base
							ConfigUtil configUtil = new ConfigUtil(localConf, springConfig);
							resolvePlaceHolders(configUtil);
							instance = configUtil;
							retriever.listen(configUtil::onChange);
							emitter.onSuccess(configUtil);
						});
					}
				}, err -> {
					emitter.onError(err);
				});
			}
		});
	}
	private static void resolvePlaceHolders(ConfigUtil config) {
		int contResolved= 0;
		int contBefore;
		do {
			contBefore= contResolved;
			contResolved= resolvePlaceHolders(config.json, config);
		} while (contResolved != 0 || contResolved != contBefore);
	}
	
	private static int resolvePlaceHolders(JsonObject configJson, ConfigUtil config) {
		int cont= 0;
		for (Entry<String, Object> entry: configJson) {
			if (entry.getValue() instanceof String) {
				if (!resolvePlaceHolders(entry.getKey(), (String) entry.getValue(), configJson, config)) {
					cont++;
				}
			} else if (entry.getValue() instanceof JsonObject) {
				resolvePlaceHolders((JsonObject) entry.getValue(), config);
			}
		}
		
		return cont;
	}

	/**
	 * 
	 * @param key
	 * @param value
	 * @param configJson
	 * @param config
	 * @return Returns false if a replacement was detected but we didn't found the key
	 */
	private static boolean resolvePlaceHolders(String key, String entryValue, JsonObject configJson, ConfigUtil config) {
		String value= entryValue.trim();
		int next= value.indexOf('$');
		if (next != -1 && value.length() > next+1 && value.charAt(next+1) == '{') {
			int close= value.indexOf('}',next+2);
			if (close != -1) {
				Object replacement= config.getObject(value.substring(next+2, close));
				if (replacement == null && "random.value".equals(value.substring(next+2, close))) {
					//FIXME: In Spring random.value is regenerated every time
					replacement= randomString();
				}
				if (replacement != null) {
					if (next == 0 && close == value.length()) {
						//Replace the String with the object
						configJson.put(key, replacement);
						return true;
					} else {
						//Replace the value and continue searching
						value= value.substring(0,next) + replacement.toString() + value.substring(close + 1);
						configJson.put(key, value);
						return resolvePlaceHolders(key, value, configJson, config);
					}
				} else {
					return false;
				}
			}
		}
		return true;
	}
	
	private static String randomString() {
		int leftLimit = 48; // numeral '0'
		int rightLimit = 102; // letter 'f'
		int targetStringLength = 32;
		Random random = new Random();
		String generatedString = random.ints(leftLimit, rightLimit + 1)
				.filter(i -> (i <= 57 || i >= 97))
				.limit(targetStringLength)
				.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
				.toString();
	 
	   return generatedString;
	}

	/**
	 * Maps properties to json objects.
	 * Ej: {"path.to.key" = "value"} to {"path" = {"to" = {"key" = "value"}}} 
	 * @param springConfig
	 * @return
	 */
	private static JsonObject propertiesToJson(JsonObject springConfig) {
		JsonObject jsonConfig= new JsonObject();
		springConfig.forEach(e -> {
			JsonObject obj= jsonConfig;
			String[] ruta= e.getKey().split("\\.");
			for (int i=0; i<ruta.length - 1; i++) {
				String key= ruta[i];
				JsonObject newObj= obj.getJsonObject(key);
				if (newObj == null) {
					newObj= new JsonObject();
					obj.put(key, newObj);
				}
				obj= newObj;
			}
			obj.put(ruta[ruta.length - 1], e.getValue());
		});
		
		return jsonConfig;
	}

	/**
	 * Returns last configured ConfigUtil
	 * @return
	 */
	public static ConfigUtil getInstance() {
		return instance;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void onChange(ConfigChange change) {
		if (!change.getNewConfiguration().isEmpty()) {
			final JsonObject newConfig;
			if (baseConf != null) {
				//This is Spring Config
				newConfig=propertiesToJson(change.getNewConfiguration());
			} else {
				newConfig= change.getNewConfiguration();
			}
			
			listeners.forEach((key, value) -> {
				Object newValue= getObject(key, newConfig);
				Object oldValue= getObject(key, change.getPreviousConfiguration());
				if (newValue != null && !newValue.equals(oldValue)
						|| newValue == null && oldValue != null) {
					for (Handler handler: value) {
						String className= handler.getClass().toString();
						if (className != null) {
							int iDollar= className.indexOf('$');
							if (iDollar != -1) {
								className= className.substring(0, iDollar);
							}
						}
						if (newValue == null) {
							// No need to check if exists. If not, newValue will be null anyway
							newValue= defaults.get(handler);
						}
						log.info("Value of " + key + " changed to " + className);
						handler.handle(newValue);
					}
				}
			});
		}
	}
	
	public void listen(String key, Handler<? extends Object> valueHandler) {
		List<Handler<? extends Object>> keyListeners= listeners.computeIfAbsent(key, v-> new ArrayList<>());
		keyListeners.add(valueHandler);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public String getString(String key) {
		return (String) getObject(key);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public String getString(String key, String defaultValue) {
		return (String) getObject(key, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public String getStringAndListen(String key, Handler<String> setter) {
		return (String) getObjectAndListen(key, setter);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public String getStringAndListen(String key, String defaultValue, Handler<String> setter) {
		return (String) getObjectAndListen(key, setter, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public Integer getInteger(String key) {
		return (Integer) getObject(key);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public int getInteger(String key, int defaultValue) {
		return (Integer) getObject(key, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public Integer getIntegerAndListen(String key, Handler<Integer> setter) {
		return (Integer) getObjectAndListen(key, setter);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public int getIntegerAndListen(String key, int defaultValue, Handler<Integer> setter) {
		return (Integer) getObjectAndListen(key, setter, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public Long getLong(String key) {
		return (Long) getObject(key);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public long getLong(String key, long defaultValue) {
		return (Long) getObject(key, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public Long getLongAndListen(String key, Handler<Long> setter) {
		return (Long) getObjectAndListen(key, setter);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public long getLongAndListen(String key, long defaultValue, Handler<Long> setter) {
		return (Long) getObjectAndListen(key, setter, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public Boolean getBoolean(String key) {
		return (Boolean) getObject(key);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public boolean getBoolean(String key, boolean defaultValue) {
		return (Boolean) getObject(key, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public Boolean getBooleanAndListen(String key, Handler<Boolean> setter) {
		return (Boolean) getObjectAndListen(key, setter);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public boolean getBooleanAndListen(String key, boolean defaultValue, Handler<Boolean> setter) {
		return (Boolean) getObjectAndListen(key, setter, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public Float getFloat(String key) {
		return (Float) getObject(key);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public float getFloat(String key, float defaultValue) {
		return (Float) getObject(key, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public Float getFloatAndListen(String key, Handler<Float> setter) {
		return (Float) getObjectAndListen(key, setter);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public float getFloatAndListen(String key, float defaultValue, Handler<Float> setter) {
		return (Float) getObjectAndListen(key, setter, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public Double getDouble(String key) {
		return (Double) getObject(key);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public double getDouble(String key, double defaultValue) {
		return (Double) getObject(key, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public Double getDoubleAndListen(String key, Handler<Double> setter) {
		return (Double) getObjectAndListen(key, setter);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public double getDoubleAndListen(String key, double defaultValue, Handler<Double> setter) {
		return (Double) getObjectAndListen(key, setter, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public Object getObject(String key) {
		Object obj= getObject(key, json);
		if (obj == null && baseConf != null) {
			obj= baseConf.getObject(key);
		}
		if (obj == null) {
			log.warn("Property " + key + " not found");
		}
		return obj;
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T extends Object> T getObject(String key, T defaultValue) {
		T obj= (T) getObject(key, json);
		if (obj == null && baseConf != null) {
			obj= (T) baseConf.getObject(key);
		}
		if (obj == null) {
			obj= defaultValue;
		}
		return obj;
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public Object getObjectAndListen(String key, Handler<? extends Object> setter) {
		listen(key,setter);
		return getObject(key);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public <T extends Object> T getObjectAndListen(String key, Handler<T> setter, T defaultValue) {
		defaults.put(setter,defaultValue);
		listen(key,setter);
		T o= (T) getObject(key, defaultValue);
		return o;
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public JsonObject getJsonObject(String key) {
		return (JsonObject) getObject(key);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public JsonObject getJsonObject(String key, @NonNull JsonObject defaultValue) {
		return (JsonObject) getObject(key, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public JsonObject getJsonObjectAndListen(String key, Handler<JsonObject> setter) {
		return (JsonObject) getObject(key, setter);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public JsonObject getJsonObjectAndListen(String key, @NonNull JsonObject defaultValue, Handler<JsonObject> setter) {
		return (JsonObject) getObjectAndListen(key, setter, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public <T> T mapTo(String key, Class<T> classT) {
		return ((JsonObject) getObject(key)).mapTo(classT);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public <T> T mapTo(String key, Class<T> classT, @NonNull T defaultValue) {
		Object obj= getObject(key);
		T retObj;
		if (obj == null) {
			retObj= defaultValue;
		} else {
			retObj= ((JsonObject) obj).mapTo(classT);
		}
		return retObj;
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public <T> T mapToAndListen(String key, Class<T> classT, Handler<T> setter) {
		listen(key,handler -> {
			setter.handle(((JsonObject) handler).mapTo(classT));
		});
		return mapTo(key, classT);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	public <T> T mapToAndListen(String key, Class<T> classT, Handler<T> setter, @NonNull T defaultValue) {
		listen(key,handler -> {
			if (handler == null) {
				setter.handle(defaultValue);
			} else {
				setter.handle(((JsonObject) handler).mapTo(classT));
			}
		});
		return mapTo(key, classT, defaultValue);
	}
	
	/**
	 * Gets a configuration parameter from all sources, with preference for environment properties.
	 * @param key separated by . Can't use [] for arrays.
	 * @return
	 */
	private Object getObject(String key, JsonObject root) {
		StringBuilder keyEnv= new StringBuilder();
		StringBuilder keyEnvMayus= new StringBuilder();
		StringBuilder keyCamelCase= new StringBuilder();
		String[] path= key.split("\\.");
		
		JsonObject jsonPath= root;
		for (int i=0; i<path.length - 1; i++) {
			String part= path[i];
			if (jsonPath != null) {
				jsonPath= jsonPath.getJsonObject(part);
			}
			keyEnv.append(part).append("_");
			keyEnvMayus.append(part.toUpperCase()).append("_");
			keyCamelCase.append(i==0? part: part.substring(0,1).toUpperCase() + part.substring(1).toLowerCase());
		}
		String last= path[path.length-1];
		keyEnv.append(last);
		keyEnvMayus.append(last.toUpperCase());
		keyCamelCase.append(last.substring(0,1).toUpperCase() + last.substring(1).toLowerCase());

		if (root.containsKey(key)) {
			return root.getValue(key);
		}
		if (root.containsKey(keyEnv.toString())) {
			return root.getValue(keyEnv.toString());
		}		
		if (root.containsKey(keyEnvMayus.toString())) {
			return root.getValue(keyEnvMayus.toString());
		}		
		if (root.containsKey(keyCamelCase.toString())) {
			return root.getValue(keyCamelCase.toString());
		}
		if (jsonPath != null) {
			return jsonPath.getValue(last);
		}
		return null;
	}
}