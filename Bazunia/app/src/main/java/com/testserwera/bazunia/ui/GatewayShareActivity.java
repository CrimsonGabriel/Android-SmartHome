package com.testserwera.bazunia.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
// import androidx.appcompat.app.AppCompatActivity; // ZMIANA: Niepotrzebne
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.testserwera.bazunia.R;
import com.testserwera.bazunia.data.Gateway;
import com.testserwera.bazunia.utils.Constants;

import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// ZMIANA: BaseActivity
public class GatewayShareActivity extends BaseActivity {

    private static final String TAG = "GatewayShareActivity";
    private SharedPreferences authPrefs;
    private OkHttpClient httpClient;
    private Gson gson;

    // UI Components
    private AutoCompleteTextView spinnerGateways;
    private AutoCompleteTextView spinnerUsers;

    private RadioButton radioFull;
    private MaterialButton btnShare;

    private ProgressBar progressBar;
    private TextView txtEmptyList;

    private List<Gateway> myGateways = new ArrayList<>();
    private List<UserPickDto> availableUsers = new ArrayList<>();
    private ShareAdapter adapter;

    private Gateway selectedGateway;
    private UserPickDto selectedUser;
    private Long currentUserId;

    // ZMIANA: Usunięto attachBaseContext

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // BaseActivity załatwia motywy
        setContentView(R.layout.activity_gateway_share);

        authPrefs = getSharedPreferences(LoginActivity.AUTH_PREFS, Context.MODE_PRIVATE);
        httpClient = new OkHttpClient();
        gson = new Gson();

        initViews();
        fetchCurrentUserAndData();
    }

    private void initViews() {
        MaterialButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        spinnerGateways = findViewById(R.id.spinnerGateways);
        spinnerUsers = findViewById(R.id.spinnerUsers);

        radioFull = findViewById(R.id.radioFull);
        btnShare = findViewById(R.id.btnShare);

        RecyclerView recyclerShares = findViewById(R.id.recyclerShares);

        progressBar = findViewById(R.id.progressBar);
        txtEmptyList = findViewById(R.id.txtEmptyList);

        recyclerShares.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ShareAdapter(new ArrayList<>(), this::deleteShare);
        recyclerShares.setAdapter(adapter);

        btnShare.setOnClickListener(v -> performShare());

        spinnerGateways.setOnItemClickListener((parent, view, position, id) -> {
            selectedGateway = myGateways.get(position);
            Log.d(TAG, "Wybrano bramkę: " + selectedGateway.getName());
            fetchSharesForGateway(selectedGateway.getId());
        });

        spinnerUsers.setOnItemClickListener((parent, view, position, id) -> selectedUser = availableUsers.get(position));
    }

    // --- KROK 1: Pobierz ID Usera ---
    private void fetchCurrentUserAndData() {
        progressBar.setVisibility(View.VISIBLE);
        String token = getJwtToken();
        if(token == null) {
            showError("Brak tokena logowania. Zaloguj się ponownie.");
            return;
        }

        Log.d(TAG, "Pobieranie danych usera... Token: " + token.substring(0, 10) + "...");

        Request request = new Request.Builder()
                .url(Constants.USER_ME_ENDPOINT)
                .addHeader("Authorization", "Bearer " + token)
                .get().build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                showError("Błąd sieci (User): " + e.getMessage());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        currentUserId = json.getLong("id");
                        Log.d(TAG, "Zalogowany user ID: " + currentUserId);

                        fetchGateways();
                        fetchAvailableUsers();

                    } catch (Exception e) {
                        showError("Błąd parsowania Usera: " + e.getMessage());
                    }
                } else {
                    showError("Błąd pobierania Usera: " + response.code());
                }
            }
        });
    }

    // --- KROK 2: Pobierz bramki (FILTROWANIE WŁAŚCICIELA) ---
    private void fetchGateways() {
        String token = getJwtToken();
        Request request = new Request.Builder()
                .url(Constants.GATEWAYS_ENDPOINT)
                .addHeader("Authorization", "Bearer " + token)
                .get().build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Błąd pobierania bramek: " + e.getMessage());
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "Pobrano bramki: " + json);

                    Type listType = new TypeToken<List<Gateway>>() {}.getType();
                    List<Gateway> allGateways = gson.fromJson(json, listType);

                    List<Gateway> ownedGateways = allGateways.stream()
                            .filter(g -> g.getOwnerId() != null && g.getOwnerId().equals(currentUserId))
                            .collect(Collectors.toList());

                    runOnUiThread(() -> setupGatewaySpinner(ownedGateways));
                } else {
                    Log.e(TAG, "Błąd API bramek: " + response.code());
                }
            }
        });
    }

    // --- KROK 3: Pobierz listę userów do wyboru ---
    private void fetchAvailableUsers() {
        String token = getJwtToken();
        Request request = new Request.Builder()
                .url(Constants.USERS_AVAILABLE_SHARE_ENDPOINT)
                .addHeader("Authorization", "Bearer " + token)
                .get().build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Błąd pobierania userów: " + e.getMessage());
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    Log.d(TAG, "Pobrano userów: " + json);

                    Type listType = new TypeToken<List<UserPickDto>>() {}.getType();
                    List<UserPickDto> users = gson.fromJson(json, listType);
                    runOnUiThread(() -> setupUserSpinner(users));
                } else {
                    Log.e(TAG, "Błąd API userów: " + response.code());
                }
            }
        });
    }

    // --- KROK 4: Pobierz udziały ---
    private void fetchSharesForGateway(Long gatewayId) {
        progressBar.setVisibility(View.VISIBLE);
        String token = getJwtToken();
        String url = Constants.GATEWAY_SHARES_BASE + "/" + gatewayId + "/shares";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .get().build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    Type listType = new TypeToken<List<SharedUserDto>>() {}.getType();
                    List<SharedUserDto> shares = gson.fromJson(json, listType);
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        adapter.updateData(shares);
                        txtEmptyList.setVisibility(shares.isEmpty() ? View.VISIBLE : View.GONE);
                    });
                } else {
                    runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                }
            }
        });
    }

    // --- AKCJA: Udostępnij ---
    private void performShare() {
        if (selectedGateway == null) {
            Toast.makeText(this, "Wybierz bramkę!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedUser == null) {
            Toast.makeText(this, "Wybierz użytkownika!", Toast.LENGTH_SHORT).show();
            return;
        }

        String permission = radioFull.isChecked() ? "FULL_ACCESS" : "VIEW";

        JSONObject json = new JSONObject();
        try {
            json.put("gatewayId", selectedGateway.getId());
            json.put("targetUserId", selectedUser.id);
            json.put("permissionLevel", permission);
        } catch (Exception e) { return; }

        String token = getJwtToken();
        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(Constants.GATEWAY_SHARE_ENDPOINT)
                .addHeader("Authorization", "Bearer " + token)
                .post(body).build();

        progressBar.setVisibility(View.VISIBLE);
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(GatewayShareActivity.this, "Błąd sieci", Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (response.isSuccessful()) {
                        Toast.makeText(GatewayShareActivity.this, "Sukces!", Toast.LENGTH_SHORT).show();
                        fetchSharesForGateway(selectedGateway.getId());
                        // Reset wyboru usera
                        spinnerUsers.setText("");
                        selectedUser = null;
                    } else {
                        Toast.makeText(GatewayShareActivity.this, "Błąd: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void deleteShare(SharedUserDto share) {
        new AlertDialog.Builder(this)
                .setTitle("Potwierdzenie")
                .setMessage("Zabrać dostęp użytkownikowi " + share.email + "?")
                .setPositiveButton("Usuń", (d, w) -> executeDelete(share.userId))
                .setNegativeButton("Anuluj", null)
                .show();
    }

    private void executeDelete(Long userId) {
        if (selectedGateway == null) return;
        String token = getJwtToken();
        String url = Constants.GATEWAY_SHARES_BASE + "/" + selectedGateway.getId() + "/share/" + userId;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .delete().build();

        progressBar.setVisibility(View.VISIBLE);
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (response.isSuccessful()) {
                        Toast.makeText(GatewayShareActivity.this, "Usunięto dostęp", Toast.LENGTH_SHORT).show();
                        fetchSharesForGateway(selectedGateway.getId());
                    } else {
                        Toast.makeText(GatewayShareActivity.this, "Błąd usuwania", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // --- UI Helpers ---
    private void setupGatewaySpinner(List<Gateway> gateways) {
        this.myGateways = gateways;
        if (gateways.isEmpty()) {
            Toast.makeText(this, "Nie jesteś właścicielem żadnej bramki.", Toast.LENGTH_LONG).show();
            btnShare.setEnabled(false);
            return;
        }
        ArrayAdapter<Gateway> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, gateways);
        spinnerGateways.setAdapter(adapter);

        if (!gateways.isEmpty()) {
            selectedGateway = gateways.get(0);
            spinnerGateways.setText(selectedGateway.toString(), false);
            fetchSharesForGateway(selectedGateway.getId());
        }
    }

    private void setupUserSpinner(List<UserPickDto> users) {
        this.availableUsers = users;
        if (users.isEmpty()) {
            Log.w(TAG, "Lista użytkowników do wyboru jest pusta.");
        }
        ArrayAdapter<UserPickDto> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, users);
        spinnerUsers.setAdapter(adapter);
    }

    private void showError(String msg) {
        Log.e(TAG, msg);
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
    }

    private String getJwtToken() {
        return authPrefs.getString(LoginActivity.KEY_JWT_TOKEN, null);
    }

    // --- DTO ---
    private static class UserPickDto {
        long id;
        String email;

        @NonNull
        @Override public String toString() { return email; }
    }

    private static class SharedUserDto {
        long userId;
        String email;
        String permissionLevel;
    }

    // --- Adapter ---
    private static class ShareAdapter extends RecyclerView.Adapter<ShareAdapter.ViewHolder> {
        private List<SharedUserDto> list;
        private final OnDeleteListener listener;

        interface OnDeleteListener { void onDelete(SharedUserDto item); }

        public ShareAdapter(List<SharedUserDto> list, OnDeleteListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @SuppressLint("NotifyDataSetChanged")
        void updateData(List<SharedUserDto> newList) {
            this.list = newList;
            notifyDataSetChanged();
        }

        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shared_user, parent, false);
            return new ViewHolder(v);
        }

        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SharedUserDto item = list.get(position);
            holder.txtEmail.setText(item.email);
            holder.txtPerm.setText(item.permissionLevel);
            holder.btnDelete.setOnClickListener(v -> listener.onDelete(item));
        }

        @Override public int getItemCount() { return list.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtEmail, txtPerm;
            ImageButton btnDelete;
            ViewHolder(View v) {
                super(v);
                txtEmail = v.findViewById(R.id.txtUserEmail);
                txtPerm = v.findViewById(R.id.txtPermission);
                btnDelete = v.findViewById(R.id.btnDeleteShare);
            }
        }
    }
}