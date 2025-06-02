package io.github.alvinchiu.gstreamgate.config;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.net.ssl.KeyManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * Configuration for TLS/SSL in gRPC server.
 * Configures the SSL context for secure connections.
 */
@Configuration
public class GrpcTlsConfig {

    private static final Logger logger = LoggerFactory.getLogger(GrpcTlsConfig.class);

    @Value("${server.ssl.key-store:}")
    private String keyStorePath;

    @Value("${server.ssl.key-store-password:}")
    private String keyStorePassword;

    @Value("${server.ssl.keyStoreType:PKCS12}")
    private String keyStoreType;

    /**
     * Creates and configures an SslContext for the gRPC server.
     * This is used when TLS is enabled for the gRPC proxy server.
     *
     * @return Configured SslContext or null if unable to create
     */
    @Bean
    public SslContext grpcSslContext() {
        // If keystore path is empty, return null (TLS not configured)
        if (keyStorePath == null || keyStorePath.isEmpty()) {
            logger.info("No keystore path configured, TLS will be disabled for gRPC server");
            return null;
        }

        try {
            // Try multiple ways to find the keystore file
            File keyStoreFile = null;

            // Try as absolute path
            File absolute = new File(keyStorePath);
            if (absolute.exists()) {
                keyStoreFile = absolute;
                logger.debug("Found keystore at absolute path: " + keyStorePath);
            }

            // Try as relative path
            if (keyStoreFile == null) {
                File relative = new File(System.getProperty("user.dir"), keyStorePath);
                if (relative.exists()) {
                    keyStoreFile = relative;
                    logger.debug("Found keystore at relative path: " + keyStorePath);
                }
            }

            // If still not found, try from classpath
            if (keyStoreFile == null) {
                try {
                    ClassPathResource resource = new ClassPathResource(keyStorePath.replace("classpath:", ""));
                    if (resource.exists()) {
                        keyStoreFile = resource.getFile();
                        logger.debug("Found keystore in classpath: " + keyStorePath);
                    }
                } catch (Exception e) {
                    logger.debug("Could not load keystore from classpath: " + e.getMessage());
                }
            }

            if (keyStoreFile == null) {
                logger.error("Could not find keystore file: " + keyStorePath);
                return null;
            }

            // Load KeyStore
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            try (FileInputStream fis = new FileInputStream(keyStoreFile)) {
                keyStore.load(fis, keyStorePassword.toCharArray());
            }

            // Create KeyManagerFactory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keyStorePassword.toCharArray());

            // Create SslContext
            return GrpcSslContexts.configure(
                    SslContextBuilder.forServer(kmf)
                            .clientAuth(ClientAuth.NONE)
            ).build();

        } catch (Exception e) {
            logger.error("Could not create gRPC TLS context: " + e.getMessage(), e);
            return null;
        }
    }
}