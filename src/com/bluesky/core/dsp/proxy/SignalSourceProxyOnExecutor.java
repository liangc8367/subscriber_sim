package com.bluesky.core.dsp.proxy;

import com.bluesky.core.dsp.SignalModule;
import com.bluesky.core.dsp.SignalSource;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

/** signal source proxy, based on executor service.
 * Created by liangc on 12/03/15.
 */
public class SignalSourceProxyOnExecutor implements SignalSource {
    private SignalSource subject;
    private ExecutorService executor;

    public SignalSourceProxyOnExecutor(SignalSource subject, ExecutorService executor){
        this.subject = subject;
        this.executor = executor;

        subject.register(this.listener);
        subject.register(this.dataAvailHandler);
    }

    @Override
    public void register(DataAvailableHandler handler) {
        registeredDataAvailableHandler = handler;
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

    private class PrivateDataAvailHandler implements SignalSource.DataAvailableHandler {

        @Override
        public void dataAvailable(ByteBuffer byteBuffer) {
            if(registeredDataAvailableHandler != null){
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        registeredDataAvailableHandler.dataAvailable(mData);
                    }
                    private ByteBuffer mData = null;
                    Runnable ctor(ByteBuffer data){
                        mData = data;
                        return this;
                    }
                }.ctor(byteBuffer));
            }
        }
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

    final PrivateDataAvailHandler dataAvailHandler = new PrivateDataAvailHandler();
    final PrivateListener listener = new PrivateListener();

    private DataAvailableHandler registeredDataAvailableHandler = null;
    private Listener registeredListener = null;

}
