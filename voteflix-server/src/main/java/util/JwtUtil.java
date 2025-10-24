package util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;

import java.util.Date;

public class JwtUtil {

    private static final String SECRET = "your-very-secret-key";
    private static final long EXPIRATION_TIME = 864_000_000; // 10 dias
    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET);

    public static String generateToken(String username, String role, int userId) {
        return JWT.create()
                .withSubject(username)
                .withClaim("role", role)
                .withClaim("id", userId)
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .sign(ALGORITHM);
    }

    public static DecodedJWT verifyToken(String token) {
        try {
            JWTVerifier verifier = JWT.require(ALGORITHM).build();
            return verifier.verify(token);
        } catch (JWTVerificationException exception) {
            return null;
        }
    }

    public static String getUsernameFromToken(String token) {
        DecodedJWT decodedJWT = verifyToken(token);
        return (decodedJWT != null) ? decodedJWT.getSubject() : null;
    }

    public static Integer getUserIdFromToken(String token) {
        DecodedJWT decodedJWT = verifyToken(token);
        if (decodedJWT != null) {
            try {
                return decodedJWT.getClaim("id").asInt();
            } catch (Exception e) {
                // Se a claim "id" não existir ou não for um inteiro
                return null;
            }
        }
        return null;
    }
}