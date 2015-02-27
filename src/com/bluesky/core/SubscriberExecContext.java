package com.bluesky.core;

import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.OLog;
import com.bluesky.common.UDPService;
import com.bluesky.core.Subscriber;

import java.net.DatagramPacket;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;

/**
 * decouple the relay of trigger events, so subscriber doesn't care who calls those triggers
 * Created by liangc on 07/02/15.
 */
public class SubscriberExecContext {
    public SubscriberExecContext(final Subscriber sub, final ExecutorService exec, final UDPService udpSvc, final OLog logger){
        mSub = sub;
        mExecutor = exec;
        mLogger = logger;
        mUdpSvc = udpSvc;
        mUdpSvc.setCompletionHandler(new UDPService.CompletionHandler() {
            @Override
            public void completed(DatagramPacket packet) {
                EvPacketRxed pktRxed = new EvPacketRxed(packet, mSub);
                mExecutor.execute(pktRxed);
            }
        });
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

    public void schedule(TimerTask timerTask, long delayMs){

    }


    private static class EvPacketRxed extends EventTrigger{
        public EvPacketRxed(DatagramPacket packet, final Subscriber sub){
            mPacket = packet;
            mSub = sub;
        }
        @Override
        public void run(){
            mSub.packetReceived(mPacket);
        }
        final DatagramPacket mPacket;
        final Subscriber mSub;
    }

    int mTimerSeed;
    final ExecutorService mExecutor;
    final Subscriber mSub;
    final UDPService mUdpSvc;

    static final String TAG = "SubEx";
    final OLog mLogger;
}
