package com.beardedgaul.android.wawime;

/**
 * Created by Florent on 28/04/2015.
 */

import android.content.Intent;
import android.content.IntentSender;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * A placeholder fragment containing a simple view.
 */
public class HistoryFragment extends Fragment {

    private final String LOG_TAG = HistoryFragment.class.getSimpleName();
    public ArrayAdapter<String> mActivitiesAdapter;

    private static final int REQUEST_OAUTH = 1;
    private static final String AUTH_PENDING = "auth_state_pending";
    private boolean authInProgress = false;

    private GoogleApiClient mClient = null;

    public HistoryFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            authInProgress = savedInstanceState.getBoolean(AUTH_PENDING);
        }
        this.buildFitnessClient();
        // Add this line in order for this fragment to handle menu events.
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //Préparation de l'adapter pour recevoir les données en provenance de google FIT API
        mActivitiesAdapter = new ArrayAdapter<String>(
                getActivity(),
                R.layout.list_item_activity,
                R.id.list_item_activity_textview,
                new ArrayList<String>());

        View rootView = inflater.inflate(R.layout.fragment_days_list, container, false);
        //A partir du rootView on récupère la listView à alimenter
        ListView listView = (ListView) rootView.findViewById(R.id.listview_days_list_activities);
        //on lie avec l'adapter mActivitiesAdapter
        listView.setAdapter(mActivitiesAdapter);
        //on se grille un petit Toast
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String forecast = mActivitiesAdapter.getItem(position);
                //Intent intent = new Intent(getActivity(), DetailActivity.class).putExtra(Intent.EXTRA_TEXT, forecast);
                //startActivity(intent);
            }
        });

        return rootView;
    }

    /**
     * Connexion a googe fitness API
     */
    private void buildFitnessClient() {
        mClient = new GoogleApiClient.Builder(getActivity())
                .addApi(Fitness.HISTORY_API)
                .addScope(new Scope(Scopes.FITNESS_ACTIVITY_READ))
                .addConnectionCallbacks(
                        new GoogleApiClient.ConnectionCallbacks() {

                            @Override
                            public void onConnected(Bundle bundle) {
                                Log.i(LOG_TAG, "Connected!!!");
                                // Now you can make calls to the Fitness APIs.
                                // Put application specific code here.
                            }

                            @Override
                            public void onConnectionSuspended(int i) {
                                // If your connection to the sensor gets lost at some point,
                                // you'll be able to determine the reason and react to it here.
                                if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_NETWORK_LOST) {
                                    Log.i(LOG_TAG, "Connection lost.  Cause: Network Lost.");
                                } else if (i == GoogleApiClient.ConnectionCallbacks.CAUSE_SERVICE_DISCONNECTED) {
                                    Log.i(LOG_TAG, "Connection lost.  Reason: Service Disconnected");
                                }
                            }
                        }
                )
                .addOnConnectionFailedListener(
                        new GoogleApiClient.OnConnectionFailedListener() {
                            // Called whenever the API client fails to connect.
                            @Override
                            public void onConnectionFailed(ConnectionResult result) {
                                Log.i(LOG_TAG, "Connection failed. Cause: " + result.toString());
                                if (!result.hasResolution()) {
                                    // Show the localized error dialog
                                    GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(),
                                            getActivity(), 0).show();
                                    return;
                                }
                                // The failure has a resolution. Resolve it.
                                // Called typically when the app is not yet authorized, and an
                                // authorization dialog is displayed to the user.
                                if (!authInProgress) {
                                    try {
                                        Log.i(LOG_TAG, "Attempting to resolve failed connection");
                                        authInProgress = true;
                                        result.startResolutionForResult(getActivity(),
                                                REQUEST_OAUTH);
                                    } catch (IntentSender.SendIntentException e) {
                                        Log.e(LOG_TAG,
                                                "Exception while starting resolution activity", e);
                                    }
                                }
                            }
                        }
                )
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Connect to the Fitness API
        Log.i(LOG_TAG, "Connecting...");
        mClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mClient.isConnected()) {
            mClient.disconnect();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_OAUTH) {
            authInProgress = false;
            if (resultCode == getActivity().RESULT_OK) {
                // Make sure the app is not already connected or attempting to connect
                if (!mClient.isConnecting() && !mClient.isConnected()) {
                    mClient.connect();
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(AUTH_PENDING, authInProgress);
    }

    public class FetchHistoryTask extends AsyncTask<String, Void, String[]> {

        private final String LOG_TAG = FetchHistoryTask.class.getSimpleName();
        private final String DATE_FORMAT = "dd/MM/yyyy";

        @Override
        protected String[] doInBackground(String... params) {
            Calendar cal = Calendar.getInstance();
            Date now = new Date();
            cal.setTime(now);
            long endTime = cal.getTimeInMillis();
            cal.add(Calendar.WEEK_OF_YEAR, -1);
            long startTime = cal.getTimeInMillis();

            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
            Log.i(LOG_TAG, "Range Start: " + dateFormat.format(startTime));
            Log.i(LOG_TAG, "Range End: " + dateFormat.format(endTime));

            DataReadRequest readRequest = new DataReadRequest.Builder()
                    .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                    .bucketByTime(1, TimeUnit.MILLISECONDS)
                    .build();

            DataReadResult dataReadResult = Fitness.HistoryApi.readData(mClient, readRequest).await(1, TimeUnit.MINUTES);
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                this.dumpDataSet(dataSet);
            }
            this.fetchDataReadResult(dataReadResult);
            return null;
        }

        private void fetchDataReadResult(DataReadResult dataReadResult) {
            for (Bucket bucket : dataReadResult.getBuckets()) {
                Log.d(LOG_TAG, "Bucket start time: " + bucket.getStartTime(TimeUnit.SECONDS));
                List<DataSet> dataSets = bucket.getDataSets();
                int totalStepsValue = 0;

                for (DataSet dataSet : dataSets) {
                    for (DataPoint dp : dataSet.getDataPoints()) {
                        for (Field field : dp.getDataType().getFields()) {
                            if (field.getName().equals("steps")) {
                                totalStepsValue += dp.getValue(field).asInt();
                            }
                        }
                    }
                }

                Log.d(LOG_TAG, "totalStepsValue: " + totalStepsValue);
            }
        }

        private void dumpDataSet(DataSet dataSet) {
            Log.i(LOG_TAG, "Data returned for Data type: " + dataSet.getDataType().getName());
            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);

            for (DataPoint dp : dataSet.getDataPoints()) {
                Log.i(LOG_TAG, "Data point:");
                Log.i(LOG_TAG, "\tType: " + dp.getDataType().getName());
                Log.i(LOG_TAG, "\tStart: " + dateFormat.format(dp.getStartTime(TimeUnit.MILLISECONDS)));
                Log.i(LOG_TAG, "\tEnd: " + dateFormat.format(dp.getEndTime(TimeUnit.MILLISECONDS)));
                for(Field field : dp.getDataType().getFields()) {
                    Log.i(LOG_TAG, "\tField: " + field.getName() +
                            " Value: " + dp.getValue(field));
                }
            }
        }
    }
}
