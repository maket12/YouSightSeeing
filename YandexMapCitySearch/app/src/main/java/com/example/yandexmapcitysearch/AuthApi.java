package com.example.yandexmapcitysearch;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

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

public final class AuthApi {

    private static final String TAG = "AuthApi";
    private static final String PREFS_NAME = "auth_tokens";
    private static final MediaType JSON
            = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient();

    public interface RefreshCallback {
        void onSuccess(String newAccess, String newRefresh);
        void onError(String message);
    }

    public interface LogoutCallback {
        void onSuccess();
        void onError(String message);
    }

    /**
     * POST /auth/refresh
     * body: { "refresh_token": "<refresh>" }
     * ответ (плоский): { "access_token": "...", "refresh_token": "...", "user": {...} }
     */
    public static void refreshTokens(Context ctx, RefreshCallback cb) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String currentRefresh = prefs.getString("refresh_token", null);

        if (currentRefresh == null) {
            cb.onError("Refresh токен отсутствует, нужен повторный вход");
            return;
        }

        String url = "http://10.0.2.2:8080/auth/refresh";

        JSONObject bodyJson = new JSONObject();
        try {
            bodyJson.put("refresh_token", currentRefresh);
        } catch (JSONException e) {
            cb.onError("Ошибка формирования запроса");
            return;
        }

        RequestBody body = RequestBody.create(bodyJson.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "refresh failure", e);
                cb.onError("Ошибка сети: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    cb.onError("Ошибка обновления токенов: " + response.code() + "\n" + respBody);
                    return;
                }

                try {
                    JSONObject json = new JSONObject(respBody);

                    String newAccess  = json.getString("access_token");
                    String newRefresh = json.optString("refresh_token", null);

                    SharedPreferences.Editor ed = prefs.edit()
                            .putString("access_token", newAccess);

                    // если сервер не прислал новый refresh_token, оставляем старый
                    if (newRefresh != null && !newRefresh.isEmpty()) {
                        ed.putString("refresh_token", newRefresh);
                    }
                    ed.apply();

                    cb.onSuccess(newAccess, newRefresh);
                } catch (JSONException e) {
                    Log.e(TAG, "parse refresh response error", e);
                    cb.onError("Некорректный ответ сервера");
                }
            }
        });
    }

    /**
    * POST /auth/logout
    * body: { "refresh_token": "<refresh>" }
    */
    public static void logout(Context ctx, LogoutCallback cb) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String refresh = prefs.getString("refresh_token", null);

        if (refresh == null) {
            prefs.edit().clear().apply();
            cb.onSuccess();
            return;
        }

        String url = "http://10.0.2.2:8080/auth/logout";

        JSONObject bodyJson = new JSONObject();
        try {
            bodyJson.put("refresh_token", refresh);
        } catch (JSONException e) {
            cb.onError("Ошибка формирования запроса");
            return;
        }

        RequestBody body = RequestBody.create(bodyJson.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "logout failure", e);
                cb.onError("Ошибка сети: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    cb.onError("Ошибка выхода: " + response.code() + "\n" + respBody);
                    return;
                }

                // успех: чистим локальное хранилище
                prefs.edit().clear().apply();
                cb.onSuccess();
            }
        });
    }
}
