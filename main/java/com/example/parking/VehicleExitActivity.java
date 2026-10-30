
package com.example.parking;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VehicleExitActivity extends Activity {

    private DatabaseHelper db;

    private EditText edtVehicleNumber;
    private TextView txtVehicleDetails;
    private Button btnSearch;
    private Button btnRelease;

    private DatabaseHelper.ParkingRecord activeRecord;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle_exit);

        db = new DatabaseHelper(this);

        // Connect Java variables to the XML views.
        edtVehicleNumber = findViewById(R.id.edtExitVehicleNumber);
        btnSearch = findViewById(R.id.btnSearchVehicle);
        txtVehicleDetails = findViewById(R.id.txtVehicleDetails);
        btnRelease = findViewById(R.id.btnConfirmExit);

        btnSearch.setOnClickListener(v -> searchVehicle());
        btnRelease.setOnClickListener(v -> confirmExit());
    }

    private String getNormalizedInput() {
        return edtVehicleNumber.getText()
                .toString()
                .trim()
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
    }

    private void searchVehicle() {
        String number = getNormalizedInput();

        activeRecord = null;
        btnRelease.setEnabled(false);

        if (number.isEmpty()) {
            edtVehicleNumber.setError("Enter vehicle number");
            edtVehicleNumber.requestFocus();
            txtVehicleDetails.setText("Enter a vehicle number to search.");
            return;
        }

        edtVehicleNumber.setError(null);
        activeRecord = db.findActiveVehicle(number);

        if (activeRecord == null) {
            txtVehicleDetails.setText(
                    "No active parking session found for " + number
            );

            Toast.makeText(
                    this,
                    "Vehicle is not currently parked",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        long duration = db.getParkingDurationMillis(activeRecord);
        double fee = db.calculateParkingFee(activeRecord);

        txtVehicleDetails.setText(
                "Vehicle: " + activeRecord.vehicleNumber +
                        "\nType: " + activeRecord.vehicleType +
                        "\nSlot: " + activeRecord.slotNumber +
                        "\nEntry time: " +
                        dateFormat.format(new Date(activeRecord.entryTime)) +
                        "\nCurrent duration: " + formatDuration(duration) +
                        String.format(
                                Locale.getDefault(),
                                "\nEstimated parking fee: ₹%.2f",
                                fee
                        ) +
                        "\n\nVerify the vehicle details before confirming exit."
        );

        btnRelease.setEnabled(true);
    }

    private void confirmExit() {
        if (activeRecord == null) {
            Toast.makeText(
                    this,
                    "Search for an active vehicle first",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        final String number = activeRecord.vehicleNumber;
        final long recordId = activeRecord.id;

        new AlertDialog.Builder(this)
                .setTitle("Confirm Vehicle Exit")
                .setMessage(
                        "Vehicle: " + number +
                                "\nSlot: " + activeRecord.slotNumber +
                                "\n\nComplete this parking session and free the slot?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm Exit", (dialog, which) -> {

                    // Check that the same parking session is still active.
                    DatabaseHelper.ParkingRecord latest =
                            db.findActiveVehicle(number);

                    if (latest == null || latest.id != recordId) {
                        activeRecord = null;
                        btnRelease.setEnabled(false);
                        txtVehicleDetails.setText(
                                "This parking session is no longer active. " +
                                        "Please search again."
                        );
                        return;
                    }

                    boolean released = db.releaseVehicle(number);

                    if (released) {
                        // Calculate the final fee using the stored entry time
                        // and the exit time recorded by the database.
                        latest.exitTime = System.currentTimeMillis();
                        latest.status = "Completed";

                        double finalFee = db.calculateParkingFee(latest);

                        txtVehicleDetails.setText(
                                "Vehicle exit completed successfully.\n" +
                                        "Vehicle: " + number +
                                        "\nSlot released: " + latest.slotNumber +
                                        String.format(
                                                Locale.getDefault(),
                                                "\nFinal parking fee: ₹%.2f",
                                                finalFee
                                        ) +
                                        "\n\nParking history has been retained."
                        );

                        Toast.makeText(
                                this,
                                String.format(
                                        Locale.getDefault(),
                                        "Vehicle exited successfully. Fee: ₹%.2f",
                                        finalFee
                                ),
                                Toast.LENGTH_LONG
                        ).show();

                        activeRecord = null;
                        btnRelease.setEnabled(false);
                        edtVehicleNumber.setText("");
                        setResult(RESULT_OK);

                    } else {
                        activeRecord = null;
                        btnRelease.setEnabled(false);

                        txtVehicleDetails.setText(
                                "Exit could not be completed. " +
                                        "Please search for the vehicle again."
                        );

                        Toast.makeText(
                                this,
                                "Exit failed. Check the active session and slot.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .show();
    }

    private String formatDuration(long millis) {
        long totalMinutes = Math.max(0, millis) / (60L * 1000L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;

        return hours + " hour(s), " + minutes + " minute(s)";
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Do not clear a record during the initial launch.
        // The search result is cleared after a successful exit instead.
    }

    @Override
    protected void onDestroy() {
        if (db != null) {
            db.close();
        }

        super.onDestroy();
    }
}
