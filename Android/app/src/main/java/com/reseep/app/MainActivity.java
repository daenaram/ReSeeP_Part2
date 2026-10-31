package com.reseep.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.reseep.app.api.ApiService;
import com.reseep.app.api.LoginRequest;
import com.reseep.app.api.LoginResponse;
import com.reseep.app.api.RetrofitClient;
import android.content.Intent;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private EditText usernameInput;
    private EditText passwordInput;
    private Button loginButton;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        statusText = findViewById(R.id.statusText);

        loginButton.setOnClickListener(view -> login());
    }

    private void login() {

        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            showMessage("Please enter your username and password.");
            return;
        }

        loginButton.setEnabled(false);
        loginButton.setText("Signing in...");
        statusText.setVisibility(TextView.VISIBLE);
        statusText.setText("Connecting to server...");
        statusText.setTextColor(0xFF718078);

        ApiService apiService = RetrofitClient.getApiService();

        LoginRequest request = new LoginRequest(username, password);

        apiService.login(request).enqueue(new Callback<LoginResponse>() {

            @Override
            public void onResponse(Call<LoginResponse> call,
                                   Response<LoginResponse> response) {

                loginButton.setEnabled(true);
                loginButton.setText("Sign In");

                if (response.isSuccessful() && response.body() != null) {

                    LoginResponse user = response.body();

                    if (!"SHOPPER".equalsIgnoreCase(user.getRole())) {
                        showMessage("This app is currently for shoppers.");
                        return;
                    }

                    getSharedPreferences("ReSeeP", MODE_PRIVATE)
                            .edit()
                            .putInt("userId", user.getId())
                            .putString("username", user.getUsername())
                            .putString("role", user.getRole())
                            .apply();

                    Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
                    startActivity(intent);
                    finish();

                } else {
                    showMessage("Invalid username or password.");
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable throwable) {

                loginButton.setEnabled(true);
                loginButton.setText("Sign In");

                showMessage("Unable to connect. Please check your server.");
            }
        });
    }

    private void showMessage(String message) {
        statusText.setVisibility(TextView.VISIBLE);
        statusText.setTextColor(0xFFB3261E);
        statusText.setText(message);
    }
}