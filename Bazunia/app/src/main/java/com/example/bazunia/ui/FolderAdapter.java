package com.example.bazunia.ui;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bazunia.R;
import com.example.bazunia.data.DatabaseHelper;

import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SECTION_HEADER = 0;
    private static final int VIEW_TYPE_FOLDER = 1;
    private static final int VIEW_TYPE_GATEWAY = 2;
    private static final int VIEW_TYPE_SENSOR = 3;

    private final Context context;
    private final List<Object> displayItems;
    private final FolderCallback callback;

    public interface FolderCallback {
        void onFolderClicked(FolderItem folder);
        void onGatewayClicked(GatewayItem gateway);
        void onSensorClicked(SensorItem sensor);
        void onFolderLongClicked(FolderItem folder, View view);
        void onGatewayLongClicked(GatewayItem gateway, View view);
        void onSensorLongClicked(SensorItem sensor, View view);
    }

    // --- Modele Widoków ---

    public static class SectionHeader {
        final String title;
        public SectionHeader(String title) { this.title = title; }
    }

    // ⭐️ ZMIANA: Dodano drugi konstruktor dla "Niezgrupowane" ⭐️
    public static class FolderItem {
        final long id;
        final String name;
        final String color; // Będzie null dla "Niezgrupowane"
        boolean isExpanded;

        // Istniejący konstruktor
        public FolderItem(Cursor cursor, boolean isExpanded) {
            this.id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            this.name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.F_COLUMN_NAME));
            this.color = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.F_COLUMN_COLOR));
            this.isExpanded = isExpanded;
        }

        // ⭐️ NOWY KONSTRUKTOR (dla "Niezgrupowane") ⭐️
        public FolderItem(long id, String name, boolean isExpanded) {
            this.id = id;
            this.name = name;
            this.color = null; // Specjalny folder nie ma koloru
            this.isExpanded = isExpanded;
        }
    }

    public static class GatewayItem {
        final long id;
        final String name;
        final String status;
        final String description;
        final boolean isSensorParent;
        boolean isExpanded;
        final long parentFolderId;

        public GatewayItem(Cursor cursor, long parentFolderId, boolean isSensorParent, boolean isExpanded) {
            this.id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_ID));
            this.name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_NAME));
            this.status = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_STATUS));
            this.description = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_DESCRIPTION));
            this.parentFolderId = parentFolderId;
            this.isSensorParent = isSensorParent;
            this.isExpanded = isExpanded;
        }
    }

    public static class SensorItem {
        final long id;
        final long gatewayId;
        final String name;
        final String type;
        final int batteryLevel;
        final String keyword;
        final String gatewayName;
        final long parentFolderId;

        public SensorItem(Cursor cursor, String gatewayName, long parentFolderId) {
            this.id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            this.gatewayId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_GATEWAY_ID));
            this.name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_NAME));
            this.type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_TYPE));
            this.batteryLevel = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_BATTERY));
            this.keyword = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_KEYWORD));
            this.gatewayName = gatewayName;
            this.parentFolderId = parentFolderId;
        }
    }

    // --- Koniec Modeli Widoków ---


    public FolderAdapter(Context context, List<Object> displayItems, FolderCallback callback) {
        this.context = context;
        this.displayItems = displayItems;
        this.callback = callback;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = displayItems.get(position);
        if (item instanceof SectionHeader) return VIEW_TYPE_SECTION_HEADER;
        if (item instanceof FolderItem) return VIEW_TYPE_FOLDER;
        if (item instanceof GatewayItem) return VIEW_TYPE_GATEWAY;
        if (item instanceof SensorItem) return VIEW_TYPE_SENSOR;
        return -1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        switch (viewType) {
            case VIEW_TYPE_SECTION_HEADER:
                View headerView = inflater.inflate(R.layout.list_section_header, parent, false);
                return new HeaderViewHolder(headerView);
            case VIEW_TYPE_FOLDER:
                View folderView = inflater.inflate(R.layout.list_group_folder, parent, false);
                return new FolderViewHolder(folderView);
            case VIEW_TYPE_GATEWAY:
                View gatewayView = inflater.inflate(R.layout.list_group, parent, false);
                return new GatewayViewHolder(gatewayView);
            case VIEW_TYPE_SENSOR:
                // Używamy tego samego layoutu co GatewaySensorCursorAdapter
                View sensorView = inflater.inflate(R.layout.list_item, parent, false);
                return new SensorViewHolder(sensorView);
            default:
                throw new IllegalArgumentException("Invalid view type");
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = displayItems.get(position);
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_SECTION_HEADER:
                ((HeaderViewHolder) holder).bind((SectionHeader) item);
                break;
            case VIEW_TYPE_FOLDER:
                ((FolderViewHolder) holder).bind((FolderItem) item);
                break;
            case VIEW_TYPE_GATEWAY:
                ((GatewayViewHolder) holder).bind((GatewayItem) item);
                break;
            case VIEW_TYPE_SENSOR:
                ((SensorViewHolder) holder).bind((SensorItem) item);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return displayItems.size();
    }


    // --- ViewHoldery ---

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView textHeader;
        HeaderViewHolder(View view) {
            super(view);
            textHeader = view.findViewById(R.id.textSectionHeader);
        }
        void bind(SectionHeader item) {
            textHeader.setText(item.title);
        }
    }

    // ⭐️ ZMIANA: Logika w `bind` do ukrywania koloru ⭐️
    class FolderViewHolder extends RecyclerView.ViewHolder {
        View colorIndicator;
        ImageView iconFolder, iconExpansion;
        TextView textName;
        FolderViewHolder(View view) {
            super(view);
            colorIndicator = view.findViewById(R.id.folderColorIndicator);
            iconFolder = view.findViewById(R.id.iconFolder);
            iconExpansion = view.findViewById(R.id.iconExpansionIndicator);
            textName = view.findViewById(R.id.textFolderName);
        }
        void bind(FolderItem item) {
            textName.setText(item.name);

            // ⭐️ ZMIANA: Obsługa braku koloru ⭐️
            if (item.color != null) {
                try {
                    colorIndicator.setBackgroundColor(Color.parseColor(item.color));
                    colorIndicator.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    colorIndicator.setBackgroundColor(Color.GRAY);
                    colorIndicator.setVisibility(View.VISIBLE);
                }
            } else {
                // To jest nasz folder "Niezgrupowane", ukryj wskaźnik koloru
                colorIndicator.setVisibility(View.GONE);
            }

            // Ikona folderu (można by też ją zmienić, ale zostawmy)
            iconFolder.setImageResource(R.drawable.ic_folder);

            // Strzałka (działa tak samo)
            iconExpansion.setImageResource(item.isExpanded ?
                    android.R.drawable.arrow_up_float :
                    android.R.drawable.arrow_down_float);

            // Kliknięcie (działa tak samo)
            itemView.setOnClickListener(v -> callback.onFolderClicked(item));
            itemView.setOnLongClickListener(v -> {
                callback.onFolderLongClicked(item, v);
                return true;
            });
        }
    }

    class GatewayViewHolder extends RecyclerView.ViewHolder {
        TextView textGroupName, textGroupStatus;
        ImageView iconGroup;
        ImageView iconExpansion;

        GatewayViewHolder(View view) {
            super(view);
            textGroupName = view.findViewById(R.id.textGroup);
            textGroupStatus = view.findViewById(R.id.textGroupStatus);
            iconGroup = view.findViewById(R.id.iconGroup);
            iconExpansion = view.findViewById(R.id.iconExpansionIndicator);
        }

        void bind(GatewayItem item) {
            textGroupName.setText(item.name);
            iconGroup.setImageResource(R.drawable.ic_gateway);
            if ("online".equalsIgnoreCase(item.status)) {
                textGroupStatus.setText(R.string.gateway_status_online);
                textGroupStatus.setTextColor(Color.parseColor("#FF009900"));
            } else {
                textGroupStatus.setText(R.string.gateway_status_offline);
                textGroupStatus.setTextColor(Color.parseColor("#FF990000"));
            }

            if (item.isSensorParent) {
                iconExpansion.setVisibility(View.VISIBLE);
                iconExpansion.setImageResource(item.isExpanded ?
                        android.R.drawable.arrow_up_float :
                        android.R.drawable.arrow_down_float);
            } else {
                iconExpansion.setVisibility(View.INVISIBLE);
            }

            itemView.setOnClickListener(v -> callback.onGatewayClicked(item));
            itemView.setOnLongClickListener(v -> {
                callback.onGatewayLongClicked(item, v);
                return true;
            });
        }
    }

    // ###############################################################
    // ###                        POCZĄTEK POPRAWKI                  ###
    // ###############################################################
    class SensorViewHolder extends RecyclerView.ViewHolder {

        // 1. Zdefiniuj wszystkie widoki z list_item.xml
        TextView textSensorName;
        TextView textBatteryLevel;
        ImageView iconBattery;

        SensorViewHolder(View view) {
            super(view);

            // 2. Znajdź widoki za pomocą findViewById (TO BYŁ BŁĄD)
            textSensorName = view.findViewById(R.id.sensor_name_text);
            textBatteryLevel = view.findViewById(R.id.battery_text);
            iconBattery = view.findViewById(R.id.battery_icon);
        }

        void bind(SensorItem item) {

            // 3. Ustaw ikonę i nazwę sensora
            textSensorName.setCompoundDrawablesWithIntrinsicBounds(getIcon(item.type, item.keyword, null), 0, 0, 0);

            String displayText = item.name;
            // Dodaj nazwę bramki (dla listy ulubionych/folderów)
            if (item.gatewayName != null) {
                displayText += " (" + item.gatewayName + ")";
            }
            textSensorName.setText(displayText);

            // 4. Ustaw stan baterii (logika skopiowana z GatewaySensorCursorAdapter)
            if (item.batteryLevel > 0) {
                textBatteryLevel.setText(item.batteryLevel + "%");
                textBatteryLevel.setVisibility(View.VISIBLE);
                iconBattery.setVisibility(View.VISIBLE);

                // Ustaw odpowiednią ikonę (musisz dodać te drawable)
                if (item.batteryLevel > 75) {
                    iconBattery.setImageResource(R.drawable.ic_battery_full);
                } else if (item.batteryLevel > 50) {
                    iconBattery.setImageResource(R.drawable.ic_battery_good);
                } else if (item.batteryLevel > 20) { // Zmieniony próg z 25 na 20
                    iconBattery.setImageResource(R.drawable.ic_battery_medium);
                } else if (item.batteryLevel > 1) { // Nowy próg dla "low"
                    iconBattery.setImageResource(R.drawable.ic_battery_low);
                } else {
                    // Poziom 1% lub 0
                    iconBattery.setImageResource(R.drawable.ic_battery_empty); // <-- NOWA IKONA
                }

                // Zmiana koloru tekstu NAZWY
                if (item.batteryLevel <= 20) {
                    textSensorName.setTextColor(Color.parseColor("#FF990000")); // Czerwony
                } else {
                    textSensorName.setTextColor(getDefaultTextColor()); // Domyślny
                }

            } else {
                // Jeśli bateria = 0 lub null, ukryj elementy baterii
                textBatteryLevel.setVisibility(View.GONE);
                iconBattery.setVisibility(View.GONE);
                textSensorName.setTextColor(getDefaultTextColor()); // Domyślny kolor
            }

            // 5. Ustaw listenery
            itemView.setOnClickListener(v -> callback.onSensorClicked(item));
            itemView.setOnLongClickListener(v -> {
                callback.onSensorLongClicked(item, v);
                return true;
            });
        }
    }
    // ###############################################################
    // ###                         KONIEC POPRAWKI                 ###
    // ###############################################################


    // Metoda pomocnicza do pobierania domyślnego koloru tekstu
    private int getDefaultTextColor() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return this.context.getColor(android.R.color.tab_indicator_text);
        } else {
            //noinspection deprecation
            return this.context.getResources().getColor(android.R.color.tab_indicator_text);
        }
    }

    private int getIcon(String type, String keyword, String value) {
        if (keyword != null) {
            switch (keyword.toLowerCase()) {
                case "tv": return R.drawable.ic_tv;
                case "washer": return R.drawable.ic_washer;
                case "fridge": return R.drawable.ic_fridge;
                case "oven": return R.drawable.ic_oven;
                case "socket": return R.drawable.ic_socket;
                case "dishwasher": return R.drawable.ic_dishwasher;
                case "hood": return R.drawable.ic_hood;
            }
        }
        if (type == null) return R.drawable.ic_sensor;
        switch (type.toLowerCase()) {
            case "button_press": return R.drawable.ic_button;
            case "temperature": return R.drawable.ic_temp;
            case "humidity": return R.drawable.ic_humidity;
            case "power": return R.drawable.ic_power;
            case "motion": return R.drawable.ic_motion;
            case "light": return R.drawable.ic_light;
            case "smoke": return R.drawable.ic_smoke;
            case "flow": return R.drawable.ic_flow;
            case "sunlight": return R.drawable.ic_sunlight;
            case "level": return R.drawable.ic_level;
            case "valve": return R.drawable.ic_valve;
            case "contact":
                if ("1".equals(value)) {
                    return R.drawable.ic_open;
                } else {
                    return R.drawable.ic_closed;
                }
            default:
                return R.drawable.ic_sensor;
        }
    }
}