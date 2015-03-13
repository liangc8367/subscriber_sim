package com.bluesky.core.dsp;

/**
 * Created by liangc on 12/03/15.
 */
public interface SignalModule {
    public boolean start();

    public boolean stop();

    public boolean reset();

    public boolean release();

    public interface Listener{
        /** callback when signal module is to end its life */
        public void onEndOfLife();
    }

    public void register(Listener listener);
}
