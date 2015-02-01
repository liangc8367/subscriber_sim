package com.bluesky;

import java.nio.ByteBuffer;

/**
 * interface of Data sources.
 * Created by liangc on 08/01/15.
 */
public interface DataSource {
    public boolean start();

    public boolean stop();

    public boolean release();

    /** callback when audio data is available */
    public interface CompletionHandler{
        /** callback for available data */
        public void dataAvailable(ByteBuffer byteBuffer);
        /** callback when datasource ends its life */
        public void onEndOfLife();
    }

    /** downstream user registers its completion handler via this method */
    public void setCompletionHandler(CompletionHandler handler);

}