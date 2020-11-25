package org.thoughtcrime.securesms.components;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.jobs.TypingSendJob;
import org.thoughtcrime.securesms.loki.protocol.SessionMetaProtocol;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.Util;
import org.session.libsignal.service.loki.protocol.shelved.multidevice.MultiDeviceProtocol;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@SuppressLint("UseSparseArrays")
public class TypingStatusSender {

  private static final String TAG = TypingStatusSender.class.getSimpleName();

  private static final long REFRESH_TYPING_TIMEOUT = TimeUnit.SECONDS.toMillis(10);
  private static final long PAUSE_TYPING_TIMEOUT   = TimeUnit.SECONDS.toMillis(3);

  private final Context              context;
  private final Map<Long, TimerPair> selfTypingTimers;

  public TypingStatusSender(@NonNull Context context) {
    this.context          = context;
    this.selfTypingTimers = new HashMap<>();
  }

  public synchronized void onTypingStarted(long threadId) {
    TimerPair pair = Util.getOrDefault(selfTypingTimers, threadId, new TimerPair());
    selfTypingTimers.put(threadId, pair);

    if (pair.getStart() == null) {
      sendTyping(threadId, true);

      Runnable start = new StartRunnable(threadId);
      Util.runOnMainDelayed(start, REFRESH_TYPING_TIMEOUT);
      pair.setStart(start);
    }

    if (pair.getStop() != null) {
      Util.cancelRunnableOnMain(pair.getStop());
    }

    Runnable stop = () -> onTypingStopped(threadId, true);
    Util.runOnMainDelayed(stop, PAUSE_TYPING_TIMEOUT);
    pair.setStop(stop);
  }

  public synchronized void onTypingStopped(long threadId) {
    onTypingStopped(threadId, false);
  }

  private synchronized void onTypingStopped(long threadId, boolean notify) {
    TimerPair pair = Util.getOrDefault(selfTypingTimers, threadId, new TimerPair());
    selfTypingTimers.put(threadId, pair);

    if (pair.getStart() != null) {
      Util.cancelRunnableOnMain(pair.getStart());

      if (notify) {
        sendTyping(threadId, false);
      }
    }

    if (pair.getStop() != null) {
      Util.cancelRunnableOnMain(pair.getStop());
    }

    pair.setStart(null);
    pair.setStop(null);
  }

  private void sendTyping(long threadId, boolean typingStarted) {
    ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(context);
    Recipient recipient = threadDatabase.getRecipientForThreadId(threadId);
    // Loki - Check whether we want to send a typing indicator to this user
    if (recipient != null && !SessionMetaProtocol.shouldSendTypingIndicator(recipient.getAddress())) { return; }
    // Loki - Take into account multi device
    if (recipient == null) { return; }
    Set<String> linkedDevices = MultiDeviceProtocol.shared.getAllLinkedDevices(recipient.getAddress().serialize());
    for (String device : linkedDevices) {
      Recipient deviceAsRecipient = Recipient.from(context, Address.fromSerialized(device), false);
      long deviceThreadID = threadDatabase.getOrCreateThreadIdFor(deviceAsRecipient);
      ApplicationContext.getInstance(context).getJobManager().add(new TypingSendJob(deviceThreadID, typingStarted));
    }
  }

  private class StartRunnable implements Runnable {

    private final long threadId;

    private StartRunnable(long threadId) {
      this.threadId = threadId;
    }

    @Override
    public void run() {
      sendTyping(threadId, true);
      Util.runOnMainDelayed(this, REFRESH_TYPING_TIMEOUT);
    }
  }

  private static class TimerPair {
    private Runnable start;
    private Runnable stop;

    public Runnable getStart() {
      return start;
    }

    public void setStart(Runnable start) {
      this.start = start;
    }

    public Runnable getStop() {
      return stop;
    }

    public void setStop(Runnable stop) {
      this.stop = stop;
    }
  }
}
