package com.example.satan.androidapp4;

import android.Manifest;
import android.app.NotificationManager;
import android.app.Service;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Button;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONObject;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class ForegroundService extends Service {
    private LocationRequest mLocationRequest;
    private long UPDATE_INTERVAL = 1000 * 1000;  /* 10 secs */
    private long FASTEST_INTERVAL = 200* 1000; /* 2 sec */

    private Timer mTimer = null;    //timer handling
    private static final String LOG_TAG = "ForegroundService";


    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(Constants.ACTION.STARTFOREGROUND_ACTION)) {

            mLocationRequest = new LocationRequest();
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            mLocationRequest.setInterval(UPDATE_INTERVAL);
            mLocationRequest.setFastestInterval(FASTEST_INTERVAL);

            LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
            builder.addLocationRequest(mLocationRequest);
            LocationSettingsRequest locationSettingsRequest = builder.build();

            SettingsClient settingsClient = LocationServices.getSettingsClient(this);
            settingsClient.checkLocationSettings(locationSettingsRequest);

            // new Google API SDK v11 uses getFusedLocationProviderClient(this)
            FusedLocationProviderClient mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            if (ActivityCompat.checkSelfPermission(this ,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                mFusedLocationClient.requestLocationUpdates(mLocationRequest, new LocationCallback() {
                            @Override
                            public void onLocationResult(LocationResult locationResult) {
                                onLocationChanged(locationResult.getLastLocation());
                            }
                        },
                        Looper.myLooper());
            }


            Log.i(LOG_TAG, "Received Start Foreground Intent ");

            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.setAction(Constants.ACTION.MAIN_ACTION);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);

            // And now, building and attaching the Skip button.
            Intent buttonSkipIntent = new Intent(this, NotificationSkipButtonHandler.class);
            buttonSkipIntent.putExtra("action", "skip");
            PendingIntent buttonSkipPendingIntent = pendingIntent.getBroadcast(this, 0, buttonSkipIntent, 0);
            //Build notification
            Notification notification = CreateNotiView(this, buttonSkipPendingIntent,"Never").build();
            startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE,  notification);


        }
        else if (intent.getAction().equals(Constants.ACTION.STOPFOREGROUND_ACTION)) {
            Toast.makeText(this,"Stop Service",Toast.LENGTH_SHORT).show();
            Log.i(LOG_TAG, "Received Stop Foreground Intent");
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(LOG_TAG, "In onDestroy");
    }

    public void onLocationChanged(Location location) {
        SendDataToServer(location);
        UpdateTime(ForegroundService.this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Used only in case of bound services.
        return null;
    }

    /**
     * Called when user clicks the "skip" button on the on-going system Notification.
     */
    public static class NotificationSkipButtonHandler extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Toast.makeText(context,"Next Clicked",Toast.LENGTH_SHORT).show();
            ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(connectivity != null)
            {
                UpdateTime(context);
            }
        }
    }


    public static void UpdateTime(Context context){
        Date time = new java.util.Date(System.currentTimeMillis());
        String UpdateText= new SimpleDateFormat("HH:mm:ss").format(time);

        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder NomBuilder = CreateNotiView(context, null,UpdateText);
        mNotificationManager.notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, NomBuilder.build());

    }

    public static void SendDataToServer(final Location location){
        new Thread() {
            @Override
            public void run() {
                try {
                    //https://requestb.in/1ej6i851?inspect
                    URL url = new URL(Constants.CONNECTION.SERVERURL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);
                    conn.connect();

                    JSONObject jsonParam = new JSONObject();
                    jsonParam.put("device", Build.MODEL);
                    jsonParam.put("latitude", location.getLatitude());
                    jsonParam.put("longitude", location.getLongitude());
                    jsonParam.put("id", "1");


                    DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                    os.writeBytes(jsonParam.toString());

                    os.flush();
                    os.close();

                    Log.i("STATUS", String.valueOf(conn.getResponseCode()));
                    Log.i("MSG", conn.getResponseMessage());

                    conn.disconnect();

                } catch(Exception e){

                }
            }
        }.start();
    }

    public static NotificationCompat.Builder CreateNotiView(Context context,PendingIntent pi ,String UpdateText){
        RemoteViews notificationView = new RemoteViews(context.getPackageName(),R.layout.notification);
        if(pi != null){
            notificationView.setOnClickPendingIntent(R.id.notification_button_skip, pi);
        }
        notificationView.setTextViewText(R.id.notification_text_artist, UpdateText);
        NotificationCompat.Builder NomBuilder = new NotificationCompat.Builder(context,"1");

        //Close Button
        Intent stopSelf = new Intent(context, ForegroundService.class);
        stopSelf.setAction(Constants.ACTION.STOPFOREGROUND_ACTION);
        PendingIntent pStopSelf = PendingIntent.getService(context, 0, stopSelf,PendingIntent.FLAG_CANCEL_CURRENT);
        notificationView.setOnClickPendingIntent(R.id.notification_button_close,pStopSelf);

        Bitmap icon = BitmapFactory.decodeResource(context.getResources(),
                R.mipmap.ic_launcher);

        NomBuilder.setContent(notificationView)
                .setSmallIcon(R.drawable.ic_stat_logo)
                .setLargeIcon(
                        Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContent(notificationView)
                .setOngoing(true);
        return NomBuilder;
    }
}