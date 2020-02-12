package expo.modules.notifications.notifications.handling;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.messaging.RemoteMessage;

import org.unimodules.core.ModuleRegistry;
import org.unimodules.core.Promise;
import org.unimodules.core.interfaces.services.EventEmitter;

import java.util.UUID;

import expo.modules.notifications.notifications.RemoteMessageSerializer;
import expo.modules.notifications.notifications.interfaces.NotificationBehavior;

/**
 * A "task" responsible for managing response to a single notification.
 */
/* package */ class SingleNotificationHandlerTask {
  /**
   * {@link Handler} on which lifecycle events are executed.
   */
  private final static Handler HANDLER = new Handler(Looper.getMainLooper());

  /**
   * Name of the event asking the delegate for behavior.
   */
  private final static String HANDLE_NOTIFICATION_EVENT_NAME = "onHandleNotification";
  /**
   * Name of the event emitted if the delegate doesn't respond in time.
   */
  private final static String HANDLE_NOTIFICATION_TIMEOUT_EVENT_NAME = "onHandleNotificationTimeout";

  /**
   * Seconds since sending the {@link #HANDLE_NOTIFICATION_EVENT_NAME} until the task
   * is considered timed out.
   */
  private final static int SECONDS_TO_TIMEOUT = 3;

  private EventEmitter mEventEmitter;
  private RemoteMessage mRemoteMessage;
  private NotificationBehavior mBehavior;
  private NotificationsHandler mDelegate;
  private String mIdentifier;

  private Runnable mTimeoutRunnable = new Runnable() {
    @Override
    public void run() {
      SingleNotificationHandlerTask.this.handleTimeout();
    }
  };

  /* package */ SingleNotificationHandlerTask(ModuleRegistry moduleRegistry, RemoteMessage remoteMessage, NotificationsHandler delegate) {
    mEventEmitter = moduleRegistry.getModule(EventEmitter.class);
    mRemoteMessage = remoteMessage;
    mDelegate = delegate;

    mIdentifier = remoteMessage.getMessageId();
    if (mIdentifier == null) {
      mIdentifier = UUID.randomUUID().toString();
    }
  }

  /**
   * @return Identifier of the task ({@link RemoteMessage#getMessageId()} or a random {@link UUID}
   * if {@link RemoteMessage#getMessageId() is null.
   */
  /* package */ String getIdentifier() {
    return mIdentifier;
  }

  /**
   * Starts the task, i.e. sends an event to the app's delegate and starts a timeout
   * after which the task finishes itself.
   */
  /* package */ void start() {
    Bundle eventBody = new Bundle();
    eventBody.putString("id", getIdentifier());
    eventBody.putBundle("notification", RemoteMessageSerializer.toBundle(mRemoteMessage));
    mEventEmitter.emit(HANDLE_NOTIFICATION_EVENT_NAME, eventBody);

    HANDLER.postDelayed(mTimeoutRunnable, SECONDS_TO_TIMEOUT * 1000);
  }

  /**
   * Stops the task abruptly (in case the app is being destroyed and there is no reason
   * to wait for the response anymore).
   */
  /* package */ void stop() {
    finish();
  }

  /**
   * Informs the task of a response - behavior requested by the app.
   *
   * @param behavior Behavior requested by the app
   * @param promise  Promise to fulfill once the behavior is applied to the notification.
   */
  /* package */ void handleResponse(final NotificationBehavior behavior, final Promise promise) {
    mBehavior = behavior;
    HANDLER.post(new Runnable() {
      @Override
      public void run() {
        // here we would show the notification
        Log.d("NotificationHandlerTask", String.format("Showing notification %s with params: %s", getIdentifier(), mBehavior));
        if (behavior.hasAnyEffect()) {
          promise.reject("ERR_NOTIFICATION_PRESENTATION_IMPL", "Notification presenting not implemented.");
        } else {
          promise.resolve(null);
        }
        finish();
      }
    });
  }

  /**
   * Callback called by {@link #mTimeoutRunnable} after timeout time elapses.
   * <p>
   * Sends a timeout event to the app.
   */
  private void handleTimeout() {
    Bundle eventBody = new Bundle();
    eventBody.putString("id", getIdentifier());
    eventBody.putBundle("notification", RemoteMessageSerializer.toBundle(mRemoteMessage));
    mEventEmitter.emit(HANDLE_NOTIFICATION_TIMEOUT_EVENT_NAME, eventBody);

    finish();
  }

  /**
   * Callback called when the task fulfills its responsibility. Clears up {@link #HANDLER}
   * and informs {@link #mDelegate} of the task's state.
   */
  private void finish() {
    HANDLER.removeCallbacks(mTimeoutRunnable);
    mDelegate.onTaskFinished(this);
  }
}
