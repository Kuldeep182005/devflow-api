package com.devflow.api.github.integration;

import com.devflow.api.github.config.GithubProperties;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GithubAppJwtService {

    private final GithubProperties properties;

    public GithubAppJwtService(GithubProperties properties) {
        this.properties = properties;
    }

    public String generate() {
        if (properties.getId() == null || properties.getId().isBlank()
                || properties.getPrivateKey() == null || properties.getPrivateKey().isBlank()) {
            throw new GithubIntegrationException(
                    HttpStatus.SERVICE_UNAVAILABLE, "GITHUB_NOT_CONFIGURED",
                    "GitHub integration is not configured");
        }
        long appId;
        try {
            appId = Long.parseLong(properties.getId().trim());
            if (appId <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            throw new GithubIntegrationException(
                    HttpStatus.SERVICE_UNAVAILABLE, "GITHUB_NOT_CONFIGURED",
                    "GitHub integration is not configured");
        }

        try {
            Instant now = Instant.now();
            return Jwts.builder()
                    .issuer(Long.toString(appId))
                    .issuedAt(Date.from(now.minusSeconds(60)))
                    .expiration(Date.from(now.plusSeconds(540)))
                    .signWith(readPrivateKey())
                    .compact();
        } catch (Exception exception) {
            throw new GithubIntegrationException(
                    HttpStatus.SERVICE_UNAVAILABLE, "GITHUB_NOT_CONFIGURED",
                    "GitHub integration is not configured");
        }
    }

    private PrivateKey readPrivateKey() throws Exception {
        String pem = properties.getPrivateKey().replace("\\n", "\n").trim();
        String encoded = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(encoded);
        // GitHub commonly supplies PKCS#1 RSA keys. Java's RSA KeyFactory
        // accepts PKCS#8, so wrap PKCS#1 in the standard PKCS#8 envelope.
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            der = pkcs8(der);
        }
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private byte[] pkcs8(byte[] pkcs1) {
        byte[] algorithm = new byte[] {
                0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
                (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
        };
        byte[] version = new byte[] {0x02, 0x01, 0x00};
        byte[] key = der(0x04, pkcs1);
        return der(0x30, concat(version, algorithm, key));
    }

    private byte[] der(int tag, byte[] value) {
        return concat(new byte[] {(byte) tag}, length(value.length), value);
    }

    private byte[] length(int value) {
        if (value < 128) {
            return new byte[] {(byte) value};
        }
        int bytes = (Integer.SIZE - Integer.numberOfLeadingZeros(value) + 7) / 8;
        byte[] result = new byte[bytes + 1];
        result[0] = (byte) (0x80 | bytes);
        for (int i = bytes; i > 0; i--) {
            result[i] = (byte) (value >>> (8 * (bytes - i)));
        }
        return result;
    }

    private byte[] concat(byte[]... values) {
        int size = 0;
        for (byte[] value : values) {
            size += value.length;
        }
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }
}
