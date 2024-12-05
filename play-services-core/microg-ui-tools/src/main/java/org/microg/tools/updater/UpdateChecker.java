package org.microg.tools.updater;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;
import org.microg.tools.ui.R;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class UpdateChecker {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/WSTxda/MicroG-RE/releases/latest";
    private static final String GITHUB_RELEASE_LINK = "https://github.com/WSTxda/MicroG-RE/releases/latest";
    private static final String ERROR_NO_RESPONSE = "ERROR_NO_RESPONSE";
    private static final String TAG = "UpdateChecker";

    private final WeakReference<Context> contextRef;

    public UpdateChecker(Context context) {
        this.contextRef = new WeakReference<>(context);
    }

    public void checkForUpdates() {
        Executor executor = Executors.newSingleThreadExecutor();
        FutureTask<String> futureTask = new FutureTask<>(this::doInBackground);

        executor.execute(futureTask);

        try {
            String latestVersion = futureTask.get();
            onPostExecute(latestVersion);
        } catch (Exception e) {
            handleUpdateError(e);
        }
    }

    private String doInBackground() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(GITHUB_API_URL).build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    String jsonData = responseBody.string();
                    return parseLatestVersion(jsonData);
                } else {
                    throw new IOException("Response body is null");
                }
            } else {
                throw new IOException("Unsuccessful response: " + response.code());
            }
        } catch (IOException e) {
            Log.e(TAG, "Error during network call", e);
            return handleRequestError(e);
        }
    }

    private String parseLatestVersion(String jsonData) {
        try {
            return new JSONObject(jsonData).optString("tag_name", "");
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON", e);
            return "";
        }
    }

    private void onPostExecute(String latestVersion) {
        Context context = contextRef.get();
        if (context == null) {
            return;
        }

        View rootView = getRootView(context);
        if (rootView == null) return;

        if (latestVersion.equals(ERROR_NO_RESPONSE)) {
            showApiErrorResponseSnackbar(rootView);
            return;
        }

        String appVersion = context.getString(R.string.github_tag_version);

        if (appVersion.compareTo(latestVersion) < 0) {
            showUpdateSnackbar(rootView, context);
            openGitHubReleaseLink(context);
        } else {
            showUpToDateSnackbar(rootView);
        }
    }

    private void handleUpdateError(Exception e) {
        Context context = contextRef.get();
        if (context != null) {
            View rootView = getRootView(context);
            if (rootView != null) {
                if (e instanceof IOException) {
                    showSnackbar(rootView, context.getString(R.string.error_connection) + e.getMessage());
                } else {
                    showSnackbar(rootView, context.getString(R.string.error_others) + e.getMessage());
                }
            }
        }
    }

    private String handleRequestError(IOException e) {
        Log.e(TAG, "Request error", e);
        return ERROR_NO_RESPONSE;
    }

    private void showUpdateSnackbar(View rootView, Context context) {
        String message = context.getString(R.string.update_available);
        showSnackbar(rootView, message);
    }

    private void showUpToDateSnackbar(View rootView) {
        String message = rootView.getContext().getString(R.string.no_update_available);
        showSnackbar(rootView, message);
    }

    private void showApiErrorResponseSnackbar(View rootView) {
        String message = rootView.getContext().getString(R.string.check_updates_error);
        showSnackbar(rootView, message);
    }

    private void showSnackbar(View rootView, String message) {
        new Handler(Looper.getMainLooper()).post(() -> Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show());
    }

    private void openGitHubReleaseLink(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASE_LINK));
        context.startActivity(intent);
    }

    private View getRootView(Context context) {
        if (context instanceof android.app.Activity) {
            return ((android.app.Activity) context).findViewById(android.R.id.content);
        }
        return null;
    }
}