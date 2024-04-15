package eu.siacs.conversations.ui;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.location.Location;
import android.location.LocationListener;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;

import com.google.common.base.Strings;
import com.google.common.primitives.Doubles;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityShowLocationBinding;
import eu.siacs.conversations.ui.util.LocationHelper;
import eu.siacs.conversations.ui.util.UriHelper;
import eu.siacs.conversations.ui.widget.Marker;
import eu.siacs.conversations.ui.widget.MyLocation;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.LocationProvider;

import org.osmdroid.util.GeoPoint;

import java.util.Map;

public class ShowLocationActivity extends LocationActivity implements LocationListener {

    private GeoPoint loc = LocationProvider.FALLBACK;
    private ActivityShowLocationBinding binding;

    private Uri createGeoUri() {
        return Uri.parse("geo:" + this.loc.getLatitude() + "," + this.loc.getLongitude());
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_show_location);
        setSupportActionBar(binding.toolbar);

        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());

        configureActionBar(getSupportActionBar());
        setupMapView(this.binding.map, this.loc);

        this.binding.fab.setOnClickListener(view -> startNavigation());

        final Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        final String action = intent.getAction();
        switch (Strings.nullToEmpty(action)) {
            case "eu.siacs.conversations.location.show":
                if (intent.hasExtra("longitude") && intent.hasExtra("latitude")) {
                    final double longitude = intent.getDoubleExtra("longitude", 0);
                    final double latitude = intent.getDoubleExtra("latitude", 0);
                    this.loc = new GeoPoint(latitude, longitude);
                }
                break;
            case Intent.ACTION_VIEW:
                final Uri uri = intent.getData();
                if (uri == null) {
                    break;
                }
                final GeoPoint point;
                try {
                    point = GeoHelper.parseGeoPoint(uri);
                } catch (final Exception e) {
                    break;
                }
                this.loc = point;
                final Map<String, String> query = UriHelper.parseQueryString(uri.getQuery());
                final String z = query.get("z");
                final Double zoom = Strings.isNullOrEmpty(z) ? null : Doubles.tryParse(z);
                if (zoom != null) {
                    Log.d(Config.LOGTAG, "inferring zoom level " + zoom + " from geo uri");
                    mapController.setZoom(zoom);
                    gotoLoc(false);
                }
                break;
        }
        updateLocationMarkers();
    }

    @Override
    protected void gotoLoc(final boolean setZoomLevel) {
        if (this.loc != null && mapController != null) {
            if (setZoomLevel) {
                mapController.setZoom(Config.Map.FINAL_ZOOM_LEVEL);
            }
            mapController.animateTo(new GeoPoint(this.loc));
        }
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode,
            @NonNull final String[] permissions,
            @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateUi();
    }

    @Override
    protected void setMyLoc(final Location location) {
        this.myLoc = location;
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_show_location, menu);
        updateUi();
        return true;
    }

    @Override
    protected void updateLocationMarkers() {
        super.updateLocationMarkers();
        if (this.myLoc != null) {
            this.binding.map.getOverlays().add(new MyLocation(this, null, this.myLoc));
        }
        this.binding.map.getOverlays().add(new Marker(this.marker_icon, this.loc));
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final var itemId = item.getItemId();
        if (itemId == R.id.action_copy_location) {
            final ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            final ClipData clip = ClipData.newPlainText("location", createGeoUri().toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.url_copied_to_clipboard, Toast.LENGTH_SHORT).show();
            return true;
        } else if (itemId == R.id.action_share_location) {
            final Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_TEXT, createGeoUri().toString());
            shareIntent.setType("text/plain");
            try {
                startActivity(Intent.createChooser(shareIntent, getText(R.string.share_with)));
            } catch (final ActivityNotFoundException e) {
                // This should happen only on faulty androids because normally chooser is always
                // available
                Toast.makeText(this, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT)
                        .show();
            }
            return true;
        } else if (itemId == R.id.action_open_with) {
            final Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(createGeoUri());
            try {
                startActivity(Intent.createChooser(intent, getText(R.string.open_with)));
            } catch (final ActivityNotFoundException e) {
                // This should happen only on faulty androids because normally chooser is always
                // available
                Toast.makeText(this, R.string.no_application_found_to_open_file, Toast.LENGTH_SHORT)
                        .show();
            }
            return true;

        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void startNavigation() {
        final Intent intent = getStartNavigationIntent();
        startActivity(intent);
    }

    private Intent getStartNavigationIntent() {
        return new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                        "google.navigation:q="
                                + this.loc.getLatitude()
                                + ","
                                + this.loc.getLongitude()));
    }

    @Override
    protected void updateUi() {
        final Intent intent = getStartNavigationIntent();
        final ActivityInfo activityInfo = intent.resolveActivityInfo(getPackageManager(), 0);
        this.binding.fab.setVisibility(activityInfo == null ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onLocationChanged(@NonNull final Location location) {
        if (LocationHelper.isBetterLocation(location, this.myLoc)) {
            this.myLoc = location;
            updateLocationMarkers();
        }
    }

    @Override
    public void onStatusChanged(final String provider, final int status, final Bundle extras) {}

    @Override
    public void onProviderEnabled(@NonNull final String provider) {}

    @Override
    public void onProviderDisabled(@NonNull final String provider) {}
}
