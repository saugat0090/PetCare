package com.example.assignmenttask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.net.Uri;
import com.google.android.gms.maps.model.LatLng;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Map helpers: saved coordinates, the shared pin link, "Find nearby" links and the no-Play-services fallback. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MapsTest {

    @Test
    public void savedCoordinatesBecomeAMapPositionAndBadOnesAreRejected() {
        JSONObject place = new JSONObject();
        assertNull(BaseActivity.latLng(place));   // no pin saved yet

        Store.set(place, "latitude", "27.680000");
        Store.set(place, "longitude", "84.430000");
        LatLng position = BaseActivity.latLng(place);
        assertNotNull(position);
        assertEquals(27.68, position.latitude, 1e-6);
        assertEquals(84.43, position.longitude, 1e-6);

        Store.set(place, "latitude", "91");       // outside the valid range
        assertNull(BaseActivity.latLng(place));

        Store.set(place, "latitude", "abc");      // not a number
        assertNull(BaseActivity.latLng(place));
    }

    @Test
    public void pinLinkUsesTheStandardGoogleMapsFormat() {
        assertEquals("https://www.google.com/maps/search/?api=1&query=27.680000,84.430000", MapsConfig.pinLink(27.68, 84.43));
    }

    @Test
    public void nearbySearchLinkAsksTheMapsAppToSearchAroundAPosition() {
        Uri link = MapsConfig.nearbySearchUri("dog park", new LatLng(27.68, 84.43));
        assertEquals("geo", link.getScheme());
        assertEquals("geo:27.680000,84.430000?q=dog%20park", link.toString());
    }

    @Test
    public void nearbyQueryIsBuiltFromTheLocationCategory() {
        assertEquals("veterinary clinic", MapsConfig.nearbyQuery("Veterinary Clinic"));
        assertEquals("pet grooming", MapsConfig.nearbyQuery("Grooming Salon"));   // reworded so it does not find human salons
        assertEquals(MapsConfig.ANY_PET_PLACE, MapsConfig.nearbyQuery(""));
        assertEquals(MapsConfig.ANY_PET_PLACE, MapsConfig.nearbyQuery(null));
    }

    @Test
    public void mapsReportUnavailableWithoutPlayServices() {
        // Unit tests have no Google Play services.
        assertFalse(Maps.available(RuntimeEnvironment.getApplication()));
    }
}
