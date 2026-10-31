package com.reseep.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reseep.app.api.ApiService;
import com.reseep.app.api.BoughtRequest;
import com.reseep.app.api.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardActivity extends AppCompatActivity {

    private LinearLayout pantryContainer;
    private LinearLayout shoppingContainer;
    private TextView welcomeText;
    private TextView dashboardStatus;
    private Button refreshButton;

    private ApiService apiService;
    private int userId;
    private String username;

    private final int GREEN = Color.rgb(23, 107, 80);
    private final int DARK = Color.rgb(35, 59, 48);
    private final int MUTED = Color.rgb(112, 130, 119);
    private final int LIGHT_GREEN = Color.rgb(237, 246, 240);

    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final int MAX_PANTRY_CHECKS = 10;
    private static final long PANTRY_CHECK_DELAY = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        pantryContainer = findViewById(R.id.pantryContainer);
        shoppingContainer = findViewById(R.id.shoppingContainer);
        welcomeText = findViewById(R.id.welcomeText);
        dashboardStatus = findViewById(R.id.dashboardStatus);
        refreshButton = findViewById(R.id.refreshButton);

        userId = getSharedPreferences("ReSeeP", MODE_PRIVATE)
                .getInt("userId", -1);

        username = getSharedPreferences("ReSeeP", MODE_PRIVATE)
                .getString("username", "Shopper");

        apiService = RetrofitClient.getApiService();

        welcomeText.setText("Welcome back, " + username + "!");

        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadDashboard();
            }
        });

        loadDashboard();
    }

    private void loadDashboard() {
        dashboardStatus.setVisibility(View.VISIBLE);
        dashboardStatus.setText("Loading your groceries...");
        pantryContainer.removeAllViews();
        shoppingContainer.removeAllViews();

        loadPantry();
        loadShoppingList();
    }

    private void loadPantry() {
        apiService.getPantry().enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(Call<JsonArray> call,
                                   Response<JsonArray> response) {

                if (response.isSuccessful() && response.body() != null) {
                    JsonArray items = response.body();

                    if (items.size() == 0) {
                        addMessage(pantryContainer, "Your pantry is empty.");
                        return;
                    }

                    for (int i = 0; i < items.size(); i++) {
                        JsonObject item = items.get(i).getAsJsonObject();

                        String name = item.get("name").getAsString();
                        String quantity = item.get("qtyOnHand").getAsString();
                        String unit = item.get("unit").getAsString();

                        addPantryItem(name, quantity + " " + unit);
                    }

                } else {
                    addMessage(pantryContainer, "Could not load pantry.");
                }
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {
                addMessage(pantryContainer, "Pantry error: " + t.getMessage());
                dashboardStatus.setText("Pantry connection error.");
            }
        });
    }

    private void loadShoppingList() {
        apiService.getShoppingList().enqueue(new Callback<JsonArray>() {

            @Override
            public void onResponse(Call<JsonArray> call,
                                   Response<JsonArray> response) {

                if (response.isSuccessful() && response.body() != null) {
                    JsonArray items = response.body();
                    int activeCount = 0;

                    for (int i = 0; i < items.size(); i++) {
                        JsonObject item = items.get(i).getAsJsonObject();

                        String status = item.get("status").getAsString();

                        if ("NEEDED".equalsIgnoreCase(status)) {
                            activeCount++;

                            String id = item.get("id").getAsString();
                            int ingredientId = item.get("ingredientId").getAsInt();
                            double amount = item.get("qty").getAsDouble();

                            String name = item.get("ingredientName").getAsString();
                            String quantity = item.get("qty").getAsString();
                            String unit = item.get("unit").getAsString();

                            addShoppingItem(id, ingredientId, amount,
                                    name, quantity + " " + unit);
                        }
                    }

                    if (activeCount == 0) {
                        addMessage(shoppingContainer,
                                "You're all caught up! No items needed.");
                    }

                    dashboardStatus.setText(
                            activeCount + " item(s) on your shopping list");

                } else {
                    addMessage(shoppingContainer, "Could not load shopping list.");
                }
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {
                addMessage(shoppingContainer, "Shopping error: " + t.getMessage());
                dashboardStatus.setText("Shopping connection error.");
            }
        });
    }

    private void addPantryItem(String name, String quantity) {
        LinearLayout row = createRow();

        TextView nameText = createText(name, 15, DARK, true);
        TextView quantityText = createText(quantity, 13, MUTED, false);

        row.addView(nameText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        row.addView(quantityText);

        pantryContainer.addView(row);
        addDivider(pantryContainer);
    }

    private void addShoppingItem(final String id, final int ingredientId, final double amount, String name, String quantity) {
        LinearLayout row = createRow();

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);

        TextView nameText = createText(name, 15, DARK, true);
        TextView quantityText = createText(quantity, 13, MUTED, false);

        details.addView(nameText);
        details.addView(quantityText);

        row.addView(details, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button boughtButton = new Button(this);
        boughtButton.setText("Bought");
        boughtButton.setTextSize(12);
        boughtButton.setAllCaps(false);
        boughtButton.setTextColor(Color.WHITE);
        boughtButton.setPadding(18, 0, 18, 0);
        boughtButton.setBackground(makeBackground(GREEN, 22));

        boughtButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boughtButton.setEnabled(false);
                boughtButton.setText("Updating...");
                markBought(id, ingredientId, amount, boughtButton);
            }
        });

        row.addView(boughtButton);
        shoppingContainer.addView(row);
        addDivider(shoppingContainer);
    }

    private void markBought(final String id,
                            final int ingredientId,
                            final double boughtQty,
                            final Button button) {

        dashboardStatus.setText("Checking pantry...");

        // Get the pantry quantity before the purchase.
        apiService.getPantry().enqueue(new Callback<JsonArray>() {
            @Override
            public void onResponse(Call<JsonArray> call,
                                   Response<JsonArray> response) {

                if (!response.isSuccessful() || response.body() == null) {
                    button.setEnabled(true);
                    button.setText("Bought");
                    dashboardStatus.setText("Could not check pantry.");
                    return;
                }

                double previousQty = findPantryQuantity(
                        response.body(), ingredientId);

                if (Double.isNaN(previousQty)) {
                    button.setEnabled(true);
                    button.setText("Bought");
                    dashboardStatus.setText("Ingredient not found in pantry.");
                    return;
                }

                final double expectedQty = previousQty + boughtQty;

                // Submit the purchase exactly once.
                apiService.markBought(id, new BoughtRequest(userId))
                        .enqueue(new Callback<JsonObject>() {

                            @Override
                            public void onResponse(Call<JsonObject> call,
                                                   Response<JsonObject> response) {

                                if (response.isSuccessful()) {
                                    button.setText("Processing...");
                                    dashboardStatus.setText(
                                            "Purchase recorded. Updating pantry...");

                                    waitForPantryUpdate(
                                            ingredientId, expectedQty, 0, button);
                                } else {
                                    button.setEnabled(true);
                                    button.setText("Bought");
                                    dashboardStatus.setText(
                                            "Purchase failed. Please try again.");
                                }
                            }

                            @Override
                            public void onFailure(Call<JsonObject> call,
                                                  Throwable t) {
                                button.setEnabled(true);
                                button.setText("Bought");
                                dashboardStatus.setText(
                                        "Connection error. Please try again.");
                            }
                        });
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {
                button.setEnabled(true);
                button.setText("Bought");
                dashboardStatus.setText("Could not check pantry.");
            }
        });
    }


    // Find the current quantity of an ingredient.
    private double findPantryQuantity(JsonArray items, int ingredientId) {

        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.get(i).getAsJsonObject();

            if (item.get("id").getAsInt() == ingredientId) {
                return item.get("qtyOnHand").getAsDouble();
            }
        }

        return Double.NaN;
    }


    // Wait until the consumer has updated the pantry.
    private void waitForPantryUpdate(final int ingredientId,
                                     final double expectedQty,
                                     final int attempt,
                                     final Button button) {

        if (attempt >= MAX_PANTRY_CHECKS) {
            button.setText("Recorded");
            button.setEnabled(false);

            dashboardStatus.setText(
                    "Purchase recorded, but pantry update is delayed. " +
                            "Please refresh shortly.");

            return;
        }

        apiService.getPantry().enqueue(new Callback<JsonArray>() {

            @Override
            public void onResponse(Call<JsonArray> call,
                                   Response<JsonArray> response) {

                if (response.isSuccessful() && response.body() != null) {

                    double currentQty = findPantryQuantity(
                            response.body(), ingredientId);

                    if (!Double.isNaN(currentQty)
                            && currentQty >= expectedQty - 0.0001) {

                        // Pantry update confirmed. Refresh everything.
                        loadDashboard();
                        return;
                    }
                }

                scheduleNextPantryCheck(
                        ingredientId, expectedQty, attempt, button);
            }

            @Override
            public void onFailure(Call<JsonArray> call, Throwable t) {

                scheduleNextPantryCheck(
                        ingredientId, expectedQty, attempt, button);
            }
        });
    }


    private void scheduleNextPantryCheck(final int ingredientId,
                                         final double expectedQty,
                                         final int attempt,
                                         final Button button) {

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                waitForPantryUpdate(
                        ingredientId, expectedQty, attempt + 1, button);
            }
        }, PANTRY_CHECK_DELAY);
    }

    private LinearLayout createRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(12, 14, 12, 14);
        return row;
    }

    private TextView createText(String value, int size, int color,
                                boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);

        if (bold) {
            text.setTypeface(null, Typeface.BOLD);
        }

        return text;
    }

    private void addMessage(LinearLayout container, String message) {
        TextView text = createText(message, 14, MUTED, false);
        text.setPadding(12, 18, 12, 18);
        container.addView(text);
    }

    private void addDivider(LinearLayout container) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(232, 238, 233));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);

        container.addView(divider, params);
    }

    private GradientDrawable makeBackground(int color, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }
}