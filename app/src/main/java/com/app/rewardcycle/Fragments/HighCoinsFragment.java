package com.app.rewardcycle.Fragments;

import static android.content.Context.LOCATION_SERVICE;
import static com.app.rewardcycle.Utils.Constants.AUTHORISATION;
import static com.app.rewardcycle.Utils.Constants.CONTENT_TYPE;
import static com.app.rewardcycle.Utils.Constants.CONTENT_TYPE_VALUE;
import static com.app.rewardcycle.Utils.Constants.OFFER18_OFFERS_API;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.app.rewardcycle.Activities.MainActivity;
import com.app.rewardcycle.Adapters.Offer18MainListAdapter;
import com.app.rewardcycle.Modals.Offer18MainListModal;
import com.app.rewardcycle.Utils.ControlRoom;
import com.app.rewardcycle.Utils.GPSReceiver;
import com.app.rewardcycle.databinding.FragmentHighCoinsBinding;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnTokenCanceledListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

public class HighCoinsFragment extends Fragment {

    FragmentHighCoinsBinding binding;
    ArrayList<Offer18MainListModal> offer18MainList;
    public Offer18MainListAdapter offer18MainListAdapter;
    FusedLocationProviderClient fusedLocationProviderClient;
    private static final String TAG = "HighCoinsFragment";
    private static final int LOCATION_REQUEST_CODE = 1234;
    public final int REQUEST_CHECK_SETTINGS = 102;
    String locationState, locationCity, locationCountry;
    GPSReceiver gpsReceiver;
    //    BroadcastReceiver gpsReceiver;
    boolean gpsStatus = true;
    Location gps = null;
    Location network = null;
    int updateLimit = 0;
    LocationRequest locationRequest;
    //        location callback define
    LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            super.onLocationResult(locationResult);

            if (locationResult != null){
                if (updateLimit >= 2) {
                    stopLocationUpdates();
                }else {
                    for (Location location : locationResult.getLocations()) {
                        Log.d(TAG, "onLocationResult: " + location.getLatitude() + "size " + locationResult.getLocations().size());
                        try {
                            addressFromLocation(location);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    Toast.makeText(requireActivity(), "Location Fetched", Toast.LENGTH_SHORT).show();
                    updateLimit += 1;
                }

            }
        }
    };


    public HighCoinsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        gpsReceiver = new GPSReceiver(requireContext());
//        gpsReceiver.registerReceiver();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for requireContext() fragment
        binding = FragmentHighCoinsBinding.inflate(inflater, container, false);


        ((MainActivity) requireActivity()).adapterListener(this);

//        if (!isLocationEnabled()) {
//            binding.gpsTxt.setVisibility(View.VISIBLE);
//        }

        binding.highCoinsRefresher.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                readyForFetchingCampaigns();
            }
        });


//        Fused Location Initialise
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext());

//        location request initialise
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build();


        //      fulfill all conditions to get user location
        readyForFetchingCampaigns();


        return binding.getRoot();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopLocationUpdates();
    }

    private void readyForFetchingCampaigns() {
        //            check runtime location permission
        if (ContextCompat.checkSelfPermission(requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            // You can directly ask for the permission.
            locationPermissionRuntime();

        } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle("Location Required!")
                    .setMessage("Please allow location permission to see High Coins")
                    .setPositiveButton("Allow", (dialog, which) -> locationPermissionRuntime())
                    .setNegativeButton("Don't Allow", (dialog, which) -> dialog.dismiss());
            builder.create().show();
        } else {
            // now check location/gps settings is enable
            createLocationRequest();

        }
    }

    void createLocationRequest() {

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(requireActivity());
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(requireActivity(), new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                // All location settings are satisfied,initialize location requests here.

//                startLocationUpdates();
                getUserLastLocation();
            }
        });

        task.addOnFailureListener(requireActivity(), new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        ResolvableApiException resolvable = (ResolvableApiException) e;
                        resolvable.startResolutionForResult(requireActivity(),
                                REQUEST_CHECK_SETTINGS);
                    } catch (IntentSender.SendIntentException sendEx) {
                        // Ignore the error.
                    }
                }
            }
        });
    }

    private void startLocationUpdates() {

        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionRuntime();
        } else {

            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void stopLocationUpdates(){
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

//    private void getUserLocation2() {
//        Location currentLocation = null;
//        LocationManager locationManager = (LocationManager) requireContext().getSystemService(LOCATION_SERVICE);
//
//        boolean hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
////------------------------------------------------------//
//        boolean hasNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
//
//
//        LocationListener gpsLocationListener = new LocationListener() {
//            @Override
//            public void onLocationChanged(@NonNull Location location) {
//                gps = location;
//            }
//        };
//
//        LocationListener netLocationListener = new LocationListener() {
//            @Override
//            public void onLocationChanged(@NonNull Location location) {
//                network = location;
//            }
//        };
//
//        if (hasGps) {
//            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//
//                locationPermissionRuntime();
//                return;
//            }
//            locationManager.requestLocationUpdates(
//                    LocationManager.GPS_PROVIDER,
//                    5000,
//                    0F,
//                    gpsLocationListener
//            );
//        }
////------------------------------------------------------//
//        if (hasNetwork) {
//            locationManager.requestLocationUpdates(
//                    LocationManager.NETWORK_PROVIDER,
//                    5000,
//                    0F,
//                    netLocationListener
//            );
//        }
//
//
//        Location lastLocByGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
//        if (lastLocByGps != null)
//            gps = lastLocByGps;
//        Location lastLocByNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
//        if (lastLocByNetwork != null)
//            network = lastLocByNetwork;
//
//        double latitude,longitude;
//
//        if (gps != null && network != null) {
//            if (gps.getAccuracy() > network.getAccuracy()) {
//                currentLocation = gps;
//                latitude = currentLocation.getLatitude();
//                longitude = currentLocation.getLongitude();
//                // use latitude and longitude as per your need
//                Log.d(TAG, "getUserLocation2: LatLng GPS: "+ latitude+" / "+longitude);
//                Toast.makeText(requireContext(), "getUserLocation2: LatLng GPS: "+ latitude+" / "+longitude, Toast.LENGTH_SHORT).show();
//            } else {
//                currentLocation = network;
//                latitude = currentLocation.getLatitude();
//                longitude = currentLocation.getLongitude();
//                // use latitude and longitude as per your need
//                Log.d(TAG, "getUserLocation2: LatLng Network: "+ latitude+" / "+longitude);
//                Toast.makeText(requireContext(), "getUserLocation2: LatLng Network: "+ latitude+" / "+longitude, Toast.LENGTH_SHORT).show();
//
//            }
//        }else {
//            Log.d(TAG, "getUserLocation2: LatLng Null Both");
//            Toast.makeText(requireContext(), "getUserLocation2: LatLng Null Both", Toast.LENGTH_SHORT).show();
//
//        }
//    }


    private void addressFromLocation(Location location) throws IOException {

        Geocoder geocoder = new Geocoder(requireActivity(), Locale.getDefault());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1, new Geocoder.GeocodeListener() {
                @Override
                public void onGeocode(@NonNull List<Address> addresses) {
                    locationCountry = addresses.get(0).getCountryCode();
                    locationState = addresses.get(0).getAdminArea();
                    locationCity = addresses.get(0).getLocality();


                    fetchAllCampaigns();
                }
            });
        } else {
//            NOT TIRAMISU
            List<Address> addressList = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addressList != null) {
                locationCountry = addressList.get(0).getCountryCode();
                locationState = addressList.get(0).getAdminArea();
                locationCity = addressList.get(0).getLocality();
                fetchAllCampaigns();
                Log.d(TAG, "onGeocode: country, state, city: " + locationCountry + locationState + locationCity);
            } else {
                Toast.makeText(requireActivity(), "Something went wrong!, can't find location.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void locationPermissionRuntime() {
        ActivityCompat.requestPermissions(requireActivity(), new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,},
                LOCATION_REQUEST_CODE);
    }


    private void getUserLastLocation() {

        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(requireActivity(),
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//          location permission is not granted
            locationPermissionRuntime();
        } else {

            fusedLocationProviderClient.getLastLocation()
                    .addOnSuccessListener(requireActivity(), location -> {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {

                            // Logic to handle location object
                            Log.d(TAG, "getUserLocation: location fetched: " + location.getLatitude());
                            try {
                                addressFromLocation(location);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

                        } else {
//                            location is null, try request location
                            Log.d(TAG, "getUserLocation: last location is null " );
                            startLocationUpdates();

                        }
                    });

        }

    }


    public void fetchAllCampaigns() {

        offer18MainList = new ArrayList<>();
        binding.emptyCampaignTxt.setVisibility(View.GONE);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, OFFER18_OFFERS_API,
                null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.d("locSer", "onChanged: fetchAllCampaigns under response: " + response);
                binding.highCoinsRefresher.setRefreshing(false);
                try {
                    if (response.getInt("response_code") == 200) {


//                        Log.d(TAG, "onResponse: ");

                        JSONArray campaigns = response.getJSONArray("data");

                        for (int i = 0; i < campaigns.length(); i++) {
                            int totalCoins = 0;
                            JSONObject campaignData = campaigns.getJSONObject(i);
                            String campaignId = campaignData.getString("offerid");


                            String campaignName = campaignData.getString("name");
                            String campaignShortDesc = campaignData.getString("short_description");
                            String campaignPrice = campaignData.getString("price");
                            String campaignIcon = campaignData.getString("logo");
                            String campaignClickUrl = campaignData.getString("click_url");
                            String campaignCountryCode = campaignData.getString("country_allow");


                            String campaignPosterImg = campaignData.getJSONArray("creatives")
                                    .getJSONObject(0)
                                    .getString("url");


                            Log.d(TAG, "onResponse: locationCountry matches campaign: " + campaignCountryCode);

                            if (campaignCountryCode.equals(locationCountry)) {
//                                user country matches campaign country
                                Offer18MainListModal offer18MainModal = new Offer18MainListModal(
                                        campaignPosterImg, campaignIcon, campaignName, campaignShortDesc,
                                        campaignPrice, ""
                                );
                                offer18MainModal.setAdId(campaignId);
                                offer18MainModal.setClickUrl(campaignClickUrl);

                                offer18MainList.add(offer18MainModal);
                            }


                                /*JSONArray goals = campaignData.getJSONArray("goals");

                                for (int j= 0; j< goals.length(); j++){
                                    JSONObject singleGoal = goals.getJSONObject(j);

                                    int payout = singleGoal.getJSONArray("payouts").getJSONObject(0).getInt("payout");

                                    totalCoins = totalCoins + payout;

                                }*/


                        }

                        if (offer18MainList.isEmpty()) {
                            binding.trakierMainListRv.setVisibility(View.GONE);
                            binding.emptyCampaignTxt.setVisibility(View.VISIBLE);
                            binding.emptyCampaignTxt.setText("No campaigns available at your location!");

                        } else {
                            binding.trakierMainListRv.setVisibility(View.VISIBLE);
                            binding.emptyCampaignTxt.setVisibility(View.GONE);
                            offer18MainListAdapter = new Offer18MainListAdapter(offer18MainList, requireContext());
                            binding.trakierMainListRv.setLayoutManager(new LinearLayoutManager(requireContext()));
                            binding.trakierMainListRv.setAdapter(offer18MainListAdapter);
                        }


                    } else {
                        Log.d("fetchAllCampaigns", "onResponse : Something went wrong: "
                                + response.getString("data"));
                    }
                } catch (JSONException e) {
                    Log.d("fetchAllCampaigns", "onResponse Failed : Json Exception: " + e.getMessage());
                }


            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("fetchAllCampaigns", "onResponse Failed : VolleyError: " + error);
                binding.highCoinsRefresher.setRefreshing(false);
                binding.emptyCampaignTxt.setVisibility(View.VISIBLE);
                binding.emptyCampaignTxt.setText("Internal server error!");
            }
        }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> header = new HashMap<>();
                header.put(CONTENT_TYPE, CONTENT_TYPE_VALUE);
                header.put(AUTHORISATION, "Bearer " + ControlRoom.getInstance().getAccessToken(requireContext()));
                return header;
            }
        };

        Volley.newRequestQueue(requireContext()).add(jsonObjectRequest);
    }

    public boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) requireContext().getSystemService(LOCATION_SERVICE);

        boolean locationEnabled;
        locationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        return locationEnabled;
    }

    public void enableLocationMsg(String msg) {
        binding.emptyCampaignTxt.setText(msg);
        binding.emptyCampaignTxt.setVisibility(View.VISIBLE);
    }

    public void getLastLocation() {
        Log.d("locSer", "onChanged: getLastLocation  High Coin");
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.getLastLocation().addOnCompleteListener(new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {
                    task.addOnSuccessListener(new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            Log.d(TAG, "onSuccess: location task");
                            if (location != null) {
                                binding.emptyCampaignTxt.setVisibility(View.GONE);
                                binding.highCoinsRefresher.setRefreshing(false);

                                Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1, addresses -> {
                                            ((MainActivity) requireActivity()).LOCATION_CITY = addresses.get(0).getLocality();
                                            ((MainActivity) requireActivity()).LOCATION_COUNTRY = addresses.get(0).getCountryCode();

                                            fetchAllCampaigns();
                                            Log.d("locSer", "onChanged: getLastLocation under fetch ");

                                            Log.d(TAG, "getLastLocation: \n Latitude: " + addresses.get(0).getLatitude() +
                                                    "\n Longitude: " + addresses.get(0).getLongitude() +
                                                    "\n Address: " + addresses.get(0).getAddressLine(0) +
                                                    "\n Locality: " + addresses.get(0).getLocality() +
                                                    "\n Area: " + addresses.get(0).getAdminArea() +
                                                    "\n Country: " + addresses.get(0).getCountryName());

                                        });
                                    } else {
                                        List<Address> addressList = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                                        if (addressList != null) {
                                            ((MainActivity) requireActivity()).LOCATION_CITY = addressList.get(0).getLocality();
                                            ((MainActivity) requireActivity()).LOCATION_COUNTRY = addressList.get(0).getCountryCode();
                                            fetchAllCampaigns();


                                            Log.d(TAG, "getLastLocation:(NOT tiramisu) \n Latitude: " + addressList.get(0).getCountryCode());
                                        } else {
                                            Toast.makeText(requireContext(), "AddressList NULL", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                } catch (IOException e) {
                                    Toast.makeText(requireContext(), "Something went wrong! " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    throw new RuntimeException(e);
                                }
                            } else {
                                Toast.makeText(requireContext(), "Can't find location, Please restart the app.", Toast.LENGTH_SHORT).show();
                                enableLocationMsg("Can't find location!");
                                binding.highCoinsRefresher.setRefreshing(false);
                            }
                        }
                    });

                    task.addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d(TAG, "onFailure: location task");
                        }
                    });

                }
            });


        } else {
            Toast.makeText(requireContext(), "Location Permission is required!", Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CHECK_SETTINGS) {

            if (resultCode == Activity.RESULT_OK) {

                startLocationUpdates();
            } else {

                Toast.makeText(requireContext(), "User Denied to enable location", Toast.LENGTH_SHORT).show();
                // User declined to change location settings

            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_REQUEST_CODE) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "onRequestPermissionsResult: ");
//                showLocationBtn.setVisibility(View.GONE);
                createLocationRequest();

            } else {

                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity())
                        .setTitle("Location Required!")
                        .setMessage("Please allow location permission to see High Coins")
                        .setPositiveButton("Allow", (dialog, which) -> locationPermissionRuntime())
                        .setNegativeButton("Don't Allow", (dialog, which) -> dialog.dismiss());
                builder.create().show();
            }

        }
    }
}