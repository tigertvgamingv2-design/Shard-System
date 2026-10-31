package com.thronecraft.net.licensing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LicenseVerifier {
    private static final int MAX_RESPONSE_BYTES = 65_536;

    private LicenseVerifier() {
    }

    public static VerificationResult verify(
            String apiUrl,
            String productSlug,
            String licenseKey
    ) throws IOException {
        URI baseUri;
        try {
            baseUri = URI.create(apiUrl);
        } catch (IllegalArgumentException exception) {
            throw new IOException("The configured license API URL is invalid.", exception);
        }
        String scheme = baseUri.getScheme();
        if (baseUri.getHost() == null || baseUri.getUserInfo() != null
                || baseUri.getQuery() != null || baseUri.getFragment() != null
                || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
            throw new IOException("The license API URL must be an HTTP(S) URL without credentials or query parameters.");
        }

        String nonce = UUID.randomUUID().toString();
        String requestBody = "{\"licenseKey\":" + jsonString(licenseKey)
                + ",\"productSlug\":" + jsonString(productSlug)
                + ",\"fingerprint\":" + jsonString(generateFingerprint())
                + ",\"nonce\":" + jsonString(nonce) + "}";

        URL url = baseUri.toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(8_000);
        connection.setInstanceFollowRedirects(false);
        connection.setDoOutput(true);

        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("License server returned HTTP " + status + ".");
            }

            String responseBody;
            try (InputStream input = connection.getInputStream()) {
                responseBody = readResponse(input);
            }
            return parseResponse(responseBody, productSlug, nonce);
        } finally {
            connection.disconnect();
        }
    }

    private static VerificationResult parseResponse(
            String responseBody,
            String productSlug,
            String requestNonce
    ) throws IOException {
        Map<String, Object> response = new JsonObjectReader(responseBody).read();
        if (!Boolean.TRUE.equals(response.get("ok"))) {
            throw new IOException("License server returned an invalid response.");
        }

        Object validValue = response.get("valid");
        Object timestampValue = response.get("timestamp");
        String reason = stringValue(response, "reason", false);
        String product = stringValue(response, "product", true);
        String nonce = stringValue(response, "nonce", false);

        if (!(validValue instanceof Boolean valid) || !(timestampValue instanceof Long timestamp)) {
            throw new IOException("License server response is missing required fields.");
        }
        if (!requestNonce.equals(nonce)) {
            throw new IOException("License server response nonce did not match the request.");
        }

        long now = System.currentTimeMillis() / 1_000;
        if (timestamp < now - 300 || timestamp > now + 300) {
            throw new IOException("License server response timestamp is outside the allowed window.");
        }
        if (valid && !productSlug.equals(product)) {
            throw new IOException("License server response was for a different product.");
        }
        return new VerificationResult(valid, reason);
    }

    private static String readResponse(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4_096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_RESPONSE_BYTES) {
                throw new IOException("License server response exceeded the size limit.");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static String generateFingerprint() throws IOException {
        String hostname = System.getenv().getOrDefault(
                "COMPUTERNAME",
                System.getenv().getOrDefault("HOSTNAME", "unknown")
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(hostname.getBytes(StandardCharsets.UTF_8));
            StringBuilder fingerprint = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                fingerprint.append(String.format("%02x", value & 0xff));
            }
            return fingerprint.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable.", exception);
        }
    }

    private static String stringValue(Map<String, Object> values, String name, boolean nullable)
            throws IOException {
        Object value = values.get(name);
        if (nullable && value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        throw new IOException("License server response field '" + name + "' is invalid.");
    }

    private static String jsonString(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    public record VerificationResult(boolean valid, String reason) {
    }

    private static final class JsonObjectReader {
        private final String json;
        private int index;

        private JsonObjectReader(String json) {
            this.json = json;
        }

        private Map<String, Object> read() throws IOException {
            Map<String, Object> values = new HashMap<>();
            skipWhitespace();
            expect('{');
            skipWhitespace();
            if (take('}')) {
                finish();
                return values;
            }
            do {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object value = readValue();
                if (values.containsKey(key)) {
                    throw new IOException("License server response contains a duplicate field.");
                }
                values.put(key, value);
                skipWhitespace();
                if (take('}')) {
                    finish();
                    return values;
                }
                expect(',');
            } while (true);
        }

        private Object readValue() throws IOException {
            if (index >= json.length()) {
                throw malformed();
            }
            char character = json.charAt(index);
            if (character == '"') {
                return readString();
            }
            if (matches("true")) {
                return Boolean.TRUE;
            }
            if (matches("false")) {
                return Boolean.FALSE;
            }
            if (matches("null")) {
                return null;
            }
            int start = index;
            if (take('-')) {
                if (index >= json.length()) {
                    throw malformed();
                }
            }
            while (index < json.length() && Character.isDigit(json.charAt(index))) {
                index++;
            }
            if (start == index || (json.charAt(start) == '-' && start + 1 == index)) {
                throw malformed();
            }
            try {
                return Long.parseLong(json.substring(start, index));
            } catch (NumberFormatException exception) {
                throw malformed();
            }
        }

        private String readString() throws IOException {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < json.length()) {
                char character = json.charAt(index++);
                if (character == '"') {
                    return value.toString();
                }
                if (character < 0x20) {
                    throw malformed();
                }
                if (character != '\\') {
                    value.append(character);
                    continue;
                }
                if (index >= json.length()) {
                    throw malformed();
                }
                char escaped = json.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(readUnicodeEscape());
                    default -> throw malformed();
                }
            }
            throw malformed();
        }

        private char readUnicodeEscape() throws IOException {
            if (index + 4 > json.length()) {
                throw malformed();
            }
            try {
                char value = (char) Integer.parseInt(json.substring(index, index + 4), 16);
                index += 4;
                return value;
            } catch (NumberFormatException exception) {
                throw malformed();
            }
        }

        private boolean matches(String value) {
            if (!json.startsWith(value, index)) {
                return false;
            }
            index += value.length();
            return true;
        }

        private void expect(char expected) throws IOException {
            if (!take(expected)) {
                throw malformed();
            }
        }

        private boolean take(char expected) {
            if (index < json.length() && json.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
        }

        private void finish() throws IOException {
            skipWhitespace();
            if (index != json.length()) {
                throw malformed();
            }
        }

        private IOException malformed() {
            return new IOException("License server returned malformed JSON.");
        }
    }
}
