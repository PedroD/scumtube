package com.backyt;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

/**
 * Created by Andre on 27/07/2015.
 */
public class PlayerNotification extends Notification {

    private Context parent;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;
    private RemoteViews remoteView;

    public PlayerNotification(Context parent){
       /* this.parent = parent;
        notificationBuilder = new NotificationCompat.Builder(parent)
                .setContentTitle("Parking Meter")
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true);

        remoteView = new RemoteViews(parent.getPackageName(), R.layout.notification);

        //set the button listeners
        setListeners(remoteView);
        notificationBuilder.setContent(remoteView);

        notificationManager = (NotificationManager) parent.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(2, notificationBuilder.build());*/
    }


    public void setListeners(RemoteViews view){
       /* //listener 1
        Intent volume = new Intent(parent,NotificationReturnSlot.class);
        volume.putExtra("DO", "volume");
        PendingIntent btn1 = PendingIntent.getActivity(parent, 0, volume, 0);
        view.setOnClickPendingIntent(R.id.btn1, btn1);

        //listener 2
        Intent stop = new Intent(parent, NotificationReturnSlot.class);
        stop.putExtra("DO", "stop");
        PendingIntent btn2 = PendingIntent.getActivity(parent, 1, stop, 0);
        view.setOnClickPendingIntent(R.id.btn2, btn2);*/
    }

    public void notificationCancel() {
        notificationManager.cancel(2);
    }
}

