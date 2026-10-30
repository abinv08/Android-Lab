
package com.example.parking;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class VehicleEntryActivity extends Activity {

    private DatabaseHelper db;
    private EditText edtVehicleNumber;
    private Spinner spVehicleType;
    private Spinner spParkingSlot;

    private final ArrayList<DatabaseHelper.ParkingSlot> availableSlots =
            new ArrayList<>();

    private boolean loadingTypes = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle_entry);

        db = new DatabaseHelper(this);

        edtVehicleNumber = findViewById(R.id.edtVehicleNumber);
        spVehicleType = findViewById(R.id.spVehicleType);
        spParkingSlot = findViewById(R.id.spParkingSlot);

        String[] types = {
                DatabaseHelper.TWO_WHEELER,
                DatabaseHelper.FOUR_WHEELER
        };

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                types
        );

        typeAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spVehicleType.setAdapter(typeAdapter);

        spVehicleType.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {

                        loadAvailableSlots();
                        loadingTypes = false;
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        availableSlots.clear();
                    }
                }
        );

        findViewById(R.id.btnAllocate).setOnClickListener(
                v -> allocate()
        );

        loadAvailableSlots();
    }

    private void loadAvailableSlots() {
        if (spVehicleType == null || spParkingSlot == null) {
            return;
        }

        Object selectedType = spVehicleType.getSelectedItem();

        String type = selectedType == null
                ? DatabaseHelper.TWO_WHEELER
                : selectedType.toString();

        availableSlots.clear();

        ArrayList<String> labels = new ArrayList<>();

        for (DatabaseHelper.ParkingSlot slot : db.getSlots()) {
            if ("Available".equals(slot.status)
                    && type.equals(slot.vehicleType)) {

                availableSlots.add(slot);
                labels.add(slot.number);
            }
        }

        if (labels.isEmpty()) {
            labels.add("No available slots");
        }

        ArrayAdapter<String> slotAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                labels
        );

        slotAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        spParkingSlot.setAdapter(slotAdapter);
        spParkingSlot.setEnabled(!availableSlots.isEmpty());
    }

    private void allocate() {
        String rawNumber = edtVehicleNumber.getText().toString().trim();

        if (rawNumber.isEmpty()) {
            edtVehicleNumber.setError("Enter vehicle number");
            edtVehicleNumber.requestFocus();
            return;
        }

        // Normalize spaces and case before submitting to SQLite.
        String number = rawNumber
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);

        // Basic format validation; detailed validation also occurs
        // inside DatabaseHelper.
        if (number.length() < 4
                || number.length() > 15
                || !number.matches("[A-Z0-9-]+")
                || !number.matches(".*[A-Z].*")
                || !number.matches(".*[0-9].*")) {

            edtVehicleNumber.setError(
                    "Enter a valid vehicle number"
            );
            edtVehicleNumber.requestFocus();
            return;
        }

        if (availableSlots.isEmpty()
                || spParkingSlot.getSelectedItemPosition() < 0
                || spParkingSlot.getSelectedItemPosition()
                >= availableSlots.size()) {

            Toast.makeText(
                    this,
                    "No suitable slots available",
                    Toast.LENGTH_LONG
            ).show();

            loadAvailableSlots();
            return;
        }

        Object selectedType = spVehicleType.getSelectedItem();

        if (selectedType == null) {
            Toast.makeText(
                    this,
                    "Select a vehicle type",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String vehicleType = selectedType.toString();

        // Refresh the selected slot against current database state.
        loadAvailableSlots();

        if (availableSlots.isEmpty()
                || spParkingSlot.getSelectedItemPosition() < 0
                || spParkingSlot.getSelectedItemPosition()
                >= availableSlots.size()) {

            Toast.makeText(
                    this,
                    "No suitable slots available",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        DatabaseHelper.ParkingSlot slot =
                availableSlots.get(
                        spParkingSlot.getSelectedItemPosition()
                );

        if (!vehicleType.equals(slot.vehicleType)
                || !"Available".equals(slot.status)) {

            Toast.makeText(
                    this,
                    "Selected slot is not suitable",
                    Toast.LENGTH_SHORT
            ).show();

            loadAvailableSlots();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Allocation")
                .setMessage(
                        "Vehicle: " + number +
                                "\nType: " + vehicleType +
                                "\nParking slot: " + slot.number +
                                "\n\nProceed with allocation?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Allocate", (dialog, which) -> {

                    boolean success = db.allocateSlot(
                            number,
                            vehicleType,
                            slot.id
                    );

                    if (success) {
                        Toast.makeText(
                                this,
                                "Vehicle " + number +
                                        " allocated to " + slot.number,
                                Toast.LENGTH_LONG
                        ).show();

                        setResult(RESULT_OK);
                        finish();

                    } else {
                        Toast.makeText(
                                this,
                                "Allocation failed. Check the vehicle " +
                                        "number, vehicle type, and slot availability.",
                                Toast.LENGTH_LONG
                        ).show();

                        loadAvailableSlots();
                    }
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (db != null && spVehicleType != null) {
            loadAvailableSlots();
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
