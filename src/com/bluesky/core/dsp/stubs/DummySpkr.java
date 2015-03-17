package com.bluesky.core.dsp.stubs;

import com.bluesky.core.dsp.SignalModule;
import com.bluesky.core.dsp.SignalSink;

import java.nio.ByteBuffer;

/**
 * Created by liangc on 07/02/15.
 */
public class DummySpkr implements SignalSink {

    @Override
    public boolean offer(ByteBuffer data, short sequence) {
        return false;
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
