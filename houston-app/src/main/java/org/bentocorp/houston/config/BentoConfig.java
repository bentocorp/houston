package org.bentocorp.houston.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class BentoConfig {

    public final static String Filename = "private-NO-COMMIT.conf";

    final Logger logger = LoggerFactory.getLogger(BentoConfig.class);

    // By default, SpringApplication converts any command line option (starting with "--") to a property and adds it to
    // the Environment instance before injection. Therefore, Houston must be started with the --env option in order to
    // load the correct configurations.
    @Autowired
    public Environment springEnvironment = null; // This is public so we can manually set it in code for
                                                 // offline testing

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

        // If this option is set (to anything), don't start the SQS service (to avoid
        // consuming from a live queue)
        boolean ignoreSqs = springEnvironment.getProperty("ignore-sqs") != null;

        String deployId = springEnvironment.getProperty("deploy-id");

        config = config.withValue("deploy-id", ConfigValueFactory.fromAnyRef(deployId))
                       .withValue("flush-redis", ConfigValueFactory.fromAnyRef(flushRedis))
                       .withValue("no-auth", ConfigValueFactory.fromAnyRef(noAuth))
                       .withValue("ignore-sqs", ConfigValueFactory.fromAnyRef(ignoreSqs))
                       .withValue("env", ConfigValueFactory.fromAnyRef(env));
    }

    public Config toTypesafeConfig() { return config; }

    public String getString(String s) { return config.getString(s); }

    public List<String> getStringList(String s) { return config.getStringList(s); }

    public boolean getBoolean(String s) { return config.getBoolean(s); }

    public boolean getIsNull(String s) { return config.getIsNull(s); }
}
