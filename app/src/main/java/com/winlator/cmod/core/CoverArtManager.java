package com.winlator.cmod.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.BuildConfig;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.steam.SteamSearchResponse;
import com.winlator.cmod.core.steam.SteamStoreAPI;
import com.winlator.cmod.core.steamgrid.SteamGridDBApi;
import com.winlator.cmod.core.steamgrid.SteamGridGridsResponse;
import com.winlator.cmod.core.steamgrid.SteamGridHeroesResponse;
import com.winlator.cmod.core.steamgrid.SteamGridSearchResponse;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class CoverArtManager {
    private static final String BASE_URL = "https://www.steamgriddb.com/api/v2/";
    private static Retrofit retrofit;
    private static final java.util.concurrent.ExecutorService executorService = Executors.newSingleThreadExecutor();

    public enum ErrorReason {
        NETWORK_UNAVAILABLE,
        NOT_FOUND,
        UNAUTHORIZED,
        RATE_LIMITED,
        UNKNOWN
    }

    public interface DownloadCallback {
        void onCompleted(Bitmap bitmap);
        void onFailed(ErrorReason reason);
    }

    public interface GridOptionsCallback {
        void onOptionsAvailable(java.util.List<SteamGridGridsResponse.GridData> options);
        void onFailed(ErrorReason reason);
    }

    public interface HeroOptionsCallback {
        void onOptionsAvailable(java.util.List<SteamGridHeroesResponse.HeroData> options);
        void onFailed(ErrorReason reason);
    }

    public static synchronized Retrofit getRetrofit() {
        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    private static final String PCGW_BASE_URL = "https://www.pcgamingwiki.com/w/";
    private static Retrofit pcgwRetrofit;

    public static synchronized Retrofit getPCGWRetrofit() {
        if (pcgwRetrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                            .header("User-Agent", "Winlator/7.0.0 (https://github.com/winlator; contact@winlator.com)")
                            .build()))
                    .build();

            pcgwRetrofit = new Retrofit.Builder()
                    .baseUrl(PCGW_BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return pcgwRetrofit;
    }

    private static final String STEAM_STORE_URL = "https://store.steampowered.com/";
    private static Retrofit steamRetrofit;

    public static synchronized Retrofit getSteamRetrofit() {
        if (steamRetrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            steamRetrofit = new Retrofit.Builder()
                    .baseUrl(STEAM_STORE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return steamRetrofit;
    }

    public static String getSteamGridDBApiKey(Context context) {
        if (context != null) {
            android.content.SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            String mode = preferences.getString("steamgrid_api_key_mode", null);
            if ("manual".equals(mode)) {
                String customKey = preferences.getString("custom_api_key", "");
                return customKey != null ? customKey.trim() : "";
            }
            if (mode == null && preferences.getBoolean("enable_custom_api_key", false)) {
                String customKey = preferences.getString("custom_api_key", "");
                if (customKey != null && !customKey.trim().isEmpty()) return customKey.trim();
            }
        }
        return BuildConfig.STEAMGRID_DEFAULT_API_KEY;
    }

    public static void fetchAutomaticArt(Context context, Shortcut shortcut, Runnable onArtUpdated) {
        fetchArt(context, shortcut, onArtUpdated, true);
    }

    public static void fetchMissingArt(Context context, Shortcut shortcut, Runnable onArtUpdated) {
        fetchArt(context, shortcut, onArtUpdated, false);
    }

    private static void fetchArt(Context context, Shortcut shortcut, Runnable onArtUpdated,
                                 boolean respectAutomaticArtPreference) {
        if (context == null || shortcut == null) return;
        if (respectAutomaticArtPreference && !shortcut.shouldAutomaticallyFetchArt()) return;

        boolean needsCover = !hasCachedArt(shortcut.getCustomCoverArtPath());
        boolean needsHero = !hasCachedArt(shortcut.getCustomHeroArtPath());
        if (!needsCover && !needsHero) return;
        if (!AppUtils.isNetworkAvailable(context)) return;

        String query = cleanSearchName(shortcut.name);
        if (query.isEmpty()) return;

        String apiKey = getSteamGridDBApiKey(context);
        SteamGridDBApi api = getRetrofit().create(SteamGridDBApi.class);
        api.searchGame("Bearer " + apiKey, query).enqueue(new Callback<SteamGridSearchResponse>() {
            @Override
            public void onResponse(Call<SteamGridSearchResponse> call, Response<SteamGridSearchResponse> response) {
                SteamGridSearchResponse.GameData match = null;
                if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                    match = findSteamGridMatch(query, response.body().data);
                }
                if (match == null) {
                    newAutomaticArtLookup(context, shortcut, query, apiKey, needsCover, needsHero, onArtUpdated)
                            .fetchSteamFallback();
                    return;
                }
                new AutomaticArtLookup(context, shortcut, query, apiKey, needsCover, needsHero, onArtUpdated, match.id)
                        .fetchSteamGridArt();
            }

            @Override
            public void onFailure(Call<SteamGridSearchResponse> call, Throwable error) {
                android.util.Log.w("CoverArtManager", "SteamGridDB artwork lookup failed", error);
                newAutomaticArtLookup(context, shortcut, query, apiKey, needsCover, needsHero, onArtUpdated)
                        .fetchSteamFallback();
            }
        });
    }

    private static AutomaticArtLookup newAutomaticArtLookup(Context context, Shortcut shortcut, String query,
                                                               String apiKey, boolean needsCover, boolean needsHero,
                                                               Runnable onArtUpdated) {
        return new AutomaticArtLookup(context, shortcut, query, apiKey, needsCover, needsHero, onArtUpdated, 0);
    }

    private static boolean hasCachedArt(String path) {
        return path != null && !path.isEmpty() && new java.io.File(path).isFile();
    }

    private static String cleanSearchName(String name) {
        if (name == null) return "";
        return name.replaceAll("\\(.*?\\)", "").replaceAll("\\[.*?\\]", "").trim();
    }

    private static String normalizeGameName(String name) {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static SteamGridSearchResponse.GameData findSteamGridMatch(
            String query, java.util.List<SteamGridSearchResponse.GameData> games) {
        String normalizedQuery = normalizeGameName(query);
        SteamGridSearchResponse.GameData bestMatch = null;
        float bestScore = 0f;
        for (SteamGridSearchResponse.GameData game : games) {
            String candidate = normalizeGameName(game.name);
            float score = gameNameMatchScore(normalizedQuery, candidate);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = game;
            }
        }
        return bestScore >= 0.72f ? bestMatch : null;
    }

    private static SteamSearchResponse.SteamItem findSteamStoreMatch(
            String query, java.util.List<SteamSearchResponse.SteamItem> games) {
        String normalizedQuery = normalizeGameName(query);
        SteamSearchResponse.SteamItem bestMatch = null;
        float bestScore = 0f;
        for (SteamSearchResponse.SteamItem game : games) {
            String candidate = normalizeGameName(game.name);
            float score = gameNameMatchScore(normalizedQuery, candidate);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = game;
            }
        }
        return bestScore >= 0.72f ? bestMatch : null;
    }

    private static float gameNameMatchScore(String query, String candidate) {
        if (query.isEmpty() || candidate.isEmpty()) return 0f;
        if (query.equals(candidate)) return 1f;
        if (query.contains(candidate) || candidate.contains(query)) {
            return (float) Math.min(query.length(), candidate.length()) / Math.max(query.length(), candidate.length());
        }
        return 0f;
    }

    private static final class AutomaticArtLookup {
        private final Context context;
        private final Shortcut shortcut;
        private final String query;
        private final String apiKey;
        private final boolean needsCover;
        private final boolean needsHero;
        private final Runnable onArtUpdated;
        private final int steamGridGameId;
        private boolean coverFinished;
        private boolean heroFinished;
        private boolean coverFailed;
        private boolean heroFailed;
        private boolean fallbackStarted;

        private AutomaticArtLookup(Context context, Shortcut shortcut, String query, String apiKey,
                                   boolean needsCover, boolean needsHero, Runnable onArtUpdated, int steamGridGameId) {
            this.context = context.getApplicationContext();
            this.shortcut = shortcut;
            this.query = query;
            this.apiKey = apiKey;
            this.needsCover = needsCover;
            this.needsHero = needsHero;
            this.onArtUpdated = onArtUpdated;
            this.steamGridGameId = steamGridGameId;
            coverFinished = !needsCover;
            heroFinished = !needsHero;
        }

        private void fetchSteamGridArt() {
            SteamGridDBApi api = getRetrofit().create(SteamGridDBApi.class);
            if (needsCover) {
                api.getGridsByGameId("Bearer " + apiKey, steamGridGameId,
                        "no_logo,alternate,material,blurred", "600x900,342x482", "static")
                        .enqueue(new Callback<SteamGridGridsResponse>() {
                            @Override
                            public void onResponse(Call<SteamGridGridsResponse> call, Response<SteamGridGridsResponse> response) {
                                String imageUrl = response.isSuccessful() && response.body() != null
                                        && response.body().data != null && !response.body().data.isEmpty()
                                        ? response.body().data.get(0).url : null;
                                downloadGridImage(imageUrl, true);
                            }

                            @Override
                            public void onFailure(Call<SteamGridGridsResponse> call, Throwable error) {
                                android.util.Log.w("CoverArtManager", "SteamGridDB cover lookup failed", error);
                                completeArt(true, false);
                            }
                        });
            }
            if (needsHero) {
                api.getHeroesByGameId("Bearer " + apiKey, steamGridGameId,
                        "alternate,blurred,material", "1920x620,3840x1240,1600x650", "static")
                        .enqueue(new Callback<SteamGridHeroesResponse>() {
                            @Override
                            public void onResponse(Call<SteamGridHeroesResponse> call, Response<SteamGridHeroesResponse> response) {
                                String imageUrl = response.isSuccessful() && response.body() != null
                                        && response.body().data != null && !response.body().data.isEmpty()
                                        ? response.body().data.get(0).url : null;
                                downloadGridImage(imageUrl, false);
                            }

                            @Override
                            public void onFailure(Call<SteamGridHeroesResponse> call, Throwable error) {
                                android.util.Log.w("CoverArtManager", "SteamGridDB hero lookup failed", error);
                                completeArt(false, false);
                            }
                        });
            }
        }

        private void downloadGridImage(String imageUrl, boolean cover) {
            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                completeArt(cover, false);
                return;
            }
            BitmapSink sink = cover ? shortcut::saveCustomCoverArt : shortcut::saveCustomHeroArt;
            downloadInto(imageUrl, sink, new DownloadCallback() {
                @Override
                public void onCompleted(Bitmap bitmap) {
                    completeArt(cover, true);
                }

                @Override
                public void onFailed(ErrorReason reason) {
                    completeArt(cover, false);
                }
            });
        }

        private void completeArt(boolean cover, boolean success) {
            boolean startFallback;
            synchronized (this) {
                if (cover) {
                    if (coverFinished) return;
                    coverFinished = true;
                    coverFailed = !success;
                } else {
                    if (heroFinished) return;
                    heroFinished = true;
                    heroFailed = !success;
                }
                startFallback = coverFinished && heroFinished && (coverFailed || heroFailed) && !fallbackStarted;
                if (startFallback) fallbackStarted = true;
            }
            if (success && onArtUpdated != null) onArtUpdated.run();
            if (startFallback) fetchSteamFallback();
        }

        private void fetchSteamFallback() {
            final boolean fallbackCover;
            final boolean fallbackHero;
            synchronized (this) {
                fallbackCover = coverFailed || (steamGridGameId == 0 && needsCover);
                fallbackHero = heroFailed || (steamGridGameId == 0 && needsHero);
            }
            if (!fallbackCover && !fallbackHero) return;
            if (!AppUtils.isNetworkAvailable(context)) return;

            SteamStoreAPI api = getSteamRetrofit().create(SteamStoreAPI.class);
            api.search(query, "english", "US").enqueue(new Callback<SteamSearchResponse>() {
                @Override
                public void onResponse(Call<SteamSearchResponse> call, Response<SteamSearchResponse> response) {
                    SteamSearchResponse.SteamItem match = response.isSuccessful() && response.body() != null
                            && response.body().items != null ? findSteamStoreMatch(query, response.body().items) : null;
                    if (match == null || match.id <= 0) return;

                    shortcut.putExtra("steam_id", Integer.toString(match.id));
                    shortcut.saveData();
                    String steamBase = "https://cdn.akamai.steamstatic.com/steam/apps/" + match.id + "/";
                    if (fallbackCover) {
                        downloadFirstAvailable(new String[]{steamBase + "library_600x900.jpg", match.tinyImage},
                                0, shortcut::saveCustomCoverArt, onArtUpdated);
                    }
                    if (fallbackHero) {
                        downloadFirstAvailable(new String[]{steamBase + "library_hero.jpg", steamBase + "header.jpg"},
                                0, shortcut::saveCustomHeroArt, onArtUpdated);
                    }
                }

                @Override
                public void onFailure(Call<SteamSearchResponse> call, Throwable error) {
                    android.util.Log.w("CoverArtManager", "Steam Store artwork fallback failed", error);
                }
            });
        }
    }

    private static void downloadFirstAvailable(String[] urls, int index, BitmapSink sink, Runnable onArtUpdated) {
        if (index >= urls.length) return;
        String url = urls[index];
        if (url == null || url.trim().isEmpty()) {
            downloadFirstAvailable(urls, index + 1, sink, onArtUpdated);
            return;
        }
        downloadInto(url, sink, new DownloadCallback() {
            @Override
            public void onCompleted(Bitmap bitmap) {
                if (onArtUpdated != null) onArtUpdated.run();
            }

            @Override
            public void onFailed(ErrorReason reason) {
                downloadFirstAvailable(urls, index + 1, sink, onArtUpdated);
            }
        });
    }

    public static void fetchCoverArtOptions(Context context, Shortcut shortcut, String searchQuery, GridOptionsCallback callback) {
        fetchCoverArtOptions(context, shortcut, searchQuery, false, callback);
    }

    public static void fetchCoverArtOptions(Context context, Shortcut shortcut, String searchQuery,
                                            boolean horizontal, GridOptionsCallback callback) {
        if (context == null || shortcut == null) return;

        if (!AppUtils.isNetworkAvailable(context)) {
            if (callback != null) callback.onFailed(ErrorReason.NETWORK_UNAVAILABLE);
            return;
        }

        final String apiKey = getSteamGridDBApiKey(context);
        SteamGridDBApi api = getRetrofit().create(SteamGridDBApi.class);

        String searchName = (searchQuery != null && !searchQuery.isEmpty()) ? searchQuery : 
                           (shortcut.name != null ? shortcut.name.replaceAll("\\(.*?\\)", "").replaceAll("\\[.*?\\]", "").trim() : "");

        if (searchName.isEmpty()) {
            if (callback != null) callback.onFailed(ErrorReason.NOT_FOUND);
            return;
        }

        api.searchGame("Bearer " + apiKey, searchName).enqueue(new Callback<SteamGridSearchResponse>() {
            @Override
            public void onResponse(Call<SteamGridSearchResponse> call, Response<SteamGridSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                    int gameId = response.body().data.get(0).id;
                    String dimensions = horizontal ? "460x215,920x430,512x512" : "600x900,342x482";
                    api.getGridsByGameId("Bearer " + apiKey, gameId, "no_logo,alternate,material,blurred", dimensions, "static").enqueue(new Callback<SteamGridGridsResponse>() {
                        @Override
                        public void onResponse(Call<SteamGridGridsResponse> call, Response<SteamGridGridsResponse> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                                if (callback != null) callback.onOptionsAvailable(response.body().data);
                            } else {
                                if (response.code() == 429) {
                                    if (callback != null) callback.onFailed(ErrorReason.RATE_LIMITED);
                                    return;
                                }
                                api.getGridsByGameId("Bearer " + apiKey, gameId, null, null, "static").enqueue(new Callback<SteamGridGridsResponse>() {
                                    @Override
                                    public void onResponse(Call<SteamGridGridsResponse> call, Response<SteamGridGridsResponse> response) {
                                        if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                                            if (callback != null) callback.onOptionsAvailable(response.body().data);
                                        } else if (callback != null) {
                                            if (response.code() == 429) callback.onFailed(ErrorReason.RATE_LIMITED);
                                            else callback.onFailed(ErrorReason.NOT_FOUND);
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<SteamGridGridsResponse> call, Throwable t) {
                                        if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
                                    }
                                });
                            }
                        }

                        @Override
                        public void onFailure(Call<SteamGridGridsResponse> call, Throwable t) {
                            if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
                        }
                    });
                } else if (callback != null) {
                    if (response.code() == 401) callback.onFailed(ErrorReason.UNAUTHORIZED);
                    else if (response.code() == 429) callback.onFailed(ErrorReason.RATE_LIMITED);
                    else callback.onFailed(ErrorReason.NOT_FOUND);
                }
            }

            @Override
            public void onFailure(Call<SteamGridSearchResponse> call, Throwable t) {
                if (callback != null) callback.onFailed(ErrorReason.NETWORK_UNAVAILABLE);
            }
        });
    }

    public static void downloadSelectedCoverArt(String url, Shortcut shortcut, DownloadCallback callback) {
        downloadInto(url, bitmap -> shortcut.saveCustomCoverArt(bitmap), callback);
    }

    public static void fetchHeroArtOptions(Context context, Shortcut shortcut, String searchQuery, HeroOptionsCallback callback) {
        if (context == null || shortcut == null) return;

        if (!AppUtils.isNetworkAvailable(context)) {
            if (callback != null) callback.onFailed(ErrorReason.NETWORK_UNAVAILABLE);
            return;
        }

        final String apiKey = getSteamGridDBApiKey(context);
        SteamGridDBApi api = getRetrofit().create(SteamGridDBApi.class);

        String searchName = (searchQuery != null && !searchQuery.isEmpty()) ? searchQuery :
                           (shortcut.name != null ? shortcut.name.replaceAll("\\(.*?\\)", "").replaceAll("\\[.*?\\]", "").trim() : "");

        if (searchName.isEmpty()) {
            if (callback != null) callback.onFailed(ErrorReason.NOT_FOUND);
            return;
        }

        api.searchGame("Bearer " + apiKey, searchName).enqueue(new Callback<SteamGridSearchResponse>() {
            @Override
            public void onResponse(Call<SteamGridSearchResponse> call, Response<SteamGridSearchResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                    int gameId = response.body().data.get(0).id;
                    // 1920x620 is SteamGridDB's standard wide hero size (also offered at 2x/other wide ratios); our
                    // fullscreen loading screen center-crops whatever comes back, so the exact ratio is not critical.
                    api.getHeroesByGameId("Bearer " + apiKey, gameId, "alternate,blurred,material", "1920x620,3840x1240,1600x650", "static").enqueue(new Callback<SteamGridHeroesResponse>() {
                        @Override
                        public void onResponse(Call<SteamGridHeroesResponse> call, Response<SteamGridHeroesResponse> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                                if (callback != null) callback.onOptionsAvailable(response.body().data);
                            } else {
                                if (response.code() == 429) {
                                    if (callback != null) callback.onFailed(ErrorReason.RATE_LIMITED);
                                    return;
                                }
                                api.getHeroesByGameId("Bearer " + apiKey, gameId, null, null, "static").enqueue(new Callback<SteamGridHeroesResponse>() {
                                    @Override
                                    public void onResponse(Call<SteamGridHeroesResponse> call, Response<SteamGridHeroesResponse> response) {
                                        if (response.isSuccessful() && response.body() != null && response.body().data != null && !response.body().data.isEmpty()) {
                                            if (callback != null) callback.onOptionsAvailable(response.body().data);
                                        } else if (callback != null) {
                                            if (response.code() == 429) callback.onFailed(ErrorReason.RATE_LIMITED);
                                            else callback.onFailed(ErrorReason.NOT_FOUND);
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<SteamGridHeroesResponse> call, Throwable t) {
                                        if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
                                    }
                                });
                            }
                        }

                        @Override
                        public void onFailure(Call<SteamGridHeroesResponse> call, Throwable t) {
                            if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
                        }
                    });
                } else if (callback != null) {
                    if (response.code() == 401) callback.onFailed(ErrorReason.UNAUTHORIZED);
                    else if (response.code() == 429) callback.onFailed(ErrorReason.RATE_LIMITED);
                    else callback.onFailed(ErrorReason.NOT_FOUND);
                }
            }

            @Override
            public void onFailure(Call<SteamGridSearchResponse> call, Throwable t) {
                if (callback != null) callback.onFailed(ErrorReason.NETWORK_UNAVAILABLE);
            }
        });
    }

    public static void downloadSelectedHeroArt(String url, Shortcut shortcut, DownloadCallback callback) {
        downloadInto(url, bitmap -> shortcut.saveCustomHeroArt(bitmap), callback);
    }

    public static void downloadSelectedArt(String url, Shortcut shortcut, boolean vertical,
                                           boolean background, DownloadCallback callback) {
        downloadInto(url, bitmap -> {
            if (!shortcut.saveShortcutArt(bitmap, vertical, background)) {
                throw new IllegalStateException("Could not save selected artwork.");
            }
        }, callback);
    }

    private interface BitmapSink {
        void accept(Bitmap bitmap);
    }

    private static void downloadInto(String url, BitmapSink sink, DownloadCallback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (InputStream input = connection.getInputStream()) {
                        Bitmap bitmap = BitmapFactory.decodeStream(input);
                        if (bitmap != null) {
                            try {
                                sink.accept(bitmap);
                                if (callback != null) callback.onCompleted(bitmap);
                            } catch (RuntimeException e) {
                                Log.e("CoverArtManager", "Failed to save downloaded artwork", e);
                                if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
                            }
                        } else if (callback != null) callback.onFailed(ErrorReason.UNKNOWN);
                    }
                } else if (callback != null) {
                    callback.onFailed(responseCode == 429 ? ErrorReason.RATE_LIMITED : ErrorReason.UNKNOWN);
                }
            } catch (Exception e) {
                Log.e("CoverArtManager", "Failed to download art", e);
                if (callback != null) {
                    callback.onFailed(e instanceof java.io.IOException ? ErrorReason.NETWORK_UNAVAILABLE : ErrorReason.UNKNOWN);
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
