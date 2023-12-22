package edu.ucsb.ece150.locationplus;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MapsActivity extends AppCompatActivity implements LocationListener, OnMapReadyCallback {
    private final String APP_STATE = "AppState";
    private final String CENTERED = "CenteredOnMyPosition";
    private final String DESTINATION_LAT = "DestinationLatitude";
    private final String DESTINATION_LNG = "DestinationLongitude";
    private final String MOVING_TO_DESTINATION = "MovingToDestination";
    private final String VIEW_SATELLITES = "ViewingSatellitesList";
    private final int RC_HANDLE_ACCESS_FINE_LOCATION_PERMISSION = 1;
    private final int RC_HANDLE_POST_NOTIFICATION_PERMISSION = 2;
    private final int GEOFENCE_RADIUS_M = 100;
    private final int MY_PERMISSIONS_REQUEST_FINE_LOCATION = 3;
    // How long user stay in before dwell is triggered
    private final int GEOFENCE_LOITER_DELAY_MS = 10000;
    private final long GEOFENCE_EXPIRATION_MS = Geofence.NEVER_EXPIRE;
    private final String GEOFENCE_REQUEST_ID = "Target Destination";
    private final List<String> GEOFENCE_REQUEST_LIST = Collections.unmodifiableList(Arrays.asList(GEOFENCE_REQUEST_ID));
    private Geofence mGeofence;
    private GeofencingClient mGeofencingClient;
    private PendingIntent mPendingIntent = null;

    private FloatingActionButton mRemoveDestinationButton;

    private GnssStatus.Callback mGnssStatusCallback;
    private GoogleMap mMap;
    private LocationManager mLocationManager;

    private Marker mCurrentLocationMarker;

    private Toolbar mToolbar;

    private boolean centeredOnMyLocation = true;
    private boolean viewingSatelliteList = false;
    private boolean movingToDestination = false;
    //Graphics on the map that represents destination region
    private Circle mCircle;
    private Marker mDestinationMarker;

    private FrameLayout mFrameLayout;
    private ArrayAdapter mAdapter;
    private TextView mSatelliteInfoHeader;
    private ArrayList<Satellite> mSatellites = new ArrayList<>();
    private ListView mListView;
    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mSharedPreferencesEditor;
    private final float NULL_COORDINATE_PLACEHOLDER = -10000;
    private float destinationLat = NULL_COORDINATE_PLACEHOLDER;
    private float destinationLng = NULL_COORDINATE_PLACEHOLDER;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Set up Google Maps
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mRemoveDestinationButton = findViewById(R.id.RemoveDestinationButton);
        mRemoveDestinationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (movingToDestination) {
                    AlertDialog cancelConfirmation = new AlertDialog.Builder(MapsActivity.this)
                            .setTitle("Remove Destination")
                            .setMessage("Would you like to remove the current destination?")
                            .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    removeDestinationGeofence();
                                }
                            })
                            .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                }
                            }).create();
                    cancelConfirmation.show();
                }
            }
        });

        // Set up Geofencing Client
        mGeofencingClient = LocationServices.getGeofencingClient(MapsActivity.this);

        // Set up Satellite List
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        mGnssStatusCallback = new GnssStatus.Callback() {
            @Override
            public void onSatelliteStatusChanged(GnssStatus status) {
                // Update mSatellites when the satellite status is updated
                if (status.getSatelliteCount() == 0) return;
                mSatellites.clear();
                for (int index = 0; index < status.getSatelliteCount(); index++) {
                    mSatellites.add(new Satellite(
                            index,
                            status.getAzimuthDegrees(index),
                            status.getCarrierFrequencyHz(index),
                            status.getCn0DbHz(index),
                            status.getConstellationType(index),
                            status.getElevationDegrees(index),
                            status.getSvid(index)
                    ));
                }
                mAdapter.notifyDataSetChanged();
                mSatelliteInfoHeader.setText((new StringBuilder(""))
                        .append("Number of Satellites: ").append(status.getSatelliteCount())
                        .toString()
                );
            }
        };

        //setup for viewing satellite information (lists, adapters, etc.)
        mFrameLayout = findViewById(R.id.frameLayout);
        mSatelliteInfoHeader = findViewById(R.id.satelliteInformationHeader);
        mAdapter = new ArrayAdapter<>(MapsActivity.this, android.R.layout.simple_expandable_list_item_1, mSatellites);
        mListView = findViewById(R.id.satelliteList);
        mListView.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                final Satellite clickedSatellite = mSatellites.get((int) id);

                AlertDialog satelliteInformation = new AlertDialog.Builder(MapsActivity.this)
                        .setTitle(clickedSatellite.toString())
                        .setMessage(clickedSatellite.getSatelliteInformation())
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        })
                        .create();
                satelliteInformation.show();
            }
        });

        // Set up Toolbar
        mToolbar = (Toolbar) findViewById(R.id.appToolbar);
        setSupportActionBar(mToolbar);

        // Set up SharedPreferences
        mSharedPreferences = getSharedPreferences(APP_STATE, MODE_PRIVATE);
        mSharedPreferencesEditor = mSharedPreferences.edit();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // [TODO] Implement behavior when Google Maps is ready


        // Create a Geofence for the destination and listening for transitions that indicate
        // arrival or Dwell in there already
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(@NonNull LatLng latLng) {
                if (movingToDestination) {
                    Toast.makeText(MapsActivity.this, "There is already an existing destination. Remove the existing destination and try again.", Toast.LENGTH_SHORT).show();
                } else {
                    AlertDialog confirmDestination = new AlertDialog.Builder(MapsActivity.this)
                            .setTitle("Confirm Destination")
                            .setMessage(String.format("Set position (%.3f°, %.3f°) as your destination?", latLng.latitude, latLng.longitude))
                            .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    movingToDestination = true;
                                    destinationLat = (float) latLng.latitude;
                                    destinationLng = (float) latLng.longitude;
                                    addDestinationGeofence(latLng);
                                }
                            })
                            .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.dismiss();
                                }
                            })
                            .create();
                    confirmDestination.show();
                }

            }
        });
    }

    @Override
    public void onLocationChanged(Location location) {
        LatLng newLocation = new LatLng(location.getLatitude(), location.getLongitude());
        if (mCurrentLocationMarker == null) {
            mCurrentLocationMarker = mMap.addMarker(new MarkerOptions()
                    .position(newLocation)
                    .title("Current Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        } else {
            mCurrentLocationMarker.setPosition(newLocation);
        }
        if (centeredOnMyLocation) {
            float zoomLevel = 15.0f;
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLocation, zoomLevel));
        }
    }

    /*
     * The following three methods onProviderDisabled(), onProviderEnabled(), and onStatusChanged()
     * do not need to be implemented -- they must be here because this Activity implements
     * LocationListener.
     *
     * You may use them if you need to.
     */
    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    private GeofencingRequest getGeofenceRequest() {
        return new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER | GeofencingRequest.INITIAL_TRIGGER_DWELL)
                .addGeofence(mGeofence)
                .build();
    }

    private PendingIntent getGeofencePendingIntent() {
        if (mPendingIntent != null)
            return mPendingIntent;

        Intent intent = new Intent(MapsActivity.this, GeofenceBroadcastReceiver.class);
        mPendingIntent = PendingIntent.getBroadcast(MapsActivity.this, 0, intent, PendingIntent.FLAG_MUTABLE);
        return mPendingIntent;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onStart() throws SecurityException {
        super.onStart();
        int accessFineLocationPermissionGranted = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION);
        if (accessFineLocationPermissionGranted != PackageManager.PERMISSION_GRANTED) {
            final String[] permission = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
            ActivityCompat.requestPermissions(this, permission, RC_HANDLE_ACCESS_FINE_LOCATION_PERMISSION);
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            final String[] permission = new String[]{android.Manifest.permission.POST_NOTIFICATIONS};
            ActivityCompat.requestPermissions(this, permission, RC_HANDLE_POST_NOTIFICATION_PERMISSION);
            return;
        }

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
        mLocationManager.registerGnssStatusCallback(mGnssStatusCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();

        centeredOnMyLocation = mSharedPreferences.getBoolean(CENTERED, true);
        movingToDestination = mSharedPreferences.getBoolean(MOVING_TO_DESTINATION, false);
        destinationLat = mSharedPreferences.getFloat(DESTINATION_LAT, NULL_COORDINATE_PLACEHOLDER);
        destinationLng = mSharedPreferences.getFloat(DESTINATION_LNG, NULL_COORDINATE_PLACEHOLDER);

        // [TODO] Data recovery
        if(getIntent().getBooleanExtra("GeofenceTriggered", false)) {
            Log.d("Geofence", "Arrived at destination");
            getIntent().removeExtra("GeofenceTriggered");
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancelAll();
            removeDestinationGeofence();
        }

        if(movingToDestination) {
            Log.d("Geofence", "onResume");
            addDestinationGeofence(new LatLng(destinationLat, destinationLng));
        } else {
            mRemoveDestinationButton.hide();
        }

        viewingSatelliteList = mSharedPreferences.getBoolean(VIEW_SATELLITES, false);
        mFrameLayout.setVisibility(viewingSatelliteList ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Data saving
        mSharedPreferencesEditor.clear();
        mSharedPreferencesEditor.putBoolean(CENTERED, centeredOnMyLocation);

        mSharedPreferencesEditor.putBoolean(MOVING_TO_DESTINATION, movingToDestination);
        mSharedPreferencesEditor.putFloat(DESTINATION_LAT, destinationLat);
        mSharedPreferencesEditor.putFloat(DESTINATION_LNG, destinationLng);

        mSharedPreferencesEditor.putBoolean(VIEW_SATELLITES, viewingSatelliteList);
        mSharedPreferencesEditor.commit();

        if(mPendingIntent != null) {
            Log.d("Geofence", "onPause");
            removeDestinationGeofence();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onStop() {
        super.onStop();

        mLocationManager.removeUpdates(this);
        mLocationManager.unregisterGnssStatusCallback(mGnssStatusCallback);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        mToolbar.inflateMenu(R.menu.actions);
        mToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int menuItemID = item.getItemId();
                if (menuItemID == R.id.toggleCenter) {
                    centeredOnMyLocation = !centeredOnMyLocation;
                    Toast.makeText(MapsActivity.this, "Auto Center " + (centeredOnMyLocation ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
                } else if (menuItemID == R.id.viewSatellite) {
                    viewingSatelliteList = !viewingSatelliteList;
                    mFrameLayout.setVisibility(viewingSatelliteList ? View.VISIBLE : View.GONE);
                }
                return true;
            }
        });
        return true;
    }


    private void addDestinationGeofence(LatLng latLng) {
        Log.d("Geofence", "Indside addDestinationGeofence");
        if (mMap != null) {
            mDestinationMarker = mMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title("Destination")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .alpha(0.8f));
            mCircle = mMap.addCircle(new CircleOptions()
                    .center(latLng)
                    .radius(GEOFENCE_RADIUS_M)
                    .fillColor(0x44FF0000)
                    .strokeColor(Color.TRANSPARENT)
                    .strokeWidth(2));
        }
        mGeofence = new Geofence.Builder()
                .setRequestId(GEOFENCE_REQUEST_ID)
                .setCircularRegion(latLng.latitude, latLng.longitude, GEOFENCE_RADIUS_M)
                .setExpirationDuration(GEOFENCE_EXPIRATION_MS)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(GEOFENCE_LOITER_DELAY_MS)
                .build();
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_FINE_LOCATION);
            // then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission
            return;
        }
        mGeofencingClient.addGeofences(getGeofenceRequest(), getGeofencePendingIntent())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d("Geofence", "Geofence added successfully");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e("Geofence", "Geofence did not get added", e);
                    }
                });
        mRemoveDestinationButton.show();

    }

    private void removeDestinationGeofence() {
        Log.d("Geofence", "Inside removeDestinationGeofence");
        movingToDestination = false;
        destinationLat = NULL_COORDINATE_PLACEHOLDER;
        destinationLng = NULL_COORDINATE_PLACEHOLDER;
        mRemoveDestinationButton.hide();
        if(mDestinationMarker != null)
            mDestinationMarker.remove();
        if(mCircle != null)
            mCircle.remove();
        mGeofencingClient.removeGeofences(GEOFENCE_REQUEST_LIST)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d("Geofence", "Geofence removed successfully");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.d("Geofence", "Geofence did not get removed successfully");
                    }
                });
        mPendingIntent = null;

    }
}
