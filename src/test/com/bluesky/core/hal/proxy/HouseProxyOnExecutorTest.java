package test.com.bluesky.core.hal.proxy; 

import com.bluesky.core.hal.House;
import com.bluesky.core.hal.proxy.HouseProxyOnExecutor;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.Before; 
import org.junit.After;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.times;

/** 
* HouseProxyOnExecutor Tester. 
* 
* @author <Authors name> 
* @since <pre>Mar 13, 2015</pre> 
* @version 1.0 
*/
public class HouseProxyOnExecutorTest {

    private class MyHouse implements House{

        @Override
        public void register(Listener listener) {
            this.listener = listener;
        }

        public void ptt(boolean pressed){
            if(listener !=null ){
                listener.onPtt(pressed);
            }
        }

        private Listener listener = null;
    }

    private MyHouse subject;

    private class MyListener implements House.Listener{
        int eventCount = 0;
        boolean valid = true;
        @Override
        public void onPtt(boolean pressed) {
            if(((eventCount % 3)== 0)!=pressed){
                valid = false;
            }
            ++eventCount;
        }
    }

    MyListener listener;
    HouseProxyOnExecutor proxy;
    final ExecutorService executor = Executors.newScheduledThreadPool(1);

@Before
public void before() throws Exception {
    listener = new MyListener();
    subject = new MyHouse();
    proxy = new HouseProxyOnExecutor(subject, executor);
} 

@After
public void after() throws Exception {
    subject = null;
    proxy = null;
    listener = null;
}

    @Test
    public void testProxyShallRelayPtt() throws Exception{
        proxy.register(listener);
        int total = 20;
        for(int i=0; i<total; ++i){
            subject.ptt((i % 3) == 0);
        }

        executor.shutdown();
        boolean result = executor.awaitTermination(20L, TimeUnit.SECONDS);

        assertEquals(true, result);
        assertEquals(total, listener.eventCount);
        assertEquals(true, listener.valid);
    }




} 
