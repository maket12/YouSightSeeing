package api;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.yandexmapcitysearch.AuthActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class UserApi {

    private static final String TAG = "UserApi";
    private static final MediaType JSON
            = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient();

    public interface UserCallback {
        void onSuccess(JSONObject userJson);
        void onError(String message);
    }

    /**
     * GET /api/users/me
     */
    public static void getMe(Context ctx, UserCallback cb) {
        String access = AuthActivity.getAccessToken();
        if (access == null) {
            cb.onError("Требуется авторизация");
            return;
        }

        Request request = new Request.Builder()
                .url(ApiConfig.USERS_ME)
                .get()
                .addHeader("Authorization", "Bearer " + access)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "getMe failure", e);
                cb.onError("Ошибка сети: " + e.getMessage());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    cb.onError("Ошибка профиля: " + response.code() + "\n" + body);
                    return;
                }
                try {
                    cb.onSuccess(new JSONObject(body));
                } catch (JSONException e) {
                    cb.onError("Некорректный ответ сервера");
                }
            }
        });
    }

    /**
     * POST /api/users/me
     * body: UpdateUserRequest (любые поля: email, full_name, first_name, last_name, picture)
     */
    public static void updateMe(Context ctx, JSONObject updateBody, UserCallback cb) {
        String access = AuthActivity.getAccessToken();
        if (access == null) {
            cb.onError("Требуется авторизация");
            return;
        }

        RequestBody body = RequestBody.create(updateBody.toString(), JSON);
        Request request = new Request.Builder()
                .url(ApiConfig.USERS_ME)
                .post(body)
                .addHeader("Authorization", "Bearer " + access)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "updateMe failure", e);
                cb.onError("Ошибка сети: " + e.getMessage());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    cb.onError("Ошибка обновления профиля: " + response.code() + "\n" + body);
                    return;
                }
                try {
                    JSONObject json = new JSONObject(body);
                    // UpdateUserResponse { id, updated, user }
                    JSONObject user = json.getJSONObject("user");
                    cb.onSuccess(user);
                } catch (JSONException e) {
                    cb.onError("Некорректный ответ сервера");
                }
            }
        });
    }

    /**
     * POST /api/users/me/picture
     * body: { "picture": "<url or empty string>" }
     */
    public static void updatePicture(Context ctx, String pictureUrl, UserCallback cb) {
        String access = AuthActivity.getAccessToken();
        if (access == null) {
            cb.onError("Требуется авторизация");
            return;
        }

        JSONObject bodyJson = new JSONObject();
        try {
            bodyJson.put("picture", pictureUrl != null ? pictureUrl : "");
        } catch (JSONException e) {
            cb.onError("Ошибка подготовки запроса");
            return;
        }

        RequestBody body = RequestBody.create(bodyJson.toString(), JSON);
        Request request = new Request.Builder()
                .url(ApiConfig.USERS_ME_PICTURE)
                .post(body)
                .addHeader("Authorization", "Bearer " + access)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "updatePicture failure", e);
                cb.onError("Ошибка сети: " + e.getMessage());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    cb.onError("Ошибка обновления фото: " + response.code() + "\n" + body);
                    return;
                }
                try {
                    JSONObject json = new JSONObject(body);
                    JSONObject user = json.getJSONObject("user");
                    cb.onSuccess(user);
                } catch (JSONException e) {
                    cb.onError("Некорректный ответ сервера");
                }
            }
        });
    }
}
