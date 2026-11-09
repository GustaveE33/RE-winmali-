package com.winlator.cmod.core.steam;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SteamSearchResponse {
    @SerializedName("total")
    public int total;

    @SerializedName("items")
    public List<SteamItem> items;

    public static class SteamItem {
        @SerializedName("id")
        public int id;

        @SerializedName("name")
        public String name;

        @SerializedName("tiny_image")
        public String tinyImage;
    }
}
