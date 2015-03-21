package com.bluesky.TestHarness;

import com.bluesky.common.*;
import com.bluesky.core.database.SubscriberDatabaseHelper;
import com.bluesky.core.dsp.SignalSink;
import com.bluesky.core.dsp.SignalSource;
import com.bluesky.core.dsp.stubs.DummyMic;
import com.bluesky.core.dsp.stubs.DummySpkr;
import com.bluesky.core.dsp.stubs.MicNoiseSim;
import com.bluesky.core.dsp.stubs.SpkrNoiseSim;
import com.bluesky.core.hal.ReferenceClock;
import com.bluesky.core.subscriber.Configuration;
import com.bluesky.core.subscriber.State;
import com.bluesky.core.subscriber.Subscriber;
import com.bluesky.core.subscriber.SubscriberAssembler;

import java.net.InetSocketAddress;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** create a set of subscribers,
 *  monitor their states, gather their ongoing callinfo,
 *  and validate
 *      no split call,
 *      no missed call
 * Created by liangc on 21/03/15.
 */
public class Farmer {

    final SubscriberDatabase mDatabase;

    public Farmer(SubscriberDatabase database){
        mDatabase = database;
    }

    /** start to work, create subscribers, and start to monitor
     *
     * @return
     */
    public boolean start(){
        createSubscribers();
        try{
            Thread.sleep(1000); // ugly way to wait until udp starts
        }catch(Exception e){
            System.out.println("hmm... ex" + e);
        }
        startSubscribers();
        return true;
    }

    public final AbstractList<Subscriber> getSubscribers(){
        return mSubscribers;
    }

    private void createSubscribers(){
        for( Long su_id : mDatabase.getSubscribers()){
            List<Long> grps = mDatabase.getGroups(su_id);

            XLog logger = new XLog();

            UDPService.Configuration udpSvcConfig = new UDPService.Configuration();
            udpSvcConfig.addrLocal = new InetSocketAddress(0); //any local port
            udpSvcConfig.addrRemote = new InetSocketAddress(
                    GlobalConstants.TRUNK_CENTER_ADDR,
                    GlobalConstants.TRUNK_CENTER_PORT
            );
            udpSvcConfig.clientMode = true;
            UDPService udpSvc = new UDPService(udpSvcConfig, logger);
            udpSvc.startService();

            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            Configuration config = new Configuration();
            config.mSuid = su_id;
            config.mTgtid = grps.get(0); // default group id

            SignalSource mic = new MicNoiseSim(executor);
            SignalSink spkr = new SpkrNoiseSim(executor);


            Subscriber sub = SubscriberAssembler.assemble(config, executor, mic, spkr, udpSvc, mClock, logger);
            mSubscribers.add(sub);

            SubStateListener listener = new SubStateListener(sub, su_id);
            sub.registerStateListener(listener);
            mSubListener.add(listener);
        }
    }

    private void startSubscribers(){
        for(Subscriber sub : mSubscribers){
            sub.start();
        }
    }

    private class SubStateListener implements Subscriber.StateListener {
        public SubStateListener(Subscriber sub, long su_id){
            mSub = sub;
            mid = su_id;
        }
        @Override
        public void stateChanged(State newState) {
            CallInformation callInfo = mSub.getCallInfo();
            System.out.println("Su[" + mid + "]: state[" + newState +
                    "], target = " + callInfo.mTargetId +
                    ", source = " + callInfo.mSourceId);
        }
        private Subscriber mSub;
        private long mid;
    }
    private ArrayList<Subscriber> mSubscribers = new ArrayList<Subscriber>();
    private ArrayList<SubStateListener> mSubListener = new ArrayList<SubStateListener>();
    private final ReferenceClock mClock =new ReferenceClock();

}
