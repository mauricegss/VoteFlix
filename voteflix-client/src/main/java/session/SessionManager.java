package session;

public class SessionManager {
    private static SessionManager instance;
    private String token;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void clearSession() {
        this.token = null;
    }
}