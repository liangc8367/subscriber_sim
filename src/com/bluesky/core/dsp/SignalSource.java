package com.bluesky.core.dsp;

import java.nio.ByteBuffer;

/**
 * interface of Data sources.
 * Created by liangc on 08/01/15.
 */
public interface SignalSource extends SignalModule{

    /** callback when audio data is available */
    public interface DataAvailableHandler {
        /** callback for available data */
        public void dataAvailable(ByteBuffer byteBuffer);
    }

    /** downstream user registers its completion handler via this method */
    public void register(DataAvailableHandler handler);

}