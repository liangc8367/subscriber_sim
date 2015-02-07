package com.bluesky.core;

import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.OLog;
import com.bluesky.core.Subscriber;

import java.util.concurrent.ExecutorService;

/**
 * decouple the relay of trigger events, so subscriber doesn't care who calls those triggers
 * Created by liangc on 07/02/15.
 */
public class SubscriberExecContext {
    public SubscriberExecContext(final Subscriber sub, final ExecutorService exec, final OLog logger){
        mSub = sub;
        mExecutor = exec;
        mLogger = logger;
    }


    private static abstract class EventTrigger implements Runnable {
    }

    private static class EvTimerExpired extends EventTrigger {
        public EvTimerExpired(NamedTimerTask timerTask, final Subscriber sub){
            mTimerTask = timerTask;
            mSub = sub;
        }
        @Override
        public void run(){
            mSub.timerExpired(mTimerTask);
        }

        NamedTimerTask mTimerTask;
        final Subscriber mSub;
    }

    public NamedTimerTask createTimerTask(){
        ++mTimerSeed;
        mLogger.d(TAG, "created timerTask: " + mTimerSeed);
        return new NamedTimerTask(mTimerSeed){
            @Override
            public void run() {
                EvTimerExpired tmExpired = new EvTimerExpired(this, mSub);
                mExecutor.execute(tmExpired);
            }
        };
    }

    int mTimerSeed;
    final ExecutorService mExecutor;
    final Subscriber mSub;

    static final String TAG = "SubEx";
    final OLog mLogger;
}
