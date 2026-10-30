
package com.example.parking;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class HistoryActivity extends Activity {

    private DatabaseHelper db;
    private LinearLayout historyContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        db = new DatabaseHelper(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText("Parking History");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, 0, 0, 16);
        root.addView(title);

        ScrollView scrollView = new ScrollView(this);

        historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);

        scrollView.addView(historyContainer);

        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
        loadHistory();
    }

    private void loadHistory() {
        historyContainer.removeAllViews();

        ArrayList<DatabaseHelper.ParkingRecord> records =
                db.getHistory();

        if (records.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No parking records found.");
            empty.setTextSize(16);
            empty.setPadding(8, 16, 8, 16);
            historyContainer.addView(empty);
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
        );

        for (DatabaseHelper.ParkingRecord record : records) {
            TextView item = new TextView(this);

            String entry = dateFormat.format(
                    new Date(record.entryTime)
            );

            String exit = record.exitTime == null
                    ? "Not exited"
                    : dateFormat.format(
                    new Date(record.exitTime)
            );

            String duration = formatDuration(
                    db.getParkingDurationMillis(record)
            );

            double fee = db.calculateParkingFee(record);

            item.setText(
                    "Vehicle: " + record.vehicleNumber +
                            "\nType: " + record.vehicleType +
                            "\nSlot: " + record.slotNumber +
                            "\nEntry: " + entry +
                            "\nExit: " + exit +
                            "\nDuration: " + duration +
                            String.format(
                                    Locale.getDefault(),
                                    "\nFee: ₹%.2f",
                                    fee
                            ) +
                            "\nStatus: " + record.status
            );

            item.setTextSize(15);
            item.setPadding(14, 14, 14, 14);
            item.setBackgroundColor(0xFFF1F5F9);

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );

            params.setMargins(0, 0, 0, 14);
            historyContainer.addView(item, params);
        }
    }

    private String formatDuration(long millis) {
        long totalMinutes = millis / (60L * 1000L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;

        return hours + " hour(s), " + minutes + " minute(s)";
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (historyContainer != null && db != null) {
            loadHistory();
        }
    }

    @Override
    protected void onDestroy() {
        if (db != null) {
            db.close();
        }

        super.onDestroy();
    }
}
