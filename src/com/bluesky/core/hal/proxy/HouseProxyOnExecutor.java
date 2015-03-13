package com.bluesky.core.hal.proxy;

import com.bluesky.core.hal.House;

import java.util.concurrent.ExecutorService;

/**
 * Created by liangc on 12/03/15.
 */
public class HouseProxyOnExecutor implements House{
    private House subject;
    private ExecutorService executor;

    public HouseProxyOnExecutor(House subject, ExecutorService executor){
        this.subject = subject;
        this.executor = executor;
        this.subject.register(this.listener);
    }

    @Override
    public void register(Listener listener) {
        this.registeredListener = listener;
    }

    private class PrivateListener implements Listener{

        @Override
        public void onPtt(boolean pressed) {
            if( registeredListener != null) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        registeredListener.onPtt(this.press);
                    }

                    private boolean press;
                    Runnable ctor(boolean press){
                        this.press = press;
                        return this;
                    }
                }.ctor(pressed));
            }
        }
    }

    private PrivateListener listener = new PrivateListener();
    private Listener registeredListener = null;
}
