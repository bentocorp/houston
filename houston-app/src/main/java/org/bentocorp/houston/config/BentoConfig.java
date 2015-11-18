package org.bentocorp.houston.config;

import javax.annotation.PostConstruct;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BentoConfig {

    public final static String Filename = "private-NO-COMMIT.conf";

    final Logger logger = LoggerFactory.getLogger(BentoConfig.class);

    // By default, SpringApplication converts any command line option (starting with "--") to a property and adds it to
    // the Environment instance before injection. Therefore, Houston must be started with the --env option in order to
    // load the correct configurations.
    @Autowired
    Environment springEnvironment = null;

    Config config = null;

    @PostConstruct
    public void init() {
        Config defaults = ConfigFactory.load(BentoConfig.Filename);
        String env = springEnvironment.getProperty("env");
        logger.info("Loading configuration from " + Filename + " for environment " + env);
        config = defaults.getConfig(env).withFallback(defaults);
        // Option to wipe Redis instance before running the Spring application
        boolean flushRedis = springEnvironment.getProperty("flush-redis") != null;
        boolean noAuth = springEnvironment.getProperty("no-auth") != null;
        config = config.withValue("flush-redis", ConfigValueFactory.fromAnyRef(flushRedis))
                .withValue("no-auth", ConfigValueFactory.fromAnyRef(noAuth))
                .withValue("env", ConfigValueFactory.fromAnyRef(env));
    }

    public Config toTypesafeConfig() { return config; }

    public String getString(String s) { return config.getString(s); }

    public List<String> getStringList(String s) { return config.getStringList(s); }

    public boolean getBoolean(String s) { return config.getBoolean(s); }
}
