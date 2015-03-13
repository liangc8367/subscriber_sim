package test.com.bluesky.core.dsp.proxy; 

import com.bluesky.core.dsp.SignalModule;
import com.bluesky.core.dsp.SignalSink;
import com.bluesky.core.dsp.proxy.SignalSinkProxyOnExecutor;
import org.junit.Test;
import org.junit.Before; 
import org.junit.After;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.times;

/** 
* SignalSinkProxyOnExecutor Tester. 
* 
* @author <Authors name> 
* @since <pre>Mar 12, 2015</pre> 
* @version 1.0 
*/
@RunWith(MockitoJUnitRunner.class)
public class SignalSinkProxyOnExecutorTest {

    final ExecutorService executor = Executors.newScheduledThreadPool(1);

    private static class MockSubject implements SignalSink {

        @Override
        public boolean start() {
            return false;
        }

        @Override
        public boolean stop() {
            return false;
        }

        @Override
        public boolean reset() {
            return false;
        }

        @Override
        public boolean release() {
            return false;
        }

        @Override
        public void register(Listener listener) {
            this.listener = listener;
        }

        public void eof(){
            if(listener != null){
                listener.onEndOfLife();
            }
        }


        private Listener listener = null;

        @Override
        public boolean offer(ByteBuffer data, short sequence) {
            return false;
        }
    }

    @Mock
    SignalModule.Listener listener;

    MockSubject subject;
    SignalSinkProxyOnExecutor proxy;

    @Before
    public void before() throws Exception {
        Mockito.reset(listener);
        subject = new MockSubject();
        proxy = new SignalSinkProxyOnExecutor(subject, executor);
    }

    @After
    public void after() throws Exception {
        proxy = null;
    }

    @Test
    public void testProxyShallRelayOnEndOfLife() throws Exception{
        proxy.register(listener);
        int total = 20;
        for(int i=0; i<total; ++i){
            subject.eof();
        }

        executor.shutdown();
        boolean result = executor.awaitTermination(20L, TimeUnit.SECONDS);

        assertEquals(true, result);
        Mockito.verify(listener, times(total)).onEndOfLife();
    }

}
