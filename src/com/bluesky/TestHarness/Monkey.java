package com.bluesky.TestHarness;

import com.bluesky.core.subscriber.Configuration;
import com.bluesky.core.subscriber.Subscriber;

import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** randomly press/release ptt key
 * Created by liangc on 21/03/15.
 */
public class Monkey {
    private boolean mRunning = false;
    private final List<Subscriber> mSubs;
    private final ScheduledExecutorService mExec = Executors.newSingleThreadScheduledExecutor();
    private final Random mRnd = new Random();
    private final boolean mSingleHand = true;

    public Monkey(List<Subscriber> subs){
        mSubs = subs;
        mRnd.setSeed(System.currentTimeMillis());
    }

    public void start(){
        if(mSingleHand) {
            itchRandomSingle();
        }else {
            itchAll();
        }
    }

    public void stop(){
        mExec.shutdown();
    }

    private void itchAll(){
        for(Subscriber sub : mSubs){
            Itch itch = new Itch(sub);
            long next = (long)(mRnd.nextInt() % 5);
            mExec.schedule(itch, next, TimeUnit.SECONDS);
        }
    }

    private void itchRandomSingle(){
        int idx = mRnd.nextInt();
        if(idx< 0){
            idx = -idx;
        }
        idx = idx % mSubs.size();
        Itch itch = new Itch(mSubs.get(idx));
        long next = (long)(mRnd.nextInt() % 5);
        mExec.schedule(itch, next, TimeUnit.SECONDS);
    }

    private class Itch implements Runnable{
        private final Subscriber sub;
        private boolean bPressed = false;
        private final Configuration conf;

        public Itch(Subscriber sub){
            this.sub = sub;
            conf = sub.getConfig();
        }

        @Override
        public void run() {
            bPressed = !bPressed;
            System.out.println("to press Sub[" + conf.mSuid + "]:" + bPressed );
            sub.ptt(bPressed);

            long next = (long) Monkey.this.mRnd.nextInt();
            if( next < 0 ) {
                next = -next;
            }
            next = next % 5;

            // only press & release once
            if(bPressed) {
                Monkey.this.mExec.schedule(this, next, TimeUnit.SECONDS);
            }
        }

    }
}
