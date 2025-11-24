package com.testserwera.bazunia.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.testserwera.bazunia.R;
import com.testserwera.bazunia.data.DatabaseHelper;

import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SECTION_HEADER = 0;
    private static final int VIEW_TYPE_FOLDER = 1;
    private static final int VIEW_TYPE_GATEWAY = 2;
    private static final int VIEW_TYPE_SENSOR = 3;

    private final Context context;
    private final List<Object> displayItems;
    private final FolderCallback callback;
    private final SharedPreferences mutePrefs;

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

    public static class FolderItem {
        final long id;
        final String name;
        final String color;
        boolean isExpanded;

        public FolderItem(Cursor cursor, boolean isExpanded) {
            this.id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            this.name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.F_COLUMN_NAME));
            this.color = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.F_COLUMN_COLOR));
            this.isExpanded = isExpanded;
        }

        public FolderItem(long id, String name, boolean isExpanded) {
            this.id = id;
            this.name = name;
            this.color = null;
            this.isExpanded = isExpanded;
        }
    }

    // --- Poprawiona klasa GatewayItem ---
    public static class GatewayItem {
        final long id;
        final String name;
        final String status;
        final String description;
        final boolean isSensorParent;
        boolean isExpanded;
        final long parentFolderId;
        final String parentColor; // Pole koloru

        // KONSTRUKTOR GŁÓWNY (5 argumentów - z kolorem)
        public GatewayItem(Cursor cursor, long parentFolderId, boolean isSensorParent, boolean isExpanded, String parentColor) {
            this.id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_ID));
            this.name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_NAME));
            this.status = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_STATUS));
            this.description = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_DESCRIPTION));
            this.parentFolderId = parentFolderId;
            this.isSensorParent = isSensorParent;
            this.isExpanded = isExpanded;
            this.parentColor = parentColor; // Przypisanie koloru
        }

        // KONSTRUKTOR POMOCNICZY (4 argumenty - bez koloru, dla Ulubionych/Niezgrupowanych)
        public GatewayItem(Cursor cursor, long parentFolderId, boolean isSensorParent, boolean isExpanded) {
            // Wywołuje główny konstruktor z null jako kolorem
            this(cursor, parentFolderId, isSensorParent, isExpanded, null);
        }
    }

    // --- Poprawiona klasa SensorItem ---
    public static class SensorItem {
        final long id;
        final long gatewayId;
        final String name;
        final String type;
        final String value;
        final int batteryLevel;
        final String keyword;
        final String gatewayName;
        final long parentFolderId;
        final String parentColor; // Pole koloru

        // KONSTRUKTOR GŁÓWNY (z kolorem)
        public SensorItem(Cursor cursor, String gatewayName, long parentFolderId, String parentColor) {
            this.id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            this.gatewayId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_GATEWAY_ID));
            this.name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_NAME));
            this.type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_TYPE));
            this.value = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_VALUE));
            this.batteryLevel = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_BATTERY));
            this.keyword = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_KEYWORD));
            this.gatewayName = gatewayName;
            this.parentFolderId = parentFolderId;
            this.parentColor = parentColor; // Przypisanie koloru
        }

        // KONSTRUKTOR POMOCNICZY (bez koloru)
        public SensorItem(Cursor cursor, String gatewayName, long parentFolderId) {
            // Wywołuje główny konstruktor z null jako kolorem
            this(cursor, gatewayName, parentFolderId, null);
        }
    }

    // --- Konstruktor ---

    public FolderAdapter(Context context, List<Object> displayItems, FolderCallback callback) {
        this.context = context;
        this.displayItems = displayItems;
        this.callback = callback;
        this.mutePrefs = context.getSharedPreferences("NotificationMutePrefs", Context.MODE_PRIVATE);
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
                View sensorView = inflater.inflate(R.layout.list_item, parent, false);
                return new SensorViewHolder(sensorView, context, mutePrefs, callback);
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
                ((FolderViewHolder) holder).bind((FolderItem) item, callback);
                break;
            case VIEW_TYPE_GATEWAY:
                ((GatewayViewHolder) holder).bind((GatewayItem) item, callback);
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

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView textHeader;
        HeaderViewHolder(View view) {
            super(view);
            textHeader = view.findViewById(R.id.textSectionHeader);
        }
        void bind(SectionHeader item) {
            textHeader.setText(item.title);
        }
    }

    static class FolderViewHolder extends RecyclerView.ViewHolder {

        ImageView iconFolder, iconExpansion;
        TextView textName;
        View rootView;

        FolderViewHolder(View view) {
            super(view);
            rootView = view;
            iconFolder = view.findViewById(R.id.iconFolder);
            iconExpansion = view.findViewById(R.id.iconExpansionIndicator);
            textName = view.findViewById(R.id.textFolderName);
        }

        void bind(FolderItem item, FolderCallback callback) {
            textName.setText(item.name);

            // Ukryj stary wskaźnik jeśli istnieje w XML
            View oldIndicator = itemView.findViewById(R.id.folderColorIndicator);
            if(oldIndicator != null) oldIndicator.setVisibility(View.GONE);

            // ZMIANA: Zawsze czyścimy filtr, żeby ikona folderu miała oryginalny kolor
            iconFolder.clearColorFilter();

            // Logika kolorowania tła
            if (item.color != null && !item.color.isEmpty()) {
                try {
                    int color = Color.parseColor(item.color);
                    rootView.setBackgroundColor(color);

                    // ZMIANA: Zmieniamy kolor TYLKO tekstu i strzałki rozwijania
                    if (isColorDark(color)) {
                        textName.setTextColor(Color.WHITE);
                        iconExpansion.setColorFilter(Color.WHITE);
                    } else {
                        textName.setTextColor(Color.BLACK);
                        iconExpansion.setColorFilter(Color.BLACK);
                    }
                } catch (Exception e) {
                    rootView.setBackgroundColor(Color.TRANSPARENT);
                    restoreDefaultColors();
                }
            } else {
                rootView.setBackgroundColor(Color.TRANSPARENT);
                restoreDefaultColors();
            }

            iconFolder.setImageResource(R.drawable.ic_folder);
            iconExpansion.setImageResource(R.drawable.ic_expand_arrow);
            iconExpansion.setRotation(item.isExpanded ? 180f : 0f);

            itemView.setOnClickListener(v -> callback.onFolderClicked(item));
            itemView.setOnLongClickListener(v -> {
                callback.onFolderLongClicked(item, v);
                return true;
            });
        }

        private void restoreDefaultColors() {
            int defaultColor = ContextCompat.getColor(itemView.getContext(), android.R.color.tab_indicator_text);
            textName.setTextColor(defaultColor);
            iconExpansion.clearColorFilter();
            // iconFolder jest czyszczony na początku bind()
        }

        private boolean isColorDark(int color) {
            double darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
            return darkness >= 0.5;
        }
    }

    static class GatewayViewHolder extends RecyclerView.ViewHolder {
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

        void bind(GatewayItem item, FolderCallback callback) {
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
                iconExpansion.setImageResource(R.drawable.ic_expand_arrow);
                iconExpansion.setRotation(item.isExpanded ? 180f : 0f);

            } else {
                iconExpansion.setVisibility(View.INVISIBLE);
            }

            // Obsługa tła z koloru rodzica
            if (item.parentColor != null) {
                try {
                    int color = Color.parseColor(item.parentColor);
                    // Ustawiamy kolor z dużą przezroczystością (ok 15%), żeby tekst był czytelny
                    int alphaColor = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color));
                    itemView.setBackgroundColor(alphaColor);
                } catch (Exception e) {
                    itemView.setBackgroundColor(Color.TRANSPARENT);
                }
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            itemView.setOnClickListener(v -> callback.onGatewayClicked(item));
            itemView.setOnLongClickListener(v -> {
                callback.onGatewayLongClicked(item, v);
                return true;
            });
        }
    }

    static class SensorViewHolder extends RecyclerView.ViewHolder {

        TextView textSensorName;
        TextView textBatteryLevel;
        ImageView iconBattery;
        ImageView iconMuteThresh;
        ImageView iconMuteBatt;

        Context context;
        SharedPreferences mutePrefs;
        FolderCallback callback;

        SensorViewHolder(View view, Context context, SharedPreferences mutePrefs, FolderCallback callback) {
            super(view);
            this.context = context;
            this.mutePrefs = mutePrefs;
            this.callback = callback;

            textSensorName = view.findViewById(R.id.sensor_name_text);
            textBatteryLevel = view.findViewById(R.id.battery_text);
            iconBattery = view.findViewById(R.id.battery_icon);
            iconMuteThresh = view.findViewById(R.id.icon_mute_threshold);
            iconMuteBatt = view.findViewById(R.id.icon_mute_battery);
        }

        void bind(SensorItem item) {
            // Przekazujemy teraz item.value, aby ikona otwarcia/zamknięcia działała
            textSensorName.setCompoundDrawablesWithIntrinsicBounds(getIcon(item.type, item.keyword, item.value), 0, 0, 0);

            if (item.gatewayName != null) {
                textSensorName.setText(context.getString(R.string.sensor_name_with_gateway_format, item.name, item.gatewayName));
            } else {
                textSensorName.setText(item.name);
            }

            // Ikony wyciszenia
            boolean isThreshMuted = mutePrefs.getBoolean("thresh_sensor_" + item.id, false);
            boolean isBattMuted = mutePrefs.getBoolean("batt_sensor_" + item.id, false);

            if (iconMuteThresh != null) {
                iconMuteThresh.setVisibility(isThreshMuted ? View.VISIBLE : View.GONE);
            }
            if (iconMuteBatt != null) {
                iconMuteBatt.setVisibility(isBattMuted ? View.VISIBLE : View.GONE);
            }

            // Obsługa baterii
            if (item.batteryLevel > 0) {
                textBatteryLevel.setText(context.getString(R.string.battery_percentage_format, item.batteryLevel));
                textBatteryLevel.setVisibility(View.VISIBLE);
                iconBattery.setVisibility(View.VISIBLE);

                if (item.batteryLevel > 75) {
                    iconBattery.setImageResource(R.drawable.ic_battery_full);
                } else if (item.batteryLevel > 50) {
                    iconBattery.setImageResource(R.drawable.ic_battery_good);
                } else if (item.batteryLevel > 20) {
                    iconBattery.setImageResource(R.drawable.ic_battery_medium);
                } else if (item.batteryLevel > 1) {
                    iconBattery.setImageResource(R.drawable.ic_battery_low);
                } else {
                    iconBattery.setImageResource(R.drawable.ic_battery_empty);
                }

                if (item.batteryLevel <= 20) {
                    textSensorName.setTextColor(Color.parseColor("#FF990000"));
                } else {
                    textSensorName.setTextColor(getDefaultTextColor(context));
                }

            } else {
                textBatteryLevel.setVisibility(View.GONE);
                iconBattery.setVisibility(View.GONE);
                textSensorName.setTextColor(getDefaultTextColor(context));
            }

            // Obsługa tła z koloru rodzica
            if (item.parentColor != null) {
                try {
                    int color = Color.parseColor(item.parentColor);
                    int alphaColor = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color));
                    itemView.setBackgroundColor(alphaColor);
                } catch (Exception e) {
                    itemView.setBackgroundColor(Color.TRANSPARENT);
                }
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            itemView.setOnClickListener(v -> callback.onSensorClicked(item));
            itemView.setOnLongClickListener(v -> {
                callback.onSensorLongClicked(item, v);
                return true;
            });
        }

        private int getDefaultTextColor(Context ctx) {
            return ContextCompat.getColor(ctx, android.R.color.tab_indicator_text);
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
}