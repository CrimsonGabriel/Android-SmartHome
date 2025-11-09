package com.example.bazunia.ui;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorTreeAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.bazunia.R;
import com.example.bazunia.data.DatabaseHelper;



public class GatewaySensorCursorAdapter extends CursorTreeAdapter {

    private final LayoutInflater inflater;
    private final DatabaseHelper dbHelper;
    private final Context context; // <<< To pole JEST używane poniżej

    public GatewaySensorCursorAdapter(Cursor cursor, Context context) {
        super(cursor, context);
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.dbHelper = new DatabaseHelper(context);
    }

    @Override
    protected Cursor getChildrenCursor(Cursor groupCursor) {

        long gatewayId = groupCursor.getLong(groupCursor.getColumnIndexOrThrow("_id"));
        return dbHelper.getSensorsForGateway(gatewayId);
    }

    @Override
    protected View newGroupView(Context context, Cursor cursor, boolean isExpanded, ViewGroup parent) {
        return inflater.inflate(R.layout.list_group, parent, false);
    }

    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
        // ... (bez zmian)
        TextView textGroupName = view.findViewById(R.id.textGroup);
        TextView textGroupStatus = view.findViewById(R.id.textGroupStatus);
        ImageView iconGroup = view.findViewById(R.id.iconGroup);
        String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_NAME));
        String status = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.G_COLUMN_STATUS));
        textGroupName.setText(name);
        iconGroup.setImageResource(R.drawable.ic_gateway);
        if ("online".equalsIgnoreCase(status)) {
            textGroupStatus.setText(R.string.gateway_status_online);
            textGroupStatus.setTextColor(Color.parseColor("#FF009900")); // Zielony
        } else {
            textGroupStatus.setText(R.string.gateway_status_offline);
            textGroupStatus.setTextColor(Color.parseColor("#FF990000")); // Czerwony
        }
    }

    @Override
    protected View newChildView(Context context, Cursor cursor, boolean isLastChild, ViewGroup parent) {
        return inflater.inflate(R.layout.list_item, parent, false);
    }

    @Override
    protected void bindChildView(View view, Context context, Cursor cursor, boolean isLastChild) {
        TextView textView = (TextView) view;
        String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_NAME));
        String type = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_TYPE));
        int batteryLevel = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.S_COLUMN_BATTERY));
        textView.setCompoundDrawablesWithIntrinsicBounds(getIconForType(type), 0, 0, 0);
        textView.setText(name);

        // <<< POPRAWKA: Używamy pola this.context zamiast parametru >>>
        int defaultTextColor;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            defaultTextColor = this.context.getColor(android.R.color.tab_indicator_text);
        } else {
            // Starsze wersje
            defaultTextColor = this.context.getResources().getColor(android.R.color.tab_indicator_text);
        }

        if (batteryLevel > 20) {
            textView.setTextColor(defaultTextColor); // Zamiast hardcoded koloru
        } else if (batteryLevel > 0) {
            textView.setTextColor(Color.parseColor("#FF990000")); // Czerwony dla niskiej baterii
        } else {
            textView.setTextColor(defaultTextColor); // Zamiast hardcoded koloru
        }
    }

    private int getIconForType(String type) {
        // ... (bez zmian)
        if (type == null) return R.drawable.ic_sensor;
        switch (type.toLowerCase()) {
            case "temperature":
                return R.drawable.ic_temp;
            case "humidity":
                return R.drawable.ic_humidity;
            case "contact":
                return R.drawable.ic_door_closed;
            case "motion":
            case "smoke":
                return R.drawable.ic_warning;
            default:
                return R.drawable.ic_sensor;
        }
    }
}