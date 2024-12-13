package com.app.rewardcycle.Activities;


import static com.app.rewardcycle.Utils.Constants.AUTHORISATION;
import static com.app.rewardcycle.Utils.Constants.BEARER;
import static com.app.rewardcycle.Utils.Constants.CHECK_APP_VERSION_URL;
import static com.app.rewardcycle.Utils.Constants.CONTENT_TYPE;
import static com.app.rewardcycle.Utils.Constants.CONTENT_TYPE_VALUE;
import static com.app.rewardcycle.Utils.Constants.UPDATE_APP_URL;
import static com.app.rewardcycle.Utils.Constants.USER_API_URL;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_INDEFINITE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.app.rewardcycle.R;
import com.app.rewardcycle.Utils.ControlRoom;
import com.app.rewardcycle.Utils.DownloadService;
import com.app.rewardcycle.databinding.ActivitySplashScreenBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.FirebaseApp;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


@SuppressLint("CustomSplashScreen")
public class SplashScreenActivity extends AppCompatActivity {
    private static final String TAG = "SplashScreenActivity";
    private static final int INSTALL_REQUEST_CODE = 2;
    ActivitySplashScreenBinding binding;
    String accessToken;
    private String unityGameID = "5369215";
    ActivityResultLauncher<IntentSenderRequest> activityResultLauncher;
    public static final int IMMEDIATE_REQUEST_CODE = 106;
    BroadcastReceiver downloadReceiver;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 123;
    Intent serviceIntent;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashScreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(getResources().getColor(android.R.color.transparent, getTheme()));
        FirebaseApp.initializeApp(this);



        binding.refreshActivity.setVisibility(View.GONE);
        binding.splashProgress.setVisibility(View.GONE);
        binding.logo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.zoom_in_anim));
        binding.appName.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_down_anim));

        createNotificationChannel();
        serviceIntent = new Intent(SplashScreenActivity.this, DownloadService.class);


        accessToken = ControlRoom.getInstance().getAccessToken(SplashScreenActivity.this);

        Log.d(TAG, "onCreate: Bearer Token: "+ accessToken);
//        showAppUpdateDialog();

        try {
            JSONObject appVerObj = new JSONObject();
            appVerObj.put("apk_version", getApkVersionCode());
            checkApkVersion(appVerObj);


        } catch (PackageManager.NameNotFoundException | JSONException e) {
            throw new RuntimeException(e);
        }
//        fetchUserData(accessToken);


        binding.refreshActivity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                binding.splashProgress.setVisibility(View.VISIBLE);
                try {
                    JSONObject appVerObj = new JSONObject();
                    appVerObj.put("apk_version", getApkVersionCode());
                    checkApkVersion(appVerObj);

                } catch (PackageManager.NameNotFoundException | JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        binding.cancelDownload.setOnClickListener(v -> {
            DownloadService.call.cancel();
            stopService(serviceIntent);
            binding.cancelDownload.setVisibility(View.GONE);
            binding.progressBar5.setProgress(0);
        });

        registerBroadCastReceiver();


    }

    private void registerBroadCastReceiver() {
        // Initialize and register the broadcast receiver
        binding.progressBar5.setIndeterminate(false);
         downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("DOWNLOAD_PROGRESS")) {
                    int progress = intent.getIntExtra("progress", 0);
                    binding.progressBar5.setProgress(progress); // Update the progress bar
                    binding.textView28.setText("Downloading: "+progress+"%");
                    
                   
                } else if (intent.getAction().equals("DOWNLOAD_COMPLETED")) {
                    Log.d(TAG, "onReceive: Download completed broadcast");
                    long apkSize = intent.getLongExtra("apkSize",0);
                    String apkPath = intent.getStringExtra("apkPath");

                    if (SplashScreenActivity.this != null) {
                        ControlRoom.getInstance().setApkSize(apkSize, SplashScreenActivity.this);// context might Null
                        ControlRoom.getInstance().setApkPath(apkPath, SplashScreenActivity.this);
                    }

                    File apkFile = new File(apkPath);
                    if (apkFile.exists() && apkFile.length() == apkSize) {
//                                        install ui
                        Log.d(TAG, "onReceive: apk exists with correct file size");
                        checkInstallPermission(apkFile);
                    }else {
                        Toast.makeText(context, "File is corrupted!, deleting", Toast.LENGTH_SHORT).show();
//                        Delete the previous apk and Re-download apk
                        boolean isDeleted = new File(getExternalFilesDir(null), "update.apk").delete();
                        if (isDeleted) 
                            showAppUpdateDialog();
                        else Toast.makeText(context, "Something went wrong!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };

        // Register the receiver
        IntentFilter downloadIntentFilter = new IntentFilter();
        downloadIntentFilter.addAction("DOWNLOAD_PROGRESS");
        downloadIntentFilter.addAction("DOWNLOAD_COMPLETED");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, downloadIntentFilter, Context.RECEIVER_NOT_EXPORTED);
        }else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                registerReceiver(downloadReceiver, downloadIntentFilter, Context.RECEIVER_NOT_EXPORTED);
            }

        }
    }

    private String getApkVersionCode() throws PackageManager.NameNotFoundException {
        return  getPackageManager().getPackageInfo(getPackageName(), 0).versionCode +"";
    }
    private String getApkVersion() throws PackageManager.NameNotFoundException {
        return  getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    }


    private void checkApkVersion(JSONObject appVersion) {

        if (!ControlRoom.isNetworkConnected(SplashScreenActivity.this)) {
            Toast.makeText(SplashScreenActivity.this, "Check Internet Connection!", Toast.LENGTH_SHORT).show();
            binding.refreshActivity.setVisibility(View.VISIBLE);
            binding.splashProgress.setVisibility(View.GONE);
        } else {
            binding.splashProgress.setVisibility(View.VISIBLE);
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, CHECK_APP_VERSION_URL,
                    appVersion,
                    response -> {
                        try {
                            if (response.getInt("code") == 200) {
                                boolean isOlderVersion = response.getBoolean("status");
                                Log.d("checkApkVersion", "onResponse: Sucessfull...:" + isOlderVersion);
                                binding.splashProgress.setVisibility(View.GONE);
                                if (isOlderVersion) {
                                    Log.d(TAG, "checkApkVersion: isolderversion tr");
                                    File apkFile = new File(getExternalFilesDir(null), "update.apk");
                                    long apkSize = ControlRoom.getInstance().getApkSize(SplashScreenActivity.this);

                                    if (apkFile.exists() && apkFile.length() == apkSize) {
//                                        install ui
                                        checkInstallPermission(apkFile);
                                    } else {
                                        showAppUpdateDialog();
                                    }
                                } else {
                                    fetchUserData(ControlRoom.getInstance().getAccessToken(SplashScreenActivity.this));
                                }


                            } else {
                                Log.d("checkApkVersion", "onResponse: Failed..." + response.getString("data"));

                            }
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }

                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.d("checkApkVersion", "onResponse: error Response: " + error.getMessage());
                    binding.splashProgress.setVisibility(View.GONE);

                }
            }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    HashMap<String, String> header = new HashMap<>();
                    header.put(CONTENT_TYPE, CONTENT_TYPE_VALUE);
                    header.put(AUTHORISATION, BEARER + SplashScreenActivity.this.accessToken);
                    return header;
                }
            };
            Volley.newRequestQueue(this).add(jsonObjectRequest);
        }


    }

    private void showUserBlockedDialog() {


        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(SplashScreenActivity.this);
        builder.setTitle("You are Blocked!")
                .setIcon(R.drawable.app_icon)
                .setMessage("Your account has been blocked, please contact on merewardns@gmail.com")
                .setPositiveButton("Contact Us", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent mailIntent = new Intent(Intent.ACTION_SENDTO);
                        mailIntent.setData(Uri.parse("mailto:"));
                        mailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"merewardns@gmail.com"});
                        startActivity(mailIntent);


                    }
                })
                .setNegativeButton("Close App", (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .show();
    }

    private void showAppUpdateDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(SplashScreenActivity.this);
        builder.setTitle("Update Available!")
                .setIcon(R.drawable.app_icon)
                .setMessage("We’ve made some improvements to enhance your experience! This update may includes:\n" +
                        "\n" +
                        "Better Performance\n" +
                        "New Features\n" +
                        "Bug Fixes\n\n" +
                        "Update now to get the best version of the app!")
                .setPositiveButton("Update Now", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        checkPostNotificationPermission();


                    }
                })
                .setNegativeButton("Close App", (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                }).show();
    }

    private void checkPostNotificationPermission() {



            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    // Request the permission
                    ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 
                            NOTIFICATION_PERMISSION_REQUEST_CODE);
                } else {
                    // Permission is already granted,
                    //                        start foreground service for download
                    startDownload();
                }
            } else {
                // For versions below Android 13, just show the notification
                stopService(new Intent(this, DownloadService.class));

                Intent intent = new Intent(SplashScreenActivity.this, DownloadService.class);
                intent.putExtra("apkUrl", UPDATE_APP_URL);  // Pass the APK URL
                startService(intent);
                binding.downloaderLayout.setVisibility(View.VISIBLE);
                binding.progressBar5.setIndeterminate(false);
            }


    }

    private void startDownload() {

        serviceIntent.putExtra("apkUrl", UPDATE_APP_URL);  // Pass the APK URL
        binding.downloaderLayout.setVisibility(View.VISIBLE);
        binding.progressBar5.setIndeterminate(false);
        startService(serviceIntent);

    }

    private void fetchUserData(String accessToken) {
        if (!ControlRoom.isNetworkConnected(SplashScreenActivity.this)) {
            Toast.makeText(SplashScreenActivity.this, "Check Internet Connection", Toast.LENGTH_SHORT).show();
            binding.refreshActivity.setVisibility(View.VISIBLE);
            binding.splashProgress.setVisibility(View.GONE);
        } else {
            binding.refreshActivity.setVisibility(View.GONE);
            binding.splashProgress.setVisibility(View.VISIBLE);
            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, USER_API_URL, null,
                    response -> {
                        try {
                            if (response.getBoolean("status") && response.getInt("code") == 200) {
//                                Log.d("fetchUserDetails", "onResponse: Sucessfull...:" + response.getString("data"));
                                // User is logged in go to MainActivity.
                                JSONObject userJsonObject = response.getJSONObject("data");
                                ControlRoom.getInstance().setUserData(userJsonObject, SplashScreenActivity.this);

                                // finish page
                                startActivity(new Intent(SplashScreenActivity.this, MainActivity.class));
                                finish();
                                binding.splashProgress.setVisibility(View.GONE);

                            } else if (!response.getBoolean("status") && response.getInt("code") == 201) {
//                                Log.d("checkUserData", "onResponse: data: " + response.getString("data"));

//                                User is not logged in go to Login Activity.
                                startActivity(new Intent(SplashScreenActivity.this, LoginActivity.class));
                                finish();

                            } else if (!response.getBoolean("status") && response.getInt("code") == 202) {
//                                User is blocked by admin.

                                showUserBlockedDialog();

                            } else {
                                Log.d("fetchUserDetails", "onResponse: Failed..." + response.getString("data"));
                                new LoginActivity().showErrorDialog(response.getString("data"));

                                binding.splashProgress.setVisibility(View.GONE);
                                Snackbar snackbar = Snackbar.make(findViewById(R.id.splashScreenRoot), "Something went wrong.", LENGTH_INDEFINITE)
                                        .setAction("Retry", new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                fetchUserData(accessToken);
                                            }
                                        });
                                snackbar.show();

                            }
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }

                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.d("fetchUserDetails", "onResponse: error Response: " + error.getMessage());
                    binding.splashProgress.setVisibility(View.GONE);
                    Snackbar snackbar = Snackbar.make(findViewById(R.id.splashScreenRoot), "Something went wrong.", LENGTH_INDEFINITE)
                            .setAction("Retry", new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    fetchUserData(accessToken);
                                }
                            });
                    snackbar.show();
                }
            }) {
                @Override
                public Map<String, String> getHeaders() throws AuthFailureError {
                    HashMap<String, String> header = new HashMap<>();
                    header.put(CONTENT_TYPE, CONTENT_TYPE_VALUE);
                    header.put(AUTHORISATION, BEARER + SplashScreenActivity.this.accessToken);
                    return header;
                }
            };
            Volley.newRequestQueue(this).add(jsonObjectRequest);
        }

    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "download_channel",
                    "Download Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (downloadReceiver != null)
                unregisterReceiver(downloadReceiver);

        }catch (Exception e){
            Log.d(TAG, "onDestroy: unregister Receiver exception: "+ e.getMessage());
        }
    }

    private void checkInstallPermission(File apkFile) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            if (!getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + this.getPackageName()));
                startActivityForResult(intent, INSTALL_REQUEST_CODE);
//                startCheckingAllowUnknownSources( apkFile);
            } else {
                installAPK(apkFile);
//                startCheckingAllowUnknownSources( apkFile);
            }
        } else {
            installAPK(apkFile);
//            startCheckingAllowUnknownSources( apkFile);
        }

    }



    private void installAPK(File apkFile) {

        if (apkFile.exists() && apkFile.length() == ControlRoom.getInstance().getApkSize(this)) {
            Log.d(TAG, "installAPK: app exists");

            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = FileProvider.getUriForFile(this, this.getPackageName() + ".provider", apkFile);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            this.startActivity(intent);

            /*new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (apkFile.exists()){
                    boolean deleted = apkFile.delete();
                    Log.d(TAG, "run: Update File Deleted!" + deleted);
                }
            }, 60 * 1000);*/

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE){
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDownload();
            }else if (!(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS))) {

                MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(this)
                        .setTitle("Permission Required!")
                        .setMessage("Please allow notification permission from app settings for downloading purposes. ")
                        .setCancelable(false)
                        .setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);

                                dialog.dismiss();
                            }
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
                materialAlertDialogBuilder.show();
            } else
                checkPostNotificationPermission();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2 && resultCode == RESULT_OK){
//            Log.d(TAG, "onActivityResult: permission accepted!");

            String filePath = ControlRoom.getInstance().getApkPath(this);
            long fileLength = ControlRoom.getInstance().getApkSize(this);
            File apkFile = new File(filePath);
            if (apkFile.exists() && fileLength == apkFile.length()){
//                install apk
                installAPK(apkFile);
            }else {
                Toast.makeText(this, "App Not Available", Toast.LENGTH_SHORT).show();
            }
        }else {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
        }
    }
}

