package test.com.bluesky.experiments;

import junit.framework.Assert;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;

import static junit.framework.TestCase.assertEquals;


/** test scheduled executor service
 * Created by liangc on 11/03/15.
 */
public class ScheduledExecutorServiceTest {

    static class Su {
        public void packetRxed(ByteBuffer buf){
            ++mRxPacketCount;
            if( buf.capacity() != mRxPacketCount ){
                mValidateRxPacket = false;
            }
        }

        public boolean validateAllRxedPacket(){
            return mValidateRxPacket;
        }

        public int getRxedPacketCount(){
            return mRxPacketCount;
        }

        boolean mValidateRxPacket = true;
        int mRxPacketCount = 0;

        //////////////// timer part
        public void fineTimerExpired(){
            ++mFineTimerCount;
        }

        public int mFineTimerCount = 0;

        public void coarseTimerExpired(){
            ++mCoarseTimerCount;
        }

        public int mCoarseTimerCount = 0;

        /////////////
        public void scheduleFineTimer(ScheduledExecutorService executor, long delay, TimeUnit unit){
            scheduledFineTimer = executor.schedule(fineTimer, delay, unit);
        }
        public void scheduleCoarseTimer(ScheduledExecutorService executor, long delay, TimeUnit unit){
            scheduledCoarseTimer = executor.schedule(coarseTimer, delay, unit);
        }

        ///////////////
        public boolean cancelFineTimer(){
            if(scheduledFineTimer!=null){
                return scheduledFineTimer.cancel(false); // not allow to interrupt the execution thread
            }
            return false;
        }

        public boolean cancelCoarseTimer(){
            if(scheduledCoarseTimer!=null){
                return scheduledCoarseTimer.cancel(false); // not allow to interrupt the execution thread
            }
            return false;
        }

        private ScheduledFuture scheduledFineTimer, scheduledCoarseTimer;

        public class FineTimer implements Runnable {
            @Override
            public void run(){
                Su.this.fineTimerExpired();
            }
        }

        public class CoarseTimer implements Runnable {
            @Override
            public void run(){
                Su.this.coarseTimerExpired();
            }
        }

        public FineTimer fineTimer = new FineTimer();
        public CoarseTimer coarseTimer = new CoarseTimer();
    }

    static class RxedPacketCourier implements Runnable {
        private Su mSu;
        private ScheduledExecutorService mScheduler;
        public RxedPacketCourier(final Su su, ScheduledExecutorService scheduler){
            mSu = su;
            mScheduler = scheduler;
        }

        @Override
        public void run(){
            ByteBuffer buf;
            while((buf=mQueue.poll())!=null){
                mSu.packetRxed(buf);
            }
        }

        public void packetRxed(ByteBuffer buf){
            mQueue.add(buf);
            mScheduler.schedule(this, 0, TimeUnit.MILLISECONDS);
        }

        final ConcurrentLinkedQueue<ByteBuffer> mQueue = new ConcurrentLinkedQueue<ByteBuffer>();

    }

    ScheduledExecutorService executor;
    RxedPacketCourier courier;
    Su su;

    @Before
    public void initialize(){
        executor = Executors.newScheduledThreadPool(1);
        su = new Su();
        courier = new RxedPacketCourier(su, executor);
    }

    @After
    public void cleanup(){
        su = null;
        courier = null;
        executor = null;
    }

    @Test
    public void testSuShouldHaveReceivedEnoughPackets() throws Exception {
        int total = 20;
        for(int i = 1; i<= total; ++i) {
            ByteBuffer buf = ByteBuffer.allocate(i);
            courier.packetRxed(buf);
        }

        executor.shutdown();
        executor.awaitTermination(20L, TimeUnit.SECONDS);

        System.out.println("su rxed packet = " + su.getRxedPacketCount());
        System.out.println("su packet validation = " + su.validateAllRxedPacket());

        assertEquals(true, su.validateAllRxedPacket());
        assertEquals(total, su.getRxedPacketCount());
    }

    @Test
    public void testSuShouldHaveReceivedAllFineTimerEvents() throws Exception {
        int total = 20;
        for(int i = 0; i< total; ++i) {
            executor.schedule(su.fineTimer, 20, TimeUnit.MILLISECONDS);
        }

        executor.shutdown();
        boolean result = executor.awaitTermination(20L, TimeUnit.SECONDS);

        System.out.println("su fineTimer expired: " + su.mFineTimerCount);

        assertEquals(true, result);
        assertEquals(total, su.mFineTimerCount);
    }

    @Test
    public void testSuShouldHaveReceivedAllCoarseTimerEvents() throws Exception {
        int total = 20;
        for(int i = 0; i< total; ++i) {
            executor.schedule(su.fineTimer, 20*6, TimeUnit.MILLISECONDS);
        }

        executor.shutdown();
        boolean result = executor.awaitTermination(20L, TimeUnit.SECONDS);

        System.out.println("su fineTimer expired: " + su.mFineTimerCount);

        assertEquals(true, result);
        assertEquals(total, su.mFineTimerCount);
    }

    @Test
    public void testSuShouldCancelFutureTimer() throws Exception {
        // schedule two timer events, with long delay
        System.out.println("to schedule @" + System.currentTimeMillis());
        su.scheduleFineTimer(executor, 5L, TimeUnit.SECONDS);
        su.scheduleCoarseTimer(executor, 5L, TimeUnit.SECONDS);

        // cancel them,
        System.out.println("to cancel @" + System.currentTimeMillis());
        boolean result1 = su.cancelFineTimer();
        boolean result2 = su.cancelCoarseTimer();

        System.out.println("cancel fine = " + result1);
        System.out.println("cancel coarse = " + result2);

        assertEquals(true, result1);
        assertEquals(true, result2);

//        List<Runnable> pendingTimerEvents = executor.shutdownNow();
//        assertEquals(0, pendingTimerEvents.size());
        System.out.println("to shutdown @" + System.currentTimeMillis());
        executor.shutdown();
        boolean result3 = executor.awaitTermination(10L, TimeUnit.SECONDS);
        System.out.println("await result=" + result3);

        System.out.println("to verify @" + System.currentTimeMillis());
        assertEquals(true, result3);
        assertEquals(0, su.mFineTimerCount);
        assertEquals(0, su.mCoarseTimerCount);

    }
}
