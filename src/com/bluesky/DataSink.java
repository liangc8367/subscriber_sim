package com.bluesky;

import java.nio.ByteBuffer;

/**
 * Created by liangc on 01/02/15.
 */
public interface DataSink {

    public boolean start();

    public boolean stop();

    public boolean release();

    public boolean offerData(ByteBuffer data, short sequence);

    public interface Listener{
        public void onEndOfLife();
    }

    public void registerListener(Listener listener);
}
