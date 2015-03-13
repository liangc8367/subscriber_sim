package com.bluesky.core.hal;


/**
 * interface of hardware house which supports basic subscriber
 * requirements.
 *
 * Created by liangc on 12/03/15.
 */
public interface House {
    public interface Listener{
        public void onPtt(boolean pressed);
    }
    public void register(Listener listener);
}
