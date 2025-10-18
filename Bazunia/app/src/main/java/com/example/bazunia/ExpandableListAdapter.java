package com.example.bazunia;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;
import android.widget.ImageView;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ExpandableListAdapter extends BaseExpandableListAdapter {
    private final Context context;
    private final List<String> listBramek;
    private final HashMap<String, List<SensorModel>> czujnikiMap;
    private final ThresholdManager thresholdManager; // Dodano manager progów

    public ExpandableListAdapter(Context context, List<String> listBramek, HashMap<String, List<SensorModel>> czujnikiMap) {
        this.context = context;
        this.listBramek = listBramek;
        this.czujnikiMap = czujnikiMap;
        this.thresholdManager = new ThresholdManager(context); // Inicjalizacja
    }

    @Override
    public int getGroupCount() {
        return listBramek.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        List<SensorModel> children = czujnikiMap.get(listBramek.get(groupPosition));
        return children != null ? children.size() : 0;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return listBramek.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return czujnikiMap.get(listBramek.get(groupPosition)).get(childPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        String bramka = (String) getGroup(groupPosition);
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.list_group, null);
        }

        // Ustaw ikonę i tekst dla grupy (bramki)
        ImageView iconView = convertView.findViewById(R.id.iconGroup);
        iconView.setImageResource(R.drawable.ic_gateway);

        TextView textView = convertView.findViewById(R.id.textGroup);
        textView.setText("Bramka: " + bramka);
        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        SensorModel sensor = (SensorModel) getChild(groupPosition, childPosition);
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.list_item, null);
        }

        // Ustaw dynamiczną ikonę na podstawie typu i wartości czujnika
        ImageView iconView = convertView.findViewById(R.id.iconChild);
        iconView.setImageResource(sensor.getIconResourceId());

        TextView textView = convertView.findViewById(R.id.textChild);
        String formattedText = String.format(Locale.getDefault(), "Czujnik %s (%s) - Ost. %s", sensor.sensorId, sensor.type, sensor.value);
        textView.setText(formattedText);

        // ZMIANA: Sprawdź progi i pokoloruj tekst na liście, jeśli jest alert
        textView.setTextColor(isSensorValueInAlertState(sensor) ? Color.RED : Color.BLACK);

        return convertView;
    }

    /**
     * Helper, który sprawdza, czy ostatnia wartość czujnika jest poza ustawionymi progami.
     */
    private boolean isSensorValueInAlertState(SensorModel sensor) {
        if ("door_contact".equalsIgnoreCase(sensor.type)) {
            return "1".equals(sensor.value);
        }

        boolean isHumidity = "humidity".equalsIgnoreCase(sensor.type);
        float defaultMin = isHumidity ? 5.0f : 18.0f;
        float defaultMax = isHumidity ? 30.0f : 22.0f;

        float min = thresholdManager.getMinThreshold(sensor.gatewayId, sensor.sensorId, defaultMin);
        float max = thresholdManager.getMaxThreshold(sensor.gatewayId, sensor.sensorId, defaultMax);

        try {
            float currentValue = Float.parseFloat(sensor.value);
            return currentValue < min || currentValue > max;
        } catch (NumberFormatException e) {
            return false; // Jeśli wartość nie jest liczbą, nie jest w stanie alertu
        }
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}