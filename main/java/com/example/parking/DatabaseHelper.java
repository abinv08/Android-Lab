
package com.example.parking;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "ParkingManager.db";
    private static final int DB_VERSION = 1;

    public static final String TWO_WHEELER = "Two-Wheeler";
    public static final String FOUR_WHEELER = "Four-Wheeler";

    // Default rate per started hour. Adjust to your requirements.
    public static final double FEE_PER_HOUR = 20.0;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(
                "CREATE TABLE ParkingSlots (" +
                        "slot_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "slot_number TEXT UNIQUE NOT NULL, " +
                        "vehicle_type TEXT NOT NULL " +
                        "CHECK(vehicle_type IN ('Two-Wheeler','Four-Wheeler')), " +
                        "status TEXT NOT NULL DEFAULT 'Available' " +
                        "CHECK(status IN ('Available','Occupied')))"
        );

        db.execSQL(
                "CREATE TABLE ParkingRecords (" +
                        "record_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "vehicle_number TEXT NOT NULL, " +
                        "vehicle_type TEXT NOT NULL, " +
                        "slot_id INTEGER NOT NULL, " +
                        "entry_time INTEGER NOT NULL, " +
                        "exit_time INTEGER DEFAULT NULL, " +
                        "status TEXT NOT NULL DEFAULT 'Active' " +
                        "CHECK(status IN ('Active','Completed')), " +
                        "FOREIGN KEY(slot_id) REFERENCES ParkingSlots(slot_id), " +
                        "CHECK((status='Active' AND exit_time IS NULL) OR " +
                        "(status='Completed' AND exit_time IS NOT NULL))" +
                        ")"
        );

        // A slot can have only one active session.
        db.execSQL(
                "CREATE UNIQUE INDEX idx_active_slot " +
                        "ON ParkingRecords(slot_id) WHERE status='Active'"
        );

        // A vehicle can have only one active session.
        db.execSQL(
                "CREATE UNIQUE INDEX idx_active_vehicle " +
                        "ON ParkingRecords(vehicle_number COLLATE NOCASE) " +
                        "WHERE status='Active'"
        );

        db.execSQL(
                "CREATE INDEX idx_records_entry_time " +
                        "ON ParkingRecords(entry_time DESC)"
        );

        // Create ten initial slots.
        for (int i = 1; i <= 10; i++) {
            ContentValues values = new ContentValues();

            values.put("slot_number",
                    String.format(Locale.US, "A%02d", i));

            values.put("vehicle_type",
                    i <= 5 ? TWO_WHEELER : FOUR_WHEELER);

            values.put("status", "Available");

            db.insertOrThrow("ParkingSlots", null, values);
        }
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase db, int oldVersion, int newVersion) {
        // Add database migrations here when the schema changes.
        // Do not drop tables: completed parking history must be retained.
    }

    // ---------------------------------------------------------
    // MODEL CLASSES
    // ---------------------------------------------------------

    public static class ParkingSlot {
        public int id;
        public String number;
        public String vehicleType;
        public String status;
    }

    public static class ParkingRecord {
        public int id;
        public String vehicleNumber;
        public String vehicleType;
        public String slotNumber;
        public long entryTime;
        public Long exitTime;
        public String status;
    }

    // ---------------------------------------------------------
    // VALIDATION
    // ---------------------------------------------------------

    private String normalizeVehicleNumber(String vehicleNumber) {
        if (vehicleNumber == null) {
            return null;
        }

        String number = vehicleNumber.trim()
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);

        if (number.isEmpty()
                || number.length() < 4
                || number.length() > 15
                || !number.matches("[A-Z0-9-]+")
                || !number.matches(".*[A-Z].*")
                || !number.matches(".*[0-9].*")) {
            return null;
        }

        return number;
    }

    private boolean isValidVehicleType(String type) {
        return TWO_WHEELER.equals(type)
                || FOUR_WHEELER.equals(type);
    }

    // ---------------------------------------------------------
    // SLOT QUERIES
    // ---------------------------------------------------------

    public ArrayList<ParkingSlot> getSlots() {
        ArrayList<ParkingSlot> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor c = db.rawQuery(
                "SELECT slot_id, slot_number, vehicle_type, status " +
                        "FROM ParkingSlots ORDER BY slot_id",
                null)) {

            while (c.moveToNext()) {
                ParkingSlot slot = new ParkingSlot();

                slot.id = c.getInt(0);
                slot.number = c.getString(1);
                slot.vehicleType = c.getString(2);
                slot.status = c.getString(3);

                list.add(slot);
            }
        }

        return list;
    }

    public int getSlotCount(String status) {
        if (status == null) {
            return 0;
        }

        SQLiteDatabase db = getReadableDatabase();

        try (Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM ParkingSlots WHERE status=?",
                new String[]{status})) {

            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int getTotalSlots() {
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM ParkingSlots", null)) {

            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int getActiveRecordCount() {
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM ParkingRecords WHERE status='Active'",
                null)) {

            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public int getCompletedRecordCount() {
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM ParkingRecords WHERE status='Completed'",
                null)) {

            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    // ---------------------------------------------------------
    // ALLOCATE A PARKING SLOT
    // ---------------------------------------------------------

    public boolean allocateSlot(
            String vehicleNumber, String vehicleType, int slotId) {

        String number = normalizeVehicleNumber(vehicleNumber);

        if (number == null
                || !isValidVehicleType(vehicleType)
                || slotId <= 0) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();
        boolean success = false;

        db.beginTransaction();

        try {
            // Verify slot availability and supported vehicle type.
            try (Cursor c = db.rawQuery(
                    "SELECT slot_id FROM ParkingSlots " +
                            "WHERE slot_id=? AND status='Available' " +
                            "AND vehicle_type=?",
                    new String[]{
                            String.valueOf(slotId),
                            vehicleType
                    })) {

                if (!c.moveToFirst()) {
                    return false;
                }
            }

            // Reject duplicate active parking sessions.
            try (Cursor c = db.rawQuery(
                    "SELECT record_id FROM ParkingRecords " +
                            "WHERE vehicle_number=? COLLATE NOCASE " +
                            "AND status='Active' LIMIT 1",
                    new String[]{number})) {

                if (c.moveToFirst()) {
                    return false;
                }
            }

            // Insert a new record; never overwrite old history.
            ContentValues record = new ContentValues();

            record.put("vehicle_number", number);
            record.put("vehicle_type", vehicleType);
            record.put("slot_id", slotId);
            record.put("entry_time", System.currentTimeMillis());
            record.put("status", "Active");

            db.insertOrThrow("ParkingRecords", null, record);

            // Update only an available slot.
            ContentValues slot = new ContentValues();
            slot.put("status", "Occupied");

            int updated = db.update(
                    "ParkingSlots",
                    slot,
                    "slot_id=? AND status='Available' " +
                            "AND vehicle_type=?",
                    new String[]{
                            String.valueOf(slotId),
                            vehicleType
                    });

            if (updated != 1) {
                return false;
            }

            db.setTransactionSuccessful();
            success = true;

        } catch (SQLiteException | IllegalArgumentException e) {
            success = false;

        } finally {
            db.endTransaction();
        }

        return success;
    }

    // ---------------------------------------------------------
    // FIND AN ACTIVE VEHICLE
    // ---------------------------------------------------------

    public ParkingRecord findActiveVehicle(String vehicleNumber) {
        String number = normalizeVehicleNumber(vehicleNumber);

        if (number == null) {
            return null;
        }

        SQLiteDatabase db = getReadableDatabase();

        try (Cursor c = db.rawQuery(
                "SELECT r.record_id, r.vehicle_number, r.vehicle_type, " +
                        "s.slot_number, r.entry_time, r.exit_time, r.status " +
                        "FROM ParkingRecords r " +
                        "JOIN ParkingSlots s ON r.slot_id=s.slot_id " +
                        "WHERE r.vehicle_number=? COLLATE NOCASE " +
                        "AND r.status='Active' LIMIT 1",
                new String[]{number})) {

            if (!c.moveToFirst()) {
                return null;
            }

            return readRecord(c);
        }
    }

    private ParkingRecord readRecord(Cursor c) {
        ParkingRecord record = new ParkingRecord();

        record.id = c.getInt(0);
        record.vehicleNumber = c.getString(1);
        record.vehicleType = c.getString(2);
        record.slotNumber = c.getString(3);
        record.entryTime = c.getLong(4);
        record.exitTime = c.isNull(5) ? null : c.getLong(5);
        record.status = c.getString(6);

        return record;
    }

    // ---------------------------------------------------------
    // RELEASE A VEHICLE AND COMPLETE ITS SESSION
    // ---------------------------------------------------------

    public boolean releaseVehicle(String vehicleNumber) {
        String number = normalizeVehicleNumber(vehicleNumber);

        if (number == null) {
            return false;
        }

        SQLiteDatabase db = getWritableDatabase();
        boolean success = false;

        db.beginTransaction();

        try {
            int slotId;
            String slotStatus;
            String slotType;
            String recordType;

            // Check the active record and its associated slot.
            try (Cursor c = db.rawQuery(
                    "SELECT r.slot_id, s.status, s.vehicle_type, " +
                            "r.vehicle_type " +
                            "FROM ParkingRecords r " +
                            "JOIN ParkingSlots s ON r.slot_id=s.slot_id " +
                            "WHERE r.vehicle_number=? COLLATE NOCASE " +
                            "AND r.status='Active' LIMIT 1",
                    new String[]{number})) {

                if (!c.moveToFirst()) {
                    return false;
                }

                slotId = c.getInt(0);
                slotStatus = c.getString(1);
                slotType = c.getString(2);
                recordType = c.getString(3);
            }

            // Do not release a mismatched or inconsistent slot.
            if (!"Occupied".equals(slotStatus)
                    || !slotType.equals(recordType)) {
                return false;
            }

            long exitTime = System.currentTimeMillis();

            ContentValues record = new ContentValues();
            record.put("exit_time", exitTime);
            record.put("status", "Completed");

            int changed = db.update(
                    "ParkingRecords",
                    record,
                    "vehicle_number=? COLLATE NOCASE AND status='Active'",
                    new String[]{number});

            if (changed != 1) {
                return false;
            }

            ContentValues slot = new ContentValues();
            slot.put("status", "Available");

            int released = db.update(
                    "ParkingSlots",
                    slot,
                    "slot_id=? AND status='Occupied'",
                    new String[]{String.valueOf(slotId)});

            if (released != 1) {
                return false;
            }

            db.setTransactionSuccessful();
            success = true;

        } catch (SQLiteException | IllegalArgumentException e) {
            success = false;

        } finally {
            db.endTransaction();
        }

        return success;
    }

    // ---------------------------------------------------------
    // PARKING HISTORY
    // ---------------------------------------------------------

    public ArrayList<ParkingRecord> getHistory() {
        ArrayList<ParkingRecord> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor c = db.rawQuery(
                "SELECT r.record_id, r.vehicle_number, r.vehicle_type, " +
                        "s.slot_number, r.entry_time, r.exit_time, r.status " +
                        "FROM ParkingRecords r " +
                        "JOIN ParkingSlots s ON r.slot_id=s.slot_id " +
                        "ORDER BY r.entry_time DESC",
                null)) {

            while (c.moveToNext()) {
                list.add(readRecord(c));
            }
        }

        return list;
    }

    // ---------------------------------------------------------
    // SEARCH ALL RECORDS FOR A VEHICLE
    // ---------------------------------------------------------

    public ArrayList<ParkingRecord> searchVehicle(String vehicleNumber) {
        ArrayList<ParkingRecord> list = new ArrayList<>();
        String number = normalizeVehicleNumber(vehicleNumber);

        if (number == null) {
            return list;
        }

        SQLiteDatabase db = getReadableDatabase();

        try (Cursor c = db.rawQuery(
                "SELECT r.record_id, r.vehicle_number, r.vehicle_type, " +
                        "s.slot_number, r.entry_time, r.exit_time, r.status " +
                        "FROM ParkingRecords r " +
                        "JOIN ParkingSlots s ON r.slot_id=s.slot_id " +
                        "WHERE r.vehicle_number=? COLLATE NOCASE " +
                        "ORDER BY r.entry_time DESC",
                new String[]{number})) {

            while (c.moveToNext()) {
                list.add(readRecord(c));
            }
        }

        return list;
    }

    // ---------------------------------------------------------
    // PARKING DURATION AND FEE
    // ---------------------------------------------------------

    public long getParkingDurationMillis(ParkingRecord record) {
        if (record == null) {
            return 0L;
        }

        long endTime = record.exitTime != null
                ? record.exitTime
                : System.currentTimeMillis();

        return Math.max(0L, endTime - record.entryTime);
    }

    public double calculateParkingFee(ParkingRecord record) {
        long duration = getParkingDurationMillis(record);

        if (duration <= 0L) {
            return 0.0;
        }

        // Charge for each started hour, with a minimum of one hour.
        long hourMillis = 60L * 60L * 1000L;
        long hours = 1L + (duration - 1L) / hourMillis;

        return hours * FEE_PER_HOUR;
    }
}
