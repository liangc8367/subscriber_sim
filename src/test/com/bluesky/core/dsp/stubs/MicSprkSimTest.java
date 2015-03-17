package test.com.bluesky.core.dsp.stubs;

import com.bluesky.core.dsp.SignalModule;
import com.bluesky.core.dsp.SignalSink;
import com.bluesky.core.dsp.SignalSource;
import com.bluesky.core.dsp.stubs.MicNoiseSim;
import com.bluesky.core.dsp.stubs.SpkrNoiseSim;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static junit.framework.TestCase.assertTrue;

/**
 * Created by liangc on 16/03/15.
 */
public class MicSprkSimTest {
    @Test
    public void testSanity() throws Exception{
        // setup
        final ScheduledExecutorService micExecutor = Executors.newSingleThreadScheduledExecutor();
        final ScheduledExecutorService spkrExecutor = Executors.newSingleThreadScheduledExecutor();

        MicNoiseSim mic = new MicNoiseSim(micExecutor);
        SpkrNoiseSim spkr = new SpkrNoiseSim(spkrExecutor);

        SignalModule.Listener micListener = new SignalModule.Listener() {
            @Override
            public void onEndOfLife() {
                System.out.println("mic end of life");
            }
        };
        mic.register(micListener);

        SignalModule.Listener spkrListener = new SignalModule.Listener() {
            @Override
            public void onEndOfLife() {
                System.out.println("spkr end of life");
            }
        };
        spkr.register(spkrListener);

        SignalSource.DataAvailableHandler micDataHandler = new SignalSource.DataAvailableHandler() {
            @Override
            public void dataAvailable(ByteBuffer byteBuffer) {
                spkr.offer(byteBuffer, seq++);
            }

            SignalSource.DataAvailableHandler ctor(SignalSink spkr){
                this.spkr = spkr;
                return this;
            }

            SignalSink spkr;
            short seq = 0;
        }.ctor(spkr);

        mic.register(micDataHandler);

        //
        spkr.start();
        mic.start();

        // run for 1 minute (about 50 packets)
        for( int i = 0; i< 50; i++){
            Thread.sleep(20);
            SpkrNoiseSim.Stats stats = spkr.getStats();
            System.out.println(
                    "total =" + stats.total +
                    ", lost=" + stats.lost +
                    ", bad_size =" + stats.bad_size +
                    ", bad_seq =" + stats.bad_seq +
                    ", bad_content= " + stats.bad_content);
        }

        spkr.stop();
        mic.stop();

        MicNoiseSim.Stats micStats = mic.getStats();
        SpkrNoiseSim.Stats spkrStats = spkr.getStats();
        System.out.println("mic generated=" + micStats.total + ", spkr total = " + spkrStats.total);
        assertTrue(micStats.total > 10);
        assertTrue(spkrStats.total > 10);
        assertTrue(spkrStats.lost == 0);
        assertTrue(spkrStats.bad_seq == 0);
        assertTrue(spkrStats.bad_seq == 0);
        assertTrue(spkrStats.bad_content == 0);
    }
}
