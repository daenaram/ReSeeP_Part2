package com.reseep.app.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {

    @POST("login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @GET("pantry")
    Call<JsonArray> getPantry();

    @GET("shopping")
    Call<JsonArray> getShoppingList();

    @POST("shopping/{id}/bought")
    Call<JsonObject> markBought(
            @Path("id") String id,
            @Body BoughtRequest request
    );
}