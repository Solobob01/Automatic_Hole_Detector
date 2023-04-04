package com.example.anomaly_detector_c1;

import android.Manifest.permission;
import android.annotation.SuppressLint;

import com.example.anomaly_detector_c1.databinding.ActivityMapsBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Environment;
import android.os.Handler;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapsActivity extends AppCompatActivity
        implements GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnMyLocationClickListener,
        OnMapReadyCallback, SensorEventListener{

    //variables for location
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private UiSettings uiSettings;
    private ActivityMapsBinding binding;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private ArrayList<LatLng> listAnomalyPositions;

    //variables for detecting anomalies
    final int CHART_UPDATES_PER_SECOND = 12;
    final float THRESHOLD = 6 ;
    SensorManager sm = null;                            // access Sensor subsystem
    Sensor accelSensor;                                       // connection to acceleration sensor
    ArrayList<Float> accelValues = new ArrayList();
    ArrayList accelTimestamps = new ArrayList();
    ArrayList<Float> processedValues = new ArrayList();
    ArrayList anomalyTimestamps = new ArrayList();
    long startTime = 0;
    int countdown = 0;

    //variables for drawingRealTimeRoute
    private ArrayList<LatLng> posRoute;
    private Polyline gpsTrack;


    //variables for writePermission
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private long timeSaveCurLocation = 1;
    private Handler timerHandlerLocation = new Handler();
    private boolean shouldRunLocation = true;
    private Runnable timerRunnableLocation = new Runnable() {
        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            if (shouldRunLocation) {

                //run again after 200 milliseconds (1/5 sec)
                fusedLocationProviderClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if(location != null){
                            drawRoute(location);
                        }
                    }
                });
                timerHandlerLocation.postDelayed(this, 1000*timeSaveCurLocation);
            }
        }
    };

    public void drawRoute(Location location){
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        if(gpsTrack == null){
            return;
        }
        List<LatLng> points = gpsTrack.getPoints();
        points.add(latLng);
        gpsTrack.setPoints(points);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        listAnomalyPositions = new ArrayList<>();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        fusedLocationProviderClient = new FusedLocationProviderClient(this);

        // Setup sensor
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelSensor = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        startTime = System.currentTimeMillis();
        processedValues.add(0f);
        processedValues.add(0f);

    }

    private long timeDelaySaveData = 10;
    private Handler timerHandlerWrite = new Handler();
    private boolean shouldRunWrite = true;
    private Runnable timerRunnableWrite = new Runnable() {
        @Override
        public void run() {
            if (shouldRunWrite) {
                System.out.println("fsavadbhgefsASFDVSDBGFDXVbdvc\n\n");
                verifyStoragePermissions(MapsActivity.this);
                String finalString = "";
                finalString += Arrays.toString(listAnomalyPositions.toArray());
                finalString += "\n\n";
                finalString += Arrays.toString(anomalyTimestamps.toArray());
                finalString += "\n\n";
                writeFileOnInternalStorage(getApplicationContext(),"anomalyInfo",finalString);
                timerHandlerWrite.postDelayed(this, 1000 * timeDelaySaveData);
            }
        }
    };

    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    public void writeFileOnInternalStorage(Context mcoContext, String sFileName, String sBody){
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        try {
            FileWriter writer = new FileWriter(new File(dir, sFileName + "_" + System.currentTimeMillis() + ".txt"));
            writer.write(sBody);
            writer.close();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // ignoring this
    }

    long prevTime = 0;
    // Most important function, called many times per second
    @SuppressLint("MissingPermission")
    public void onSensorChanged(SensorEvent event) {
        synchronized ( this ) {
            if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                // Get current time, to slow the number of updates to UPDATES_PER_SECOND
                long curTime = event.timestamp;
                if (curTime - prevTime > (1000000000 / CHART_UPDATES_PER_SECOND)) {

                    int i = accelValues.size();
                    // Add raw data
                    accelValues.add(event.values[1]);
                    long current_time = System.currentTimeMillis()-startTime;
                    // Add timestamp of data
                    accelTimestamps.add(current_time);
                    //Compute moving average, currently simple moving average of 3 entries ( maybe raise to 4-5?)
                    if(i>=2) {
                        // Add filtered value
                        processedValues.add(((processedValues.get(i - 1) + processedValues.get(i - 2) + accelValues.get(i - 1)) / 3));
                    }
                    // Do not check for anomaly if already found one very recently (less than one second ago)
                    if(countdown>0){
                        countdown--;
                    }
                    else if(Math.abs(processedValues.get(i)) > THRESHOLD){
                        // FOUND ANOMALY, RECORD LOCATION AND PUT MAP MARKER HERE
                        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                            @Override
                            public void onSuccess(Location location) {
                                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                                listAnomalyPositions.add(latLng);
                                mMap.addMarker(new MarkerOptions().title("Anomaly " + listAnomalyPositions.size()).position(latLng));
                                Toast toast = Toast.makeText(getApplicationContext(), "Anomaly found!", Toast.LENGTH_SHORT);
                                toast.show();
                            }
                        });
                        anomalyTimestamps.add(current_time);
                        countdown = CHART_UPDATES_PER_SECOND;
                    }

                    prevTime = curTime;
                }
            }
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        timerHandlerLocation.postDelayed(timerRunnableLocation, 0);
        timerHandlerWrite.postDelayed(timerRunnableWrite, 0);

        sm.registerListener(this, accelSensor,SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onStop() {
        shouldRunLocation = false;
        shouldRunWrite = false;
        timerHandlerLocation.removeCallbacksAndMessages(timerRunnableLocation);
        timerHandlerWrite.removeCallbacksAndMessages(timerRunnableWrite);
        sm.unregisterListener(this);
        super.onStop();
    }


    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        uiSettings = mMap.getUiSettings();
        if (ActivityCompat.checkSelfPermission(this, permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MapsActivity.this, new String[]{permission.ACCESS_FINE_LOCATION}, 44);
            return;
        }
        mMap.setMyLocationEnabled(true);
        mMap.setOnMyLocationButtonClickListener(this);
        mMap.setOnMyLocationClickListener(this);
        uiSettings.setZoomControlsEnabled(true);
        gpsTrack = mMap.addPolyline(new PolylineOptions().color(Color.BLUE));

    }

    /**
     * Enables the My Location layer if the fine location permission has been granted.
     */
    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        // 1. Check if permissions are granted, if so, enable the my location layer
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            return;
        }
    }

    @Override
    public boolean onMyLocationButtonClick() {
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false;
    }

    @Override
    public void onMyLocationClick(@NonNull Location location) {
        Toast.makeText(this, "Current location:\n" + location.getLatitude() + " " + location.getLongitude(), Toast.LENGTH_LONG).show();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }
        enableMyLocation();
    }
}