package com.bluesky.core.dsp.stubs;

import com.bluesky.core.dsp.SignalModule;
import com.bluesky.core.dsp.SignalSource;

/**
 * Created by liangc on 07/02/15.
 */
public class DummyMic implements SignalSource {

    @Override
    public void register(DataAvailableHandler handler) {

    }

    @Override
    public boolean start() {
        return false;
    }

    @Override
    public boolean stop() {
        return false;
    }

    @Override
    public boolean reset() {
        return false;
    }

    @Override
    public boolean release() {
        return false;
    }

    @Override
    public void register(Listener listener) {

    }
}
