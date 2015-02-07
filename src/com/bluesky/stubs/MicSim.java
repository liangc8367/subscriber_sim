package com.bluesky.stubs;

import com.bluesky.DataSource;

/**
 * Created by liangc on 07/02/15.
 */
public class MicSim implements DataSource{
    public MicSim(){

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
    public void setCompletionHandler(CompletionHandler handler) {

    }
}
