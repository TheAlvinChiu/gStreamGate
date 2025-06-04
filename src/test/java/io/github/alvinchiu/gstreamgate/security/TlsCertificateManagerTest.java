package io.github.alvinchiu.gstreamgate.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TlsCertificateManagerTest {
    private static final String CERT = """
-----BEGIN CERTIFICATE-----
MIIC/zCCAeegAwIBAgIUF7cA9TNFgYTAYfx5VmZBMtlRDYIwDQYJKoZIhvcNAQEL
BQAwDzENMAsGA1UEAwwEVGVzdDAeFw0yNTA2MDQwNjQ5NDJaFw0yNTA2MDUwNjQ5
NDJaMA8xDTALBgNVBAMMBFRlc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK
AoIBAQDWKh86PP0p3sas4jyzjdCqAovLAnjvS86LfFdI3Wa5AfY3DkmSXZY/uBUc
AehwQm3U/bXQ6IzV83nNQX/vAg0l3lOoY1CQUHONvS1wnvoQruuY9xaetpFTxPQt
Hy/edy27ijQa8sS7IfZkmdNk/2Tv4xmhlUFK/39V96t8XYAxPwsAE0hRU6ugkrzC
jPT/03BsegjxHLW12Hvok4hSoFizB3Io2l6d4D1ask09IKduT55UIDRpjTSQwlCR
G80YIQ1Im9K+Hn5J13KSv9mfg/7vdK3yI5zrPDXp7I+IBQXsLOzm1Zldnxh7BN1v
qzFh5Yz6IhLVAKXTYebB5C2reFClAgMBAAGjUzBRMB0GA1UdDgQWBBQuDbaJy0xZ
A+c16SVMSh2D65TlrTAfBgNVHSMEGDAWgBQuDbaJy0xZA+c16SVMSh2D65TlrTAP
BgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQB4ovQDvqKRXoMXfZGR
podQD6DyiTHdusrMZn1rxzcL7cXAzzNLoTiyPcyyZa9aHA+uBAJGDZc0wMFJCZUt
R4MKcEhAP740nuJiRrr5AQi9VQShZgrHK0420684hKaTIsQPFy6l5d8JG39xpDXf
vKBGZzv/ESi2RIe091swb31Zy0uDX4m8QIQlLt4lSQ0nSGN/tRiSwOo7QEFOkPOv
J3eFLK1HtXzaeyHMMD1RKj4r1lxmcBs26z14i4Ncfb1iCnjWHxJwGtx4kG2ee7BO
MCTpECwnMhv0EM/+b2LvY4J970mdFGgakKdAJXyZPlORyAqwUKZRplDQPFyFnoCh
pP5M
-----END CERTIFICATE-----
""";

    @Test
    void validateCertificateReturnsTrueForValidCert() {
        TlsCertificateManager manager = new TlsCertificateManager();
        assertTrue(manager.validateCertificate(CERT));
    }

    @Test
    void createClientSslContextFailsOnEmptyCert() {
        TlsCertificateManager manager = new TlsCertificateManager();
        assertThrows(IllegalArgumentException.class, () -> manager.createClientSslContext(""));
    }
}
