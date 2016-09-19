package com.olm.crimemap;

import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.geojson.GeoJsonFeature;
import com.google.maps.android.geojson.GeoJsonLayer;
import com.google.maps.android.geojson.GeoJsonPoint;
import com.google.maps.android.geojson.GeoJsonPointStyle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * By Tyson Macdonald, Sept 18, 2016
 *
 * This activity displays map data taken from the source https://data.sfgov.org/resource/ritf-b9ki.geojson
 *
 * On initial load, the app displays SF police district crime data, with map marker hue
 * indicating the ordering of the districts in terms of incident count.  Stronger hue indicates
 * a larger number of incidents.  Clicking on each marker will reveal the incident count,
 * while panning the map to place the marker in the center.
 *
 * The initial query is defined in the string resource, SFPD_Incidents_request,
 * which requests the number in incidents per SF police district and the location of that district,
 * in order of incident count. Further, that ordering is made descending by the string resource DESC.
 *
 * When a valid geoJSON result is returned, the order of the groups is recorded in mPointOrder
 * for later reference.
 * That geoJSON result is made into map layers -- a process where the ordering of the geoJSON
 * data is lost -- and the map layers are modified to include the location of the police districts.
 * Marker colors are assigned by the ordering recorded in mPointOrder.
 *
 * The application also has a search term function, which is available via the magnifying glass icon
 * in the app toolbar.  Tapping on the magnifying glass will cause a search term text box to
 * animate open.  Tapping on the icon again, will cause the search box to close.
 * When the search box is open, text can be entered into the box.  With text in the box, pressing
 * the search icon again, or pressing the keyboard return button, will execute the search, with the
 * search term being displayed as the toolbar title.  If the search term is included in any database
 * field, a marker is shown on the map.
 *
 * TODO: implement paging though the search term results as there can be many.
 *
 * While search results are displayed, hitting the system back button will return the map to showing
 * the original district data.
 *
 * All queries are made in an asyncTask, DownloadGeoJsonFile.
 *
 */

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MapsActivity";
    private static final int NUMBER_QUERY_MONTHS = -1;  // how many months do we look back in time
    private static final float DEFAULT_ZOOM = 11.5f;
    private GoogleMap mMap;

    private EditText mSearchBox;
    private LayoutWidthAnimator mSearchBoxAnimator;

    private boolean mIsFileParsingError = false;
    private long mQueryDate;


    private HashMap<String,String> mPointOrder = new HashMap<>();
    private static final String POINT_ORDER_KEY = "mPointOrder_key";


    // flag for whether the search box is open
    private boolean mSearchBoxExtended = false;
    private static final String SEARCH_BOX_EXTENDED_KEY = "mSearchBoxExtended_key";

    // flag for whether we are in a search state
    private boolean mSearchInProgress = false;
    private static final String SEARCH_IN_PROGRESS_KEY = "mSearchInProgress_key";


    // string for storing the district query results for later review after searches
    private String mDistrictResults = "";
    private static final String DISTRICT_RESULTS_KEY = "mDistrictResults_key";

    // string for storing the district query results for later review after searches
    private String mSearchResults = "";
    private static final String SEARCH_RESULTS_KEY = "mSearchResults_key";



    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        mapFragment.getMapAsync(this);


        // Find the toolbar view inside the activity layout
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        // Sets the Toolbar to act as the ActionBar for this Activity window.
        // Make sure the toolbar exists in the activity and is not null
        setSupportActionBar(toolbar);

        // Remove default title text
        getSupportActionBar().setDisplayShowTitleEnabled(true);

        // create a search box for the toolbar
        mSearchBox = (EditText) findViewById(R.id.searchBox);
        FrameLayout searchLayout = (FrameLayout) findViewById(R.id.searchBoxLayout);
        searchLayout.setVisibility(View.GONE);  //initial state of search box

        // set the animation class for the search box
        mSearchBoxAnimator = new LayoutWidthAnimator(searchLayout,
                getResources().getDimensionPixelSize(R.dimen.search_box_length));


        // setup listener for the ENTER signal from the keyboard
        mSearchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE ||

                        (actionId == EditorInfo.IME_NULL &&
                                event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                                event.getAction() == KeyEvent.ACTION_DOWN)) {

                    searchButtonPushed();
                    return false;
                }
                return true;
            }
        });


        // get the query date, one month ago
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, NUMBER_QUERY_MONTHS);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        mQueryDate = cal.getTimeInMillis();



        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();

    }



    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Maps Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.olm.crimemap/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Maps Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.olm.crimemap/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

    // Menu icons are inflated just as they were with actionbar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu_main; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            // Sends a request to the People app to display the create contact screen
            case R.id.search:

                searchButtonPushed();
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);


        outState.putBoolean(SEARCH_BOX_EXTENDED_KEY, mSearchBoxExtended);
        outState.putBoolean(SEARCH_IN_PROGRESS_KEY, mSearchInProgress);
        outState.putString(DISTRICT_RESULTS_KEY, mDistrictResults);
        outState.putSerializable(POINT_ORDER_KEY, mPointOrder);
        outState.putString(SEARCH_RESULTS_KEY, mSearchResults);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can restore the view hierarchy
        super.onRestoreInstanceState(savedInstanceState);

        mSearchBoxExtended = savedInstanceState.getBoolean(SEARCH_BOX_EXTENDED_KEY);
        if(mSearchBoxExtended){
            mSearchBoxAnimator.extend();
        }

        mSearchInProgress = savedInstanceState.getBoolean(SEARCH_IN_PROGRESS_KEY);

        mDistrictResults = savedInstanceState.getString(DISTRICT_RESULTS_KEY);

        mSearchResults = savedInstanceState.getString(SEARCH_RESULTS_KEY);

        mPointOrder = (HashMap<String,String>) savedInstanceState.getSerializable(POINT_ORDER_KEY);
    }



    /**
     * Method to handle the logic behind presses of the search icon.
     * We should open or close the search box with each press, alternating.
     * If the search box is open AND has text, a search query should be sent to the database
     */
    private void searchButtonPushed() {
        if (mSearchBoxExtended) {

            // remove the search box
            mSearchBoxAnimator.retract();

            // run the search on any search terms
            if (!mSearchBox.getText().toString().isEmpty()) {

                // perform the search, making sure there is no extra spaces
                // also converting the term to upper case since all database records are the same.
                startSearch(mSearchBox.getText().toString().trim().toUpperCase());
            }

        } else {
            //show the search box
            mSearchBoxAnimator.extend();

            // place cursor in text box
            mSearchBox.setSelection(mSearchBox.getText().length());

        }

        // toggle search box state record
        mSearchBoxExtended = !mSearchBoxExtended;
    }

    /**
     * When the back button is pressed, the app title should return, the search box should clear,
     * and the original district map should return
     */
    @Override
    public void onBackPressed(){

        if(!mDistrictResults.isEmpty()) {
            restoreDistrictMap();
        }

        searchBoxClear(null);
        getSupportActionBar().setTitle(getString(R.string.app_name));

    }

    /**
     * Method to clear the search box of text when the X next to the search box is pressed
     */
    public void searchBoxClear(View view) {
        mSearchBox.setText("");

        mSearchInProgress = false;
    }


    /**
     * Method to initialize the search based on the given string
     * @param s String to form a database request with.
     */
    private void startSearch(String s) {

        // searching is initiated
        mSearchInProgress = true;

        // set the activity title to reflect the new search term
        String search_feedback = String.format(getString(R.string.search_feedback), s);
        getSupportActionBar().setTitle(search_feedback);

        // initiate the database query with the string s
        retrieveSearchFileFromUrl(s);

        // clear the last data from the map
        mMap.clear();
    }


    /**
     * Initial building of the map
     *
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // restore the previously retrieved district map results
        if(!mDistrictResults.isEmpty() && !mSearchInProgress){
            restoreDistrictMap();

        }else if(!mSearchResults.isEmpty() && mSearchInProgress){

            restoreSearchMap();
        }else {

            // otherwise, retrieve the district data for the first time
            retrievePoliceDistrictFileFromUrl();
        }
        //TODO: There should probably be another state where the search is in progress but the results are still empty

        // Move the camera to San Francisco
        LatLng sanFrancisco = new LatLng(37.7749, -122.4194);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(sanFrancisco, DEFAULT_ZOOM));


        // set a few properties of the map
        UiSettings uiSettings = mMap.getUiSettings();
        uiSettings.setZoomControlsEnabled(true);
        uiSettings.setCompassEnabled(false);
        uiSettings.setMyLocationButtonEnabled(false);
        uiSettings.setScrollGesturesEnabled(true);
        uiSettings.setZoomGesturesEnabled(false);
        uiSettings.setTiltGesturesEnabled(true);
        uiSettings.setRotateGesturesEnabled(false);
    }


    /**
     * Method to return previously saved results to the map,
     * clearing the map of any previous data
     */
    private void restoreDistrictMap() {
        mMap.clear();

        try {
            addGeoJsonLayerToDistrictMap(new GeoJsonLayer(mMap, new JSONObject(mDistrictResults)));
        } catch (JSONException e) {
            Log.e(TAG, "GeoJSON file could not be converted to a JSONObject");
        }
    }

    /**
     * Method to return previously saved results to the map,
     * clearing the map of any previous data
     */
    private void restoreSearchMap() {
        mMap.clear();

        try {
            addGeoJsonSearchLayerToMap(new GeoJsonLayer(mMap, new JSONObject(mSearchResults)));
        } catch (JSONException e) {
            Log.e(TAG, "GeoJSON file could not be converted to a JSONObject");
        }
    }

    /**
     * Method to construct the initial query for the police district data.
     * The query form exists in the string resource, SFPD_Incidents_request, but
     * must include a string representing the query date of form SFPD_Incidents_query_date_format.
     *
     * To ensure descending order of the query results the query must have the string resource DESC
     * appended to it.
     *
     * The fully constructed query is then passed to the asyncTask for execution.
     */
    private void retrievePoliceDistrictFileFromUrl() {

        String query_date_format = getString(R.string.SFPD_Incidents_query_date_format);

        String decs = getString(R.string.DESC);

        String query_string = String.format(getString(R.string.SFPD_Incidents_request),
                getDateString(mQueryDate, query_date_format));

        new DownloadGeoJsonFile().execute(query_string+decs);
    }


    /**
     * Method to construct the query for the search term.
     * The query form exists in the string resource, SFPD_Incidents_search_term_request,
     * including the given search term.
     *
     * The fully constructed query is then passed to the asyncTask for execution.
     * @param search_term String of the search term to include in the database request.
     */
    private void retrieveSearchFileFromUrl(String search_term) {

        String query_date_format = getString(R.string.SFPD_Incidents_query_date_format);

        String query_string = String.format(
                getString(R.string.SFPD_Incidents_search_term_request),
                search_term,
                getDateString(mQueryDate, query_date_format));

        new DownloadGeoJsonFile().execute(query_string);
    }


    /**
     * AsyncTask task for sending out the query and processing the results.
     */
    private class DownloadGeoJsonFile extends AsyncTask<String, Void, GeoJsonLayer> {

        @Override
        protected GeoJsonLayer doInBackground(String... params) {

            try {
                // Open a stream from the URL
                InputStream stream = new URL(params[0]).openStream();

                String line;
                StringBuilder result = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

                while ((line = reader.readLine()) != null) {
                    // Read and save each line of the stream
                    result.append(line);
                }

                // Close the stream
                reader.close();
                stream.close();

                String search_result = result.toString();

                // Save the result for later restoration
                if(mSearchInProgress) {

                    mSearchResults = search_result;
                }else {
                    mDistrictResults = search_result;
                }

                // create the JSON object for the results
                JSONObject temp = new JSONObject(search_result);

                // save the sorted order of the geo objects
                savePointOrder(temp);

                // create the geographic layer that holds the response data
                return new GeoJsonLayer(mMap,temp);

            } catch (IOException e) {

                Log.e(TAG, "GeoJSON file could not be read");

                mIsFileParsingError = false;

            } catch (JSONException e) {
                Log.e(TAG, "GeoJSON file could not be converted to a JSONObject");

                mIsFileParsingError = true;
            }
            return null;
        }

        @Override
        protected void onPostExecute(GeoJsonLayer layer) {
            if (layer != null) {

                if(mSearchInProgress){
                    addGeoJsonSearchLayerToMap(layer);
                }else {
                    addGeoJsonLayerToDistrictMap(layer);
                }
            }else {

                //show type of error on the UI thread
                Toast.makeText(MapsActivity.this,
                        mIsFileParsingError ? R.string.file_parsing_error : R.string.server_error,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Since the request from the end point is already sorted in descending order and
     * adding the geoJSON data to the map layers destroys the order...
     *
     * Here we record the order of the request results for later reference.
     */
    private void savePointOrder(JSONObject thing) {

        String count;

        JSONObject item;
        JSONArray list;
        int list_size;

        try {
            list = thing.getJSONArray("features");
            list_size = list.length();

            for(int i = 0; i <list_size; i++){

                item = (JSONObject)list.get(i);

                Log.d(TAG, item.toString());

                item = item.getJSONObject("properties");
                count = item.getString("count");

                mPointOrder.put(count, String.format("%d", i+1));
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    /**
     * Add layers to the district map, after modifying the markers, and setting the action to
     * perform when clicking on the marker.
     *
     * @param layer GeoJsonLayer
     */
    private void addGeoJsonLayerToDistrictMap(GeoJsonLayer layer) {

        modifyDistrictMapMarkers(layer);
        layer.addLayerToMap();

        // Demonstrate receiving features via GeoJsonLayer clicks.
        layer.setOnFeatureClickListener(new GeoJsonLayer.GeoJsonOnFeatureClickListener() {
            @Override
            public void onFeatureClick(GeoJsonFeature feature) {

                // display the number of incidents in a toast
                Toast.makeText(MapsActivity.this,
                        String.format(getString(R.string.number_of_incidents),
                                feature.getProperty("count")),
                        Toast.LENGTH_SHORT).show();
            }
        });

    }


    /**
     * Add layers to the search query map, setting the action to perform when clicking on the markers.
     * @param layer
     */
    private void addGeoJsonSearchLayerToMap(GeoJsonLayer layer) {

        modifySearchMapMarkers(layer);
        layer.addLayerToMap();

        // Demonstrate receiving features via GeoJsonLayer clicks.
        layer.setOnFeatureClickListener(new GeoJsonLayer.GeoJsonOnFeatureClickListener() {
            @Override
            public void onFeatureClick(GeoJsonFeature feature) {

                // display the event description in a toast
                Toast.makeText(MapsActivity.this,
                        feature.getProperty("descript"),
                        Toast.LENGTH_SHORT).show();
                //TODO make the description into the marker title
            }
        });

    }


    /**
     * Creates a point for each feature based on the average Y and X data (lat. and long.)
     * and choosing the color of the marker (pointStyle) based on the original result order,
     * mPointOrder.
     * @param layer GeoJsonLayer
     */
    private void modifyDistrictMapMarkers(GeoJsonLayer layer) {

        int order_index;
        String count;
        GeoJsonPoint point;


        // Iterate over all the features stored in the layer
        for (GeoJsonFeature feature : layer.getFeatures()) {
            // Check if the magnitude property exists
            if (feature.getProperty("count") != null &&
                    feature.getProperty("avg_x") != null &&
                    feature.getProperty("count") != null) {

                // modify each geographic feature have a point with the given average coordinates
                point = new GeoJsonPoint(new LatLng(Float.parseFloat(feature.getProperty("avg_y")),
                        Float.parseFloat(feature.getProperty("avg_x"))));
                feature.setGeometry(point);


                // based on the incident count, get the order index for the entry
                count = feature.getProperty("count");
                order_index = Integer.parseInt(mPointOrder.get(count));

                // Get the icon for the feature, using the prescribed color based on order
                BitmapDescriptor pointIcon =
                        BitmapDescriptorFactory.defaultMarker(colorByOrder(order_index));

                // Create a new point style
                GeoJsonPointStyle pointStyle = new GeoJsonPointStyle();

                // Set options for the point style
                pointStyle.setIcon(pointIcon);
                pointStyle.setTitle(String.format(getString(R.string.incident_count), count));

                // Assign the point style to the feature
                feature.setPointStyle(pointStyle);
            }
        }
    }


    /**
     * Modify search marker to have a title
     * @param layer GeoJsonLayer
     */
    private void modifySearchMapMarkers(GeoJsonLayer layer) {

        // Iterate over all the features stored in the layer
        for (GeoJsonFeature feature : layer.getFeatures()) {


            // Get the icon for the feature, using the a bold color
            BitmapDescriptor pointIcon = BitmapDescriptorFactory.defaultMarker(colorByOrder(1));

            // Create a new point style
            GeoJsonPointStyle pointStyle = new GeoJsonPointStyle();

            // Set options for the point style
            pointStyle.setIcon(pointIcon);
            pointStyle.setTitle(feature.getProperty("descript"));

            // Assign the point style to the feature
            feature.setPointStyle(pointStyle);

        }
    }


    /**
     * Assigns color based on crime stats, also converting our color resources into hue values.
     */
    private float colorByOrder(int index) {
        float[] hsv = new float[3];

        switch (index){
            case 1:
                Color.colorToHSV(chooseColor(R.color.a), hsv);
                break;
            case 2:
                Color.colorToHSV(chooseColor(R.color.b), hsv);
                break;
            case 3:
                Color.colorToHSV(chooseColor(R.color.c), hsv);
                break;
            case 4:
                Color.colorToHSV(chooseColor(R.color.d), hsv);
                break;
            case 5:
                Color.colorToHSV(chooseColor(R.color.e), hsv);
                break;
            case 6:
                Color.colorToHSV(chooseColor(R.color.f), hsv);
                break;
            case 7:
                Color.colorToHSV(chooseColor(R.color.g), hsv);
                break;
            default:
                Color.colorToHSV(chooseColor(R.color.h), hsv);

        }
        return hsv[0];
    }

    /**
     * Returns date string based on the number of ms since epoc
     * Support for the getCountDown_Days method, made public for testing
     *
     * @param ms_since_epoc,  timestamp, number ms since epoc
     * @param out_date_format String, format for the timeString
     * @return String, string representation of the date
     */
    public static String getDateString(Long ms_since_epoc, final String out_date_format) {

        final SimpleDateFormat dateFormat = new SimpleDateFormat(out_date_format, Locale.US);
        //dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        String out_date = "";
        try {
            Date date = new Date(ms_since_epoc);

            out_date = dateFormat.format(date);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return out_date;
    }

    @SuppressWarnings("deprecation")
    private int chooseColor(int resource) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getColor(resource);
        } else {
            return getResources().getColor(resource);
        }
    }
}
