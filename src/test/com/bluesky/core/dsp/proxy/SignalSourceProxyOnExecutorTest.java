package test.com.bluesky.core.dsp.proxy; 

import com.bluesky.core.dsp.SignalModule;
import com.bluesky.core.dsp.SignalSource;
import com.bluesky.core.dsp.proxy.SignalSourceProxyOnExecutor;
import org.junit.Test;
import org.junit.Before; 
import org.junit.After;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;

/** 
* SignalSourceProxyOnExecutor Tester. 
* 
* @author <Authors name> 
* @since <pre>Mar 12, 2015</pre> 
* @version 1.0 
*/
@RunWith(MockitoJUnitRunner.class)
public class SignalSourceProxyOnExecutorTest {
    final ExecutorService executor = Executors.newScheduledThreadPool(1);

    private static class MockSubject implements SignalSource {

        @Override
        public void register(DataAvailableHandler handler) {
            this.dataHandler = handler;
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

        public void dataAvailable(ByteBuffer data){
            if(dataHandler !=null ){
                dataHandler.dataAvailable(data);
            }
        }

        private Listener listener = null;
        private DataAvailableHandler dataHandler = null;
    }

    @Mock
    SignalModule.Listener listener;
    @Mock
    SignalSource.DataAvailableHandler dataListener;

    MockSubject subject;
    SignalSourceProxyOnExecutor proxy;

    @Before
    public void before() throws Exception {
        Mockito.reset(listener);
        Mockito.reset(dataListener);
        subject = new MockSubject();
        proxy = new SignalSourceProxyOnExecutor(subject, executor);
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

    @Test
    public void testProxyShallRelayData() throws Exception{
        proxy.register(dataListener);
        int total = 20;
        for(int i=0; i<total; ++i){
            ByteBuffer data = ByteBuffer.allocate(i+1);
            subject.dataAvailable(data);
        }

        executor.shutdown();
        boolean result = executor.awaitTermination(20L, TimeUnit.SECONDS);

        assertEquals(true, result);
        Mockito.verify(dataListener, times(total)).dataAvailable(any(ByteBuffer.class));
    }

    private static class TestDataHandler implements SignalSource.DataAvailableHandler {
        int receivedCount = 0;
        boolean dataValid = true;

        @Override
        public void dataAvailable(ByteBuffer byteBuffer) {
            ++receivedCount;
            if(byteBuffer.limit() != receivedCount){
                dataValid = false;
            }
        }
    }

    @Test
    public void testProxyShallRelayData2() throws Exception{
        TestDataHandler dataHandler = new TestDataHandler();
        proxy.register(dataHandler);
        int total = 20;
        for(int i=0; i<total; ++i){
            ByteBuffer data = ByteBuffer.allocate(i+1);
            subject.dataAvailable(data);
        }

        executor.shutdown();
        boolean result = executor.awaitTermination(20L, TimeUnit.SECONDS);

        assertEquals(true, result);
        assertEquals(total, dataHandler.receivedCount);
        assertEquals(true, dataHandler.dataValid);
    }

}
