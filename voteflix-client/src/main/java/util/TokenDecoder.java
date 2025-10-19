package util;

import org.json.JSONObject;
import java.util.Base64;

public class TokenDecoder {

    /**
     * Decodifica o payload de um token JWT para extrair a função (role) do usuário.
     * Este método NÃO valida a assinatura do token; ele apenas lê os dados públicos.
     * A validação de segurança é responsabilidade exclusiva do servidor.
     *
     * @param token O token JWT recebido do servidor.
     * @return A função ("role") do usuário ("admin" ou "user") ou null se não for encontrada.
     */
    public static String getRoleFromToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            // Um JWT é composto por 3 partes separadas por ".". A parte do meio é o payload.
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                // Decodifica o payload que está em Base64Url
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                JSONObject jsonPayload = new JSONObject(payload);
                return jsonPayload.optString("role", null);
            }
        } catch (Exception e) {
            System.err.println("Erro ao decodificar o payload do token JWT: " + e.getMessage());
        }
        return null;
    }
}