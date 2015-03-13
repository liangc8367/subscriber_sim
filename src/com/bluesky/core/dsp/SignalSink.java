package com.bluesky.core.dsp;

import java.nio.ByteBuffer;

/**
 * Created by liangc on 01/02/15.
 */
public interface SignalSink extends SignalModule {

    /** offer data to sink
     *
     * @param data to be processed
     * @param sequence number, subclass of sink class can use sequence id to order/buffer incoming data
     * @return
     */
    public boolean offer(ByteBuffer data, short sequence);

}
