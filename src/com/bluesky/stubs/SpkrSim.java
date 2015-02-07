package com.bluesky.stubs;

import com.bluesky.DataSink;

import java.nio.ByteBuffer;

/**
 * Created by liangc on 07/02/15.
 */
public class SpkrSim implements DataSink {
    public SpkrSim(){

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
    public boolean release() {
        return false;
    }

    @Override
    public boolean offerData(ByteBuffer data, short sequence) {
        return false;
    }

    @Override
    public void registerListener(Listener listener) {

    }
}
