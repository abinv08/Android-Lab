package com.example.parking;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    DatabaseHelper db;
    TextView txtCounts;
    LinearLayout slotContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new DatabaseHelper(this);
        txtCounts = findViewById(R.id.txtCounts);
        slotContainer = findViewById(R.id.slotContainer);

        findViewById(R.id.btnEntry).setOnClickListener(v ->
                startActivity(new Intent(this, VehicleEntryActivity.class)));

        findViewById(R.id.btnExit).setOnClickListener(v ->
                startActivity(new Intent(this, VehicleExitActivity.class)));

        findViewById(R.id.btnHistory).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDashboard();
    }

    private void refreshDashboard() {
        int total = db.getTotalSlots();
        int available = db.getSlotCount("Available");
        int occupied = db.getSlotCount("Occupied");

        txtCounts.setText("Total slots: " + total +
                "\nAvailable: " + available +
                "\nOccupied: " + occupied);

        slotContainer.removeAllViews();

        for (DatabaseHelper.ParkingSlot slot : db.getSlots()) {
            TextView item = new TextView(this);
            item.setText(slot.number + "  |  " +
                    slot.vehicleType + "  |  " + slot.status);
            item.setTextSize(16);
            item.setPadding(16, 18, 16, 18);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setTextColor(Color.BLACK);
            item.setBackgroundColor(
                    slot.status.equals("Available")
                            ? Color.rgb(220, 245, 220)
                            : Color.rgb(255, 220, 220));

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 4, 0, 4);
            slotContainer.addView(item, params);
        }
    }
}