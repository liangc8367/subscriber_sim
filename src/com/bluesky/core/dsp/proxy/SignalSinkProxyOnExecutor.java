package com.bluesky.core.dsp.proxy;

import com.bluesky.core.dsp.SignalModule;
import com.bluesky.core.dsp.SignalSink;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

/**
 * Created by liangc on 12/03/15.
 */
public class SignalSinkProxyOnExecutor implements SignalSink {
    private SignalSink subject;
    private ExecutorService executor;

    public SignalSinkProxyOnExecutor(SignalSink subject, ExecutorService executor){
        this.subject = subject;
        this.executor = executor;
        subject.register(this.listener);
    }

    @Override
    public boolean offer(ByteBuffer data, short sequence) {
        return subject.offer(data, sequence);
    }

    @Override
    public boolean start() {
        return subject.start();
    }

    @Override
    public boolean stop() {
        return subject.stop();
    }

    @Override
    public boolean reset() {
        return subject.reset();
    }

    @Override
    public boolean release() {
        return subject.release();
    }

    @Override
    public void register(Listener listener) {
        registeredListener = listener;
    }

    private class PrivateListener implements SignalModule.Listener {

        @Override
        public void onEndOfLife() {
            if(registeredListener != null){
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        registeredListener.onEndOfLife();
                    }
                });
            }
        }
    }

    final PrivateListener listener = new PrivateListener();

    private Listener registeredListener = null;

}
