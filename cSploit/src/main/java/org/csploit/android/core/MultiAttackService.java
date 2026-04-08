package org.csploit.android.core;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import org.csploit.android.R;
import org.csploit.android.net.Network;
import org.csploit.android.net.Target;
import org.csploit.android.net.datasource.Search;
import org.csploit.android.net.metasploit.MsfExploit;
import org.csploit.android.tools.NMap;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Service for attack multiple targets in background
 */
public class MultiAttackService extends Service {

  public final static String MULTI_ACTIONS = "MultiAttackService.data.actions";
  public final static String MULTI_TARGETS = "MultiAttackService.data.targets";

  private static final int NOTIFICATION_ID = 2;
  private static final int CANCEL_CODE = 1;
  private static final int CLICK_CODE = 2;
  private static final String NOTIFICATION_CANCELLED = "org.csploit.android.core.MultiAttackService.CANCELLED";

  private static final int PENDING_INTENT_FLAGS =
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

  private final static int TRACE    = 1;
  private final static int SCAN     = 2;
  private final static int INSPECT  = 4;
  private final static int EXPLOIT  = 8;
  private final static int CRACK    = 16;

  private NotificationManager mNotificationManager;
  private NotificationCompat.Builder mBuilder;
  private BroadcastReceiver mReceiver;
  private Intent mContentIntent;
  private boolean mRunning = false;
  private int totalTargets;
  private int completedTargets;

  private HandlerThread workerThread;
  private Handler workerHandler;

  @Override
  public void onCreate() {
    super.onCreate();
    workerThread = new HandlerThread("MultiAttackService");
    workerThread.start();
    workerHandler = new Handler(workerThread.getLooper());
  }

  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, final int startId) {
    if (intent == null) {
      return START_NOT_STICKY;
    }
    final Intent workIntent = intent;
    workerHandler.post(new Runnable() {
      @Override
      public void run() {
        try {
          handleMultiAttackIntent(workIntent);
        } finally {
          stopSelf(startId);
        }
      }
    });
    return START_NOT_STICKY;
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private class SingleWorker implements Runnable {

    private int tasks;
    private Target target;
    private Child process = null;
    private Future future = null;

    public SingleWorker(final int tasks, final Target target) {
      this.tasks = tasks;
      this.target = target;
    }

    @Override
    public void run() {
      try {
        if((tasks&TRACE)!=0)
          trace();
        if((tasks&SCAN)!=0)
          scan();
        if((tasks&INSPECT)!=0)
          inspect();
        if((tasks&EXPLOIT)!=0)
          exploit();
        if((tasks&CRACK)!=0)
          crack();
        Logger.debug(target+" done");

        synchronized (MultiAttackService.this) {
          completedTargets++;
          mBuilder.setContentInfo(String.format(Locale.getDefault(), "%d/%d",
                  completedTargets, totalTargets))
                  .setChannelId(getBaseContext().getString(R.string.csploitChannelId));
          mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        }

      } catch (InterruptedException e) {
        if(future != null && !future.isDone())
          future.cancel(false);
        if(process != null && process.running)
          process.kill();
        Logger.info(e.getMessage());
      }
    }

    private void trace() {
      // not implemented yet
    }

    private void scan() throws InterruptedException {
      try {
        process = System.getTools().nmap.synScan(target, new NMap.SynScanReceiver() {
          @Override
          public void onPortFound(int port, String protocol) {
            target.addOpenPort(port, Network.Protocol.fromString(protocol));
          }
        }, null);
        process.join();
      } catch (ChildManager.ChildNotStartedException e) {
        Logger.error("cannot start nmap process");
      }
    }

    private void inspect() throws InterruptedException {
      try {
        process = System.getTools().nmap.inpsect(target, new NMap.InspectionReceiver() {
          @Override
          public void onOpenPortFound(int port, String protocol) {
            target.addOpenPort(port, Network.Protocol.fromString(protocol));
          }

          @Override
          public void onServiceFound(int port, String protocol, String service, String version) {
            Target.Port p;

            if (service != null && !service.isEmpty())
              if (version != null && !version.isEmpty())
                p = new Target.Port(port, Network.Protocol.fromString(protocol), service, version);
              else
                p = new Target.Port(port, Network.Protocol.fromString(protocol), service);
            else
              p = new Target.Port(port, Network.Protocol.fromString(protocol));
            target.addOpenPort(p);
          }

          @Override
          public void onOsFound(String os) {
            target.setDeviceOS(os);
          }

          @Override
          public void onDeviceFound(String device) {
            target.setDeviceType(device);
          }
        }, target.hasOpenPorts());

        process.join();
      } catch (ChildManager.ChildNotStartedException e) {
        Logger.error("cannot start nmap process");
      }
    }

    private void exploit() throws InterruptedException {

      future = Search.searchExploitForServices(target, new Search.Receiver<Target.Exploit>() {
        @Override
        public void onItemFound(Target.Exploit exploit) {
          target.addExploit(exploit);
        }

        @Override
        public void onFoundItemChanged(Target.Exploit exploit) {

        }

        @Override
        public void onEnd() {

        }
      });

      try {
        future.get();
      } catch (ExecutionException e) {
        System.errorLogging(e);
      }

      if(System.getMsfRpc() != null) {
        for(Target.Exploit e : target.getExploits()) {
          if(e instanceof MsfExploit) {
            ((MsfExploit)e).tryLaunch();
          }
        }
      }
    }

    private void crack() {
      // not implemented yet
    }

  }

  private void setupNotification() {
    // get notification manager
    mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    // get notification builder
    mBuilder = new NotificationCompat.Builder(this, getBaseContext().getString(R.string.csploitChannelId));
    // create a broadcast receiver to get actions
    // performed on the notification by the user
    mReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Logger.debug("received action: "+action);
        if(action==null)
          return;
        // user cancelled our notification
        if(action.equals(NOTIFICATION_CANCELLED)) {
          mRunning = false;
          stopSelf();
        }
      }
    };
    mContentIntent = null;
    // register our receiver
    registerReceiver(mReceiver,new IntentFilter(NOTIFICATION_CANCELLED));
    // set common notification actions
    mBuilder.setDeleteIntent(PendingIntent.getBroadcast(this, CANCEL_CODE, new Intent(NOTIFICATION_CANCELLED), PENDING_INTENT_FLAGS));
    mBuilder.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(), PENDING_INTENT_FLAGS));
  }

  /**
   * if mContentIntent is null delete our notification,
   * else assign it to the notification onClick
   */
  private void finishNotification() {
    try {
      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH);
    } catch (Throwable ignored) {
    }

    if (mNotificationManager == null) {
      if (mReceiver != null) {
        try {
          unregisterReceiver(mReceiver);
        } catch (Throwable ignored) {
        }
        mReceiver = null;
      }
      mBuilder = null;
      return;
    }

    if(mContentIntent==null){
      Logger.debug("deleting notifications");
      mNotificationManager.cancel(NOTIFICATION_ID);
    } else {
      Logger.debug("assign '"+mContentIntent.toString()+"'to notification");
      mBuilder.setContentIntent(PendingIntent.getActivity(this, CLICK_CODE, mContentIntent, PENDING_INTENT_FLAGS))
              .setProgress(0,0,false)
              .setAutoCancel(true)
              .setDeleteIntent(PendingIntent.getActivity(this, 0, new Intent(), PENDING_INTENT_FLAGS))
              .setChannelId(getBaseContext().getString(R.string.csploitChannelId));
      mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }
    if(mReceiver!=null)
      unregisterReceiver(mReceiver);
    mReceiver             = null;
    mBuilder              = null;
    mNotificationManager  = null;
  }

  private void handleMultiAttackIntent(Intent intent) {
    int[] actions,targetsIndex;
    int i;
    ExecutorService executorService;

    //initialize data
    int tasks = 0;

    actions = intent.getIntArrayExtra(MULTI_ACTIONS);
    targetsIndex = intent.getIntArrayExtra(MULTI_TARGETS);

    if(actions == null || targetsIndex == null)
      return;

    mRunning = true;

    //fetch targets
    // TODO: rewrite this service since target index may change
    List<Target> list = System.getTargets();
    Target[] targets = new Target[targetsIndex.length];

    for(i =0; i< targetsIndex.length;i++)
      targets[i] = list.get(targetsIndex[i]);

    //fetch tasks
    for(int stringId : actions) {
      switch (stringId) {
        case R.string.trace:
          tasks |=TRACE;
          break;
        case R.string.port_scanner:
          tasks |=SCAN;
          break;
        case R.string.inspector:
          tasks |=INSPECT;
          break;
        case R.string.exploit_finder:
          tasks |=EXPLOIT;
          break;
        case R.string.login_cracker:
          tasks |=CRACK;
          break;
      }
    }

    Logger.debug("targets: "+targets.length);
    Logger.debug("tasks: "+ tasks);

    setupNotification();

    mBuilder.setContentTitle(getString(R.string.multiple_attack))
            .setSmallIcon(R.drawable.ic_launcher)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .setChannelId(getBaseContext().getString(R.string.csploitChannelId));
    ServiceCompat.startForeground(
        this,
        NOTIFICATION_ID,
        mBuilder.build(),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);

    // create and start threads
    totalTargets = targets.length;
    final int fTasks = tasks;
    executorService = Executors.newFixedThreadPool(targets.length);

    for(Target t : targets) {
      executorService.submit(new SingleWorker(fTasks, t));
    }

    executorService.shutdown();

    while(mRunning) {
      try {
        executorService.awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        break;
      }
    }

    executorService.shutdownNow();

    mRunning = false;
  }

  @Override
  public void onDestroy() {
    finishNotification();
    if (workerThread != null) {
      workerThread.quitSafely();
    }
    super.onDestroy();
  }
}
