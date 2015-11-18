package org.bentocorp.houston.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.embedded.ConfigurableEmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;
import org.springframework.boot.context.embedded.Ssl;
import org.springframework.stereotype.Component;

@Component
public class SecurityConfig implements EmbeddedServletContainerCustomizer {

    final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Autowired
    BentoConfig config = null;

    // The self-signed certificate configured for local development was generated using the following command
    // keytool -genkey -alias localhost -storetype PKCS12 -keyalg RSA -keysize 2048 -keystore keystore.p12 -validity 3650
    // Copy the certificate (keystore.p12) to /sites/.ssl
    public void customize(ConfigurableEmbeddedServletContainer container) {
        /* Enable SSL (https) via Amazon's Elastic Load Balancer (ELB)
        logger.info("Configuring SSL");
        Ssl ssl = new Ssl();
        ssl.setEnabled(true);
        ssl.setKeyStore(config.getString("ssl.keystore.file"));
        ssl.setKeyPassword(config.getString("ssl.keystore.password"));
        ssl.setKeyStoreType(config.getString("ssl.keystore.type"));
        ssl.setKeyAlias(config.getString("ssl.keystore.alias"));
        ssl.setProtocol("TLS");
        ssl.setClientAuth(Ssl.ClientAuth.WANT);
        container.setSsl(ssl);
        */
    }
}
