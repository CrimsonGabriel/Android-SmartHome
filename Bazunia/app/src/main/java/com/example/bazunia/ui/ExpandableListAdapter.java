package com.example.bazunia.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.bazunia.R;
import com.example.bazunia.data.SensorModel;
import com.example.bazunia.data.ThresholdManager;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class ExpandableListAdapter extends BaseExpandableListAdapter {
    private final Context context;
    private final List<String> listBramek;
    private final HashMap<String, List<SensorModel>> czujnikiMap;
    private final ThresholdManager thresholdManager;

    // ViewHolder dla grupy
    private static class GroupViewHolder {
        ImageView iconView;
        TextView textView;
    }

    // ViewHolder dla dziecka (czujnika)
    private static class ChildViewHolder {
        TextView textView;
    }

    public ExpandableListAdapter(Context context, List<String> listBramek, HashMap<String, List<SensorModel>> czujnikiMap) {
        this.context = context;
        this.listBramek = listBramek;
        this.czujnikiMap = czujnikiMap;
        this.thresholdManager = new ThresholdManager(context);
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
        List<SensorModel> sensors = czujnikiMap.get(listBramek.get(groupPosition));
        return (sensors != null) ? sensors.get(childPosition) : null;
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
        GroupViewHolder holder;

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.list_group, parent, false);
            holder = new GroupViewHolder();
            holder.iconView = convertView.findViewById(R.id.iconGroup);
            holder.textView = convertView.findViewById(R.id.textGroup);
            convertView.setTag(holder);
        } else {
            holder = (GroupViewHolder) convertView.getTag();
        }

        holder.iconView.setImageResource(R.drawable.ic_gateway);
        holder.textView.setText(context.getString(R.string.gateway_label, bramka));
        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        SensorModel sensor = (SensorModel) getChild(groupPosition, childPosition);
        ChildViewHolder holder;

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.list_item, parent, false);
            holder = new ChildViewHolder();
            holder.textView = (TextView) convertView; // Nasz layout list_item to tylko TextView
            convertView.setTag(holder);
        } else {
            holder = (ChildViewHolder) convertView.getTag();
        }

        if (sensor != null) {
            holder.textView.setCompoundDrawablesWithIntrinsicBounds(sensor.getIconResourceId(), 0, 0, 0);
            String formattedText = String.format(Locale.getDefault(), context.getString(R.string.sensor_list_item_format), sensor.sensorId, sensor.type, sensor.value);
            holder.textView.setText(formattedText);

            // POPRAWKA: Użycie koloru z motywu zamiast stałego Color.BLACK
            int defaultColor;
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
            defaultColor = typedValue.data;

            holder.textView.setTextColor(isSensorValueInAlertState(sensor) ? Color.RED : defaultColor);
        }

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    private boolean isSensorValueInAlertState(SensorModel sensor) {
        if (sensor == null) return false;

        if (context.getString(R.string.sensor_type_door_contact).equalsIgnoreCase(sensor.type)) {
            return context.getString(R.string.door_contact_open_value).equals(sensor.value);
        }

        boolean isHumidity = context.getString(R.string.sensor_type_humidity).equalsIgnoreCase(sensor.type);
        float defaultMin = isHumidity ? 5.0f : 18.0f;
        float defaultMax = isHumidity ? 30.0f : 22.0f;

        float min = thresholdManager.getMinThreshold(sensor.gatewayId, sensor.sensorId, defaultMin);
        float max = thresholdManager.getMaxThreshold(sensor.gatewayId, sensor.sensorId, defaultMax);

        try {
            float currentValue = Float.parseFloat(sensor.value);
            return currentValue < min || currentValue > max;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
