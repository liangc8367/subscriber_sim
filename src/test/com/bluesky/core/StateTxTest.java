package test.com.bluesky.core;

import com.bluesky.DataSink;
import com.bluesky.DataSource;
import com.bluesky.common.*;
import com.bluesky.core.subscriber.*;
import com.bluesky.protocol.CallData;
import com.bluesky.protocol.CallInit;
import com.bluesky.protocol.CallTerm;
import com.bluesky.protocol.Registration;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import test.com.bluesky.core.helpers.PayloadMatcher;
import test.com.bluesky.core.helpers.SubscriberPeeper;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.AdditionalMatchers.leq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.stub;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Created by liangc on 08/03/15.
 */
@RunWith(MockitoJUnitRunner.class)
public class StateTxTest {
    @Mock
    DataSink spkr;
    @Mock
    DataSource mic;
    @Mock
    UDPService udpService;
    @Mock
    SubscriberExecContext execCtx;
    @Mock
    OLog logger;

    final Configuration config = new Configuration();
    final NamedTimerTask timerTask = new NamedTimerTask(20) {
        @Override
        public void run() {

        }
    };

    Subscriber su;
    StateTx stateTx;

    private void setup() throws Exception{
        Mockito.reset(udpService);
        Mockito.reset(execCtx);
        stub(execCtx.createTimerTask()).toReturn(timerTask);
        when(execCtx.currentTimeMillis())
                .thenReturn(100L)
                .thenReturn(102L) // sent #1
                .thenReturn(122L) // sent #2
                .thenReturn(141L) // sent #3
                .thenReturn(161L); // fall to online
        config.mSuid = 100;
        config.mTgtid = 1000;
        su = new Subscriber(config, execCtx, mic, spkr, udpService, logger);
        SubscriberPeeper peeper = new SubscriberPeeper();
        peeper.setState(su, State.TX);

        stateTx = new StateTx(su);
    }

    /*
    *  - on data available: sent data
    *  - ptt release: stop tx, and transit to TxStopping
    *  - txEnd: tx stopped prior to ptt release: call hang
    */
    @Test
    public void test_tx_ptt_released() throws Exception {
        setup();

        stateTx.entry();
        stateTx.ptt(false);

        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.TX_STOPPING, peeper.peepState(su));
    }

    @Test
    public void test_tx_ptt_mic_dataAvailable() throws Exception {
        setup();

        stateTx.entry();

        ByteBuffer audio = ByteBuffer.allocate(20);
        stateTx.micDataAvailable(audio);

        Mockito.verify(udpService, times(1)).send((ByteBuffer) argThat(
                new PayloadMatcher(
                        config.mTgtid,
                        config.mSuid,
                        CallData.class)));
    }

    @Test
    public void test_tx_tx_end() throws Exception {
        setup();

        stateTx.entry();
        stateTx.txEnd();

        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.CALL_HANG, peeper.peepState(su));
    }

}