package util;

import org.json.JSONObject;
import java.util.Base64;

public class TokenDecoder {

    public static String getRoleFromToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                JSONObject jsonPayload = new JSONObject(payload);
                return jsonPayload.optString("role", null);
            }
        } catch (Exception e) {
            System.err.println("Erro ao decodificar o payload do token JWT para obter role: " + e.getMessage());
        }
        return null;
    }

    public static Integer getUserIdFromToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                JSONObject jsonPayload = new JSONObject(payload);
                if (jsonPayload.has("id")) {
                    return jsonPayload.getInt("id");
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao decodificar o payload do token JWT para obter user ID: " + e.getMessage());
        }
        return null;
    }
}