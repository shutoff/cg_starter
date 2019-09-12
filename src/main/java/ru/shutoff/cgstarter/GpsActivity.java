package ru.shutoff.cgstarter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;

import java.util.Date;

public class GpsActivity extends AppCompatActivity {

    private LocationManager locationManager;
    private LocationListener netListener;
    private LocationListener gpsListener;

    Location currentBestLocation;
    boolean need_fine;

    void locationChanged() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();
        netListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                locationChanged(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, netListener);
            } else {
                netListener = null;
            }
        } catch (Exception ex) {
            netListener = null;
        }

        if ((netListener == null) || need_fine) {

            gpsListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    locationChanged(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {

                }

                @Override
                public void onProviderEnabled(String provider) {

                }

                @Override
                public void onProviderDisabled(String provider) {

                }
            };

            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, gpsListener);
            } catch (Exception ex) {
                gpsListener = null;
            }
        }

        locationChanged(getLastBestLocation());

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (netListener != null)
            locationManager.removeUpdates(netListener);
        if (gpsListener != null)
            locationManager.removeUpdates(gpsListener);
    }

    static final int TWO_MINUTES = 1000 * 60 * 2;

    @SuppressLint("MissingPermission")
    Location getLastBestLocation() {
        Location locationGPS = null;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                locationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
            // ignore
        }
        Location locationNet = null;
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                locationNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
            // ignore
        }
        long GPSLocationTime = 0;
        if (locationGPS != null)
            GPSLocationTime = locationGPS.getTime();
        long NetLocationTime = 0;
        if (locationNet != null)
            NetLocationTime = locationNet.getTime();
        if (GPSLocationTime > NetLocationTime)
            return locationGPS;
        return locationNet;
    }

    public void locationChanged(Location location) {
        if ((location != null) && isBetterLocation(location, currentBestLocation))
            currentBestLocation = location;
        if (currentBestLocation != null) {
            long t1 = currentBestLocation.getTime() + TWO_MINUTES;
            long t2 = new Date().getTime();
            if (t1 < t2)
                currentBestLocation = null;
        }
        locationChanged();
    }

    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null)
            return true;

        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null)
            return provider2 == null;
        return provider1.equals(provider2);
    }
}
