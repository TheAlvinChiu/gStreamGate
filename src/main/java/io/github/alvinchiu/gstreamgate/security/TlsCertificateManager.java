package io.github.alvinchiu.gstreamgate.security;

import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.*;
import java.util.Collection;

/**
 * Manages TLS certificates and SSL contexts.
 * Supports building SSL contexts directly from string content, rather than reading from files.
 */
@Component
public class TlsCertificateManager {
    private static final Logger logger = LoggerFactory.getLogger(TlsCertificateManager.class);

    /**
     * Creates a client SSL context that automatically trusts all upstream certificates.
     *
     * @return SSL context configured to auto-trust all certificates
     * @throws IOException if an error occurs while processing certificates or keys
     */
    public SslContext createInsecureClientSslContext() throws IOException {
        logger.debug("Creating client SSL context with auto-trust for all certificates");

        try {
            SslContext context = GrpcSslContexts.configure(SslContextBuilder.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE))
                    .build();
            logger.debug("Successfully created auto-trust SSL context");
            return context;
        } catch (Exception e) {
            logger.error("Error creating client SSL context: " + e.getMessage() + ", possibly SSL library configuration issue");
            throw new IOException("Failed to create client SSL context", e);
        }
    }

    /**
     * Creates a client SSL context using specified trusted certificates.
     *
     * @param trustCertsContent PEM format trusted certificate content
     * @return SSL context configured with the specified trusted certificates
     * @throws IOException if an error occurs while processing certificates or keys
     */
    public SslContext createClientSslContext(String trustCertsContent) throws IOException {
        if (trustCertsContent == null || trustCertsContent.isEmpty()) {
            logger.error("Trust certificate content is empty, cannot create SSL context");
            throw new IllegalArgumentException("Trust certificate content cannot be empty");
        }

        logger.debug("Creating client SSL context with custom trusted certificates");

        try {
            // Analyze certificate count and basic information
            int certCount = 0;
            StringBuilder certInfo = new StringBuilder("Trust certificate content analysis:");

            String beginMarker = "-----BEGIN CERTIFICATE-----";
            String endMarker = "-----END CERTIFICATE-----";
            int beginIndex = trustCertsContent.indexOf(beginMarker);

            while (beginIndex != -1) {
                certCount++;
                int endIndex = trustCertsContent.indexOf(endMarker, beginIndex);

                if (endIndex != -1) {
                    try {
                        String certPem = trustCertsContent.substring(beginIndex, endIndex + endMarker.length());
                        ByteArrayInputStream certStream = new ByteArrayInputStream(
                                certPem.getBytes(StandardCharsets.UTF_8));
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");
                        X509Certificate cert = (X509Certificate) cf.generateCertificate(certStream);

                        certInfo.append("\n  Certificate #").append(certCount)
                                .append(": Subject=").append(cert.getSubjectX500Principal().getName())
                                .append(", Issuer=").append(cert.getIssuerX500Principal().getName())
                                .append(", Valid from ").append(cert.getNotBefore())
                                .append(" to ").append(cert.getNotAfter());
                    } catch (Exception e) {
                        certInfo.append("\n  Certificate #").append(certCount).append(": Could not parse: ").append(e.getMessage());
                    }
                }

                beginIndex = trustCertsContent.indexOf(beginMarker, beginIndex + beginMarker.length());
            }

            logger.debug(certInfo.toString());

            if (certCount == 0) {
                logger.warn("No valid X.509 certificates found in the provided PEM content");
            } else {
                logger.debug("Found " + certCount + " X.509 certificates in the provided PEM content");
            }

            // Convert trusted certificate content to InputStream
            ByteArrayInputStream trustCertsStream = new ByteArrayInputStream(
                    trustCertsContent.getBytes(StandardCharsets.UTF_8));

            // Build SSL context
            SslContext context = GrpcSslContexts.configure(SslContextBuilder.forClient()
                            .trustManager(trustCertsStream))
                    .build();

            logger.debug("Successfully created SSL context with custom trusted certificates");
            return context;
        } catch (Exception e) {
            logger.error("Error creating client SSL context: " + e.getMessage());
            throw new IOException("Failed to create client SSL context", e);
        }
    }

    /**
     * Validates the validity of an X509 certificate.
     *
     * @param certContent PEM format X509 certificate content
     * @return true if the certificate is valid, false otherwise
     */
    public boolean validateCertificate(String certContent) {
        if (certContent == null || certContent.isEmpty()) {
            logger.error("Certificate content is empty, cannot validate");
            return false;
        }

        try {
            ByteArrayInputStream certStream = new ByteArrayInputStream(
                    certContent.getBytes(StandardCharsets.UTF_8));
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Collection<? extends X509Certificate> certs = (Collection<? extends X509Certificate>)
                    cf.generateCertificates(certStream);

            if (certs.isEmpty()) {
                logger.error("No valid X509 certificates found");
                return false;
            }

            // Log the number of certificates found
            logger.debug("Found " + certs.size() + " X.509 certificates in the provided content");

            // Check the first certificate
            X509Certificate cert = certs.iterator().next();

            // Log basic certificate information
            logger.debug("Certificate information: Subject=" + cert.getSubjectX500Principal().getName() +
                    ", Issuer=" + cert.getIssuerX500Principal().getName() +
                    ", Valid from " + cert.getNotBefore() + " to " + cert.getNotAfter());

            try {
                // Check if certificate is expired
                cert.checkValidity();
                logger.debug("Certificate validation successful, certificate is within validity period");
            } catch (CertificateExpiredException e) {
                logger.error("Certificate has expired, expiration date: " + cert.getNotAfter());
                return false;
            } catch (CertificateNotYetValidException e) {
                logger.error("Certificate is not yet valid, effective date: " + cert.getNotBefore());
                return false;
            }

            return true;
        } catch (CertificateException e) {
            logger.error("Certificate validation failed: " + e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error validating certificate: " + e.getMessage());
            return false;
        }
    }
}