package api;

public final class ApiConfig {
    private ApiConfig() {}

    public static final String BASE_URL = "http://10.0.2.2:8080";

    public static final String AUTH_GOOGLE  = BASE_URL + "/auth/google";
    public static final String AUTH_REFRESH = BASE_URL + "/auth/refresh";
    public static final String AUTH_LOGOUT  = BASE_URL + "/auth/logout";
    public static final String ROUTES_CALCULATE  = BASE_URL + "/api/routes/calculate";
    public static final String USERS_ME         = BASE_URL + "/api/users/me";

    public static final String USER_ME         = BASE_URL + "/user/me";
    public static final String USER_ME_PICTURE = BASE_URL + "/api/user/me/picture";
}
