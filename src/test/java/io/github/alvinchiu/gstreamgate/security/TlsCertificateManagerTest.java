package io.github.alvinchiu.gstreamgate.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TlsCertificateManagerTest {
    private static final String CERT = """
-----BEGIN CERTIFICATE-----
MIIDBzCCAe+gAwIBAgIUJeVrx17IJrFUxoAXMQV8UKnSxOswDQYJKoZIhvcNAQEL
BQAwEzERMA8GA1UEAwwIVGVzdENlcnQwHhcNMjUwNjA2MDc0MzQ2WhcNMzUwNjA0
MDc0MzQ2WjATMREwDwYDVQQDDAhUZXN0Q2VydDCCASIwDQYJKoZIhvcNAQEBBQAD
ggEPADCCAQoCggEBAK98QB82Rz95ABZGpQMcodqt1NhiVtfo1fsVfTWqmCGBNGCO
F6nK2rEm9vrwZgp5FP7pNlTCJrIXvRqETVXCiguklwN4msRsZKZ6uo70FzgYR8hq
mCVMiVcOqPuWwcjLyy7m7hUWNGnVS1xV7jEn8/rfrGvF+oWedRusU98eDNYLinjz
61zlj95YYeOw9UAX6BREY/6k4V9SzmUq4XcDcdOD7EonIMme5rbYN4gGJDmgJACK
xcyiWBc0EjfrJvpAHVwR70EeATvmj3NkpmpugHQajjpYJaz24+Meo63X5QIVnTC0
tdBhDJ5VuweBgUmhncZlJmLxlDzSeIHnbvNo5xkCAwEAAaNTMFEwHQYDVR0OBBYE
FPWe8Z6526E6WC8gTSYnfQKYRNZ9MB8GA1UdIwQYMBaAFPWe8Z6526E6WC8gTSYn
fQKYRNZ9MA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQELBQADggEBAE3oHF87
Y8qcNjZUt62Yto0pOMKHtb3ZrwBNZCw/jWt9TZfemZoX5zIephi2hcHmOwbVR2cR
PXhgmLj0GQTU25cNqWhkmvTTfI+Q9tRnFZWPPlRZEQyykaoRURTlEXc5/7dOmJ4o
U+YZefaYtULLUG//LBPxl+xonXjOwxlcrJ9k5UqK1HFrZ0a0jGAxHEIAGN+ItOe2
gv8xwSv9QfkLmAOsQKFeHWOHpCMXgJBNgNzNTfwjoKWrtThmTV4+TEdnM1pcug8d
87IMhgqkj8Hus36un1+TwO7e3T6Ai2/uoTIXB7VdAGHvGlfXH8F72T7dyQOrYghL
KH9EJRFV53iBYIw=
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
