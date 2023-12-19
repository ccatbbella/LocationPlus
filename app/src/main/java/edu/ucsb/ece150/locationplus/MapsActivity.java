package edu.ucsb.ece150.locationplus;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

public class MapsActivity extends AppCompatActivity implements LocationListener, OnMapReadyCallback {

    private Geofence mGeofence;
    private GeofencingClient mGeofencingClient;
    private PendingIntent mPendingIntent = null;

    private GnssStatus.Callback mGnssStatusCallback;
    private GoogleMap mMap;
    private LocationManager mLocationManager;

    private Marker mCurrentLocationMarker;

    private Toolbar mToolbar;

    private boolean centeredOnMyLocation = true;
    private boolean viewingSatelliteList = false;

    private FrameLayout mFrameLayout;
    private ArrayAdapter mAdapter;
    private TextView mSatelliteInfoHeader;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Set up Google Maps
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Set up Geofencing Client
        mGeofencingClient = LocationServices.getGeofencingClient(MapsActivity.this);

        // Set up Satellite List
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        mGnssStatusCallback = new GnssStatus.Callback() {
            @Override
            public void onSatelliteStatusChanged(GnssStatus status) {
                // [TODO] Implement behavior when the satellite status is updated
            }
        };

        //setup for viewing satellite information (lists, adapters, etc.)
        mFrameLayout = findViewById(R.id.frameLayout);

        mSatelliteInfoHeader = findViewById(R.id.satelliteInformationHeader);


        // Set up Toolbar
        mToolbar = (Toolbar) findViewById(R.id.appToolbar);
        setSupportActionBar(mToolbar);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // [TODO] Implement behavior when Google Maps is ready


        // [TODO] In addition, add a listener for long clicks (which is the starting point for
        // creating a Geofence for the destination and listening for transitions that indicate
        // arrival)
    }

    @Override
    public void onLocationChanged(Location location) {
        LatLng newLocation = new LatLng(location.getLatitude(), location.getLongitude());
        if (mCurrentLocationMarker == null){
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
    public void onProviderDisabled(String provider) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    private GeofencingRequest getGeofenceRequest() {
        // [TODO] Set the initial trigger (i.e. what should be triggered if the user is already
        // inside the Geofence when it is created)

        return new GeofencingRequest.Builder()
                //.setInitialTrigger()  <--  Add triggers here
                .addGeofence(mGeofence)
                .build();
    }

    private PendingIntent getGeofencePendingIntent() {
        if(mPendingIntent != null)
            return mPendingIntent;

        Intent intent = new Intent(MapsActivity.this, GeofenceBroadcastReceiver.class);
        mPendingIntent = PendingIntent.getBroadcast(MapsActivity.this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return mPendingIntent;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onStart() throws SecurityException {
        super.onStart();

        // [TODO] Ensure that necessary permissions are granted (look in AndroidManifest.xml to
        // see what permissions are needed for this app)

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
        mLocationManager.registerGnssStatusCallback(mGnssStatusCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // [TODO] Data recovery
    }

    @Override
    protected void onPause() {
        super.onPause();

        // [TODO] Data saving
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onStop() {
        super.onStop();

        mLocationManager.removeUpdates(this);
        mLocationManager.unregisterGnssStatusCallback(mGnssStatusCallback);
    }

    public boolean onCreateOptionsMenu(Menu menu){
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
}
