package com.bluesky.core.dsp.stubs;

import com.bluesky.common.GlobalConstants;
import com.bluesky.core.dsp.SignalModule;
import com.bluesky.core.dsp.SignalSource;

import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** mic simulator, which generates random data (as if a noise generator)
 *  it executes on a ScheduledExecutorService
 *
 * Created by liangc on 14/03/15.
 */
public class MicNoiseSim implements SignalSource {
    final private ScheduledExecutorService mExecutor;
    private DataAvailableHandler mHandler = null;
    private Listener mListener = null;
    private boolean mRunning = false;
    private short mDataSequence = 0;
    private ScheduledFuture mScheduledJob;

    public MicNoiseSim(ScheduledExecutorService executor){
        mExecutor = executor;
    }

    @Override
    public void register(DataAvailableHandler handler) {
        mHandler = handler;
    }

    @Override
    public boolean start() {
        if(!mRunning){
            mScheduledJob = mExecutor.scheduleAtFixedRate(
                    mJob, GlobalConstants.CALL_PACKET_INTERVAL, GlobalConstants.CALL_PACKET_INTERVAL,
                    TimeUnit.MILLISECONDS);
            mRunning = true;
        }
        return true;
    }

    @Override
    public boolean stop() {
        if(mRunning){
            //NOTE: I assume cancel() always can success. Actually, there's race condition that
            // cancel() is called after scheduled-job has just been executed?
            mScheduledJob.cancel(false);
            mScheduledJob = null;
            mRunning = false;
            if(mListener!=null){
                mListener.onEndOfLife();
            }
        }
        return true;
    }

    @Override
    public boolean reset() {
        if(mRunning){
            return false;
        }
        internalReset();
        return true;
    }

    /** release, not supported
     *
     * @return
     */
    @Override
    public boolean release() {
        return true;
    }

    @Override
    public void register(Listener listener) {
        mListener = listener;
    }

    public Stats getStats(){
        return mStats;
    }

    private void internalReset(){
        mDataSequence = 0;
    }

    /** generate pseudo noise data
     *
     */
    void generateNoise(){
        ByteBuffer data = ByteBuffer.allocate(GlobalConstants.COMPRESSED_20MS_AUDIO_SIZE);
        for(int i = 0; i< GlobalConstants.COMPRESSED_20MS_AUDIO_SIZE/2; ++i){
            data.putShort((short)(i+mDataSequence));
        }
        data.flip();
        ++mDataSequence;
        ++mStats.total;
        if(mHandler != null){
            mHandler.dataAvailable(data);
        }
    }

    final Runnable mJob = new Runnable(){
        public void run(){
            MicNoiseSim.this.generateNoise();
        }
    };

    public static class Stats {
        public int total = 0;
    }
    private Stats mStats = new Stats();
}
