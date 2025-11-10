package com.example.bazunia.ui;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
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

    // Typy widoków
    private static final int VIEW_TYPE_SECTION_HEADER = 0;
    private static final int VIEW_TYPE_FOLDER = 1;
    private static final int VIEW_TYPE_GATEWAY = 2;
    private static final int VIEW_TYPE_SENSOR = 3;

    private final Context context;
    private final List<Object> displayItems; // Lista przechowująca nagłówki, foldery, bramki, czujniki
    private final FolderCallback callback;

    /**
     * Interfejs do komunikacji z DataActivity
     */
    public interface FolderCallback {
        void onFolderClicked(FolderItem folder);
        void onGatewayClicked(GatewayItem gateway);
        void onSensorClicked(SensorItem sensor);

        void onFolderLongClicked(FolderItem folder, View view);
        void onGatewayLongClicked(GatewayItem gateway, View view);
        void onSensorLongClicked(SensorItem sensor, View view);
    }

    // --- Modele Widoków (wewnętrzne klasy) ---
    // Przechowują dane potrzebne do wyświetlenia wiersza

    public static class SectionHeader {
        final String title;
        public SectionHeader(String title) { this.title = title; }
    }

    public static class FolderItem {
        final long id;
        final String name;
        final String color;
        boolean isExpanded; // DataActivity będzie zarządzać tym stanem
        public FolderItem(Cursor cursor, boolean isExpanded) {
            this.id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            this.name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.F_COLUMN_NAME));
            this.color = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.F_COLUMN_COLOR));
            this.isExpanded = isExpanded;
        }
    }

    public static class GatewayItem {
        final long id;
        final String name;
        final String status;
        final String description;
        final boolean isSensorParent; // Czy ten wiersz bramki może się rozwijać (false dla ulubionych)
        boolean isExpanded;
        final long parentFolderId; // ⭐️ NOWE POLE (np. ID folderu lub -1 dla "bez kategorii")

        public GatewayItem(Cursor cursor, long parentFolderId, boolean isSensorParent, boolean isExpanded) {
            this.id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_ID));
            this.name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_NAME));
            this.status = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_STATUS));
            this.description = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_DESCRIPTION));
            this.parentFolderId = parentFolderId; // ⭐️ PRZYPISANIE
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
        final String gatewayName; // Potrzebne dla listy ulubionych
        public SensorItem(Cursor cursor, String gatewayName) {
            this.id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            this.gatewayId = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_GATEWAY_ID));
            this.name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_NAME));
            this.type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_TYPE));
            this.batteryLevel = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_BATTERY));
            this.keyword = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_KEYWORD));
            this.gatewayName = gatewayName;
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
            try {
                colorIndicator.setBackgroundColor(Color.parseColor(item.color));
            } catch (Exception e) {
                colorIndicator.setBackgroundColor(Color.GRAY);
            }

            // (Jeśli masz własne ikony, użyj R.drawable.ic_arrow_up / R.drawable.ic_arrow_down)
            iconExpansion.setImageResource(item.isExpanded ?
                    android.R.drawable.arrow_up_float :
                    android.R.drawable.arrow_down_float);

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
        ImageView iconExpansion; // <-- NOWE POLE

        GatewayViewHolder(View view) {
            super(view);
            textGroupName = view.findViewById(R.id.textGroup);
            textGroupStatus = view.findViewById(R.id.textGroupStatus);
            iconGroup = view.findViewById(R.id.iconGroup);
            iconExpansion = view.findViewById(R.id.iconExpansionIndicator); // <-- NOWE FINDVIEWBYID
        }

        void bind(GatewayItem item) {
            // Logika skopiowana z GatewaySensorCursorAdapter
            textGroupName.setText(item.name);
            iconGroup.setImageResource(R.drawable.ic_gateway);
            if ("online".equalsIgnoreCase(item.status)) {
                textGroupStatus.setText(R.string.gateway_status_online);
                textGroupStatus.setTextColor(Color.parseColor("#FF009900")); // Zielony
            } else {
                textGroupStatus.setText(R.string.gateway_status_offline);
                textGroupStatus.setTextColor(Color.parseColor("#FF990000")); // Czerwony
            }

            // ⭐️ NOWA LOGIKA DLA STRZAŁKI ⭐️
            // Sprawdź, czy ta bramka W OGÓLE może się rozwijać (czy jest rodzicem)
            if (item.isSensorParent) {
                iconExpansion.setVisibility(View.VISIBLE); // Pokaż strzałkę
                // Ustaw odpowiednią ikonę (góra/dół)
                iconExpansion.setImageResource(item.isExpanded ?
                        android.R.drawable.arrow_up_float :
                        android.R.drawable.arrow_down_float);
            } else {
                // To jest bramka w ulubionych, nie ma strzałki
                iconExpansion.setVisibility(View.INVISIBLE);
            }

            itemView.setOnClickListener(v -> callback.onGatewayClicked(item));
            itemView.setOnLongClickListener(v -> {
                callback.onGatewayLongClicked(item, v);
                return true;
            });
        }
    }

    class SensorViewHolder extends RecyclerView.ViewHolder {
        TextView textView; // Zakładamy, że list_item to tylko TextView
        SensorViewHolder(View view) {
            super(view);
            textView = (TextView) view;
        }
        void bind(SensorItem item) {
            // Logika skopiowana z GatewaySensorCursorAdapter
            // TODO: Potrzebujemy metody getIcon() (skopiuj z GatewaySensorCursorAdapter)
            // textView.setCompoundDrawablesWithIntrinsicBounds(getIcon(item.type, item.keyword, null), 0, 0, 0);

            String displayText = item.name;
            if (item.gatewayName != null) {
                // Jeśli to ulubiony czujnik, pokaż bramkę
                displayText += " (" + item.gatewayName + ")";
            }
            textView.setText(displayText);

            // TODO: Logika kolorowania baterii (skopiuj z GatewaySensorCursorAdapter)

            itemView.setOnClickListener(v -> callback.onSensorClicked(item));
            itemView.setOnLongClickListener(v -> {
                callback.onSensorLongClicked(item, v);
                return true;
            });
        }
    }
}