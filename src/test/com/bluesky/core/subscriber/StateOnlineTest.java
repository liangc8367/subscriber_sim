package test.com.bluesky.core.subscriber;

import com.bluesky.core.dsp.SignalSink;
import com.bluesky.core.dsp.SignalSource;
import com.bluesky.common.GlobalConstants;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.OLog;
import com.bluesky.common.UDPService;
import com.bluesky.core.hal.ReferenceClock;
import com.bluesky.core.subscriber.*;

import com.bluesky.protocol.CallData;
import com.bluesky.protocol.CallInit;
import com.bluesky.protocol.CallTerm;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.*;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import test.com.bluesky.core.subscriber.helpers.SubscriberPeeper;

/**
 * Created by liangc on 08/03/15.
 */
@RunWith(MockitoJUnitRunner.class)
public class StateOnlineTest {
    @Mock
    SignalSink spkr;
    @Mock
    SignalSource mic;
    @Mock
    UDPService udpService;
    @Mock
    ScheduledExecutorService executor;
    @Mock
    OLog logger;
    @Mock
    ReferenceClock clock;

    final Configuration config = new Configuration();
    Subscriber su;
    StateOnline stateOnline;

    private void setup() throws Exception{
        Mockito.reset(udpService);
        Mockito.reset(executor);
        Mockito.reset(clock);

        config.mSuid = 100;
        config.mTgtid = 9000;
        su = new Subscriber(config, executor, mic, spkr, udpService, clock, logger);
        SubscriberPeeper peeper = new SubscriberPeeper();
        peeper.setState(su, State.ONLINE);

        stateOnline = new StateOnline(su);
    }

    /*
     *  - ptt pressed: transit to call init state
     *  - pkt rxed, callInit: transit to call rxing state
     *  - ptk rxed, callData: transit to call rxing state
     *  - pkt rxed, callTerm, transit to call hang state
     *  - ptt: to TxInit
     */
    @Test
    public void test_online_keep_alive() throws Exception {
        setup();
        stateOnline.entry();

        Mockito.verify(executor, times(1)).
                schedule(any(Runnable.class), eq(GlobalConstants.REGISTRATION_RETRY_MAX_TIME), eq(TimeUnit.MILLISECONDS));

        stateOnline.coarseTimerExpired();
        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.OFFLINE, peeper.peepState(su));
    }

    @Test
    public void test_online_ptt_pressed() throws Exception {
        setup();
        stateOnline.entry();

        stateOnline.ptt(true);
        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.TX_INIT, peeper.peepState(su));
        assertEquals(config.mTgtid, peeper.peepCallInfo(su).mTargetId);
        assertEquals(config.mSuid, peeper.peepCallInfo(su).mSourceId);
    }

    @Test
    public void test_online_rxed_callInit() throws Exception {
        setup();
        stateOnline.entry();

        long tgt = 1000, src = 200;
        short seq = 20;
        CallInit callInit = new CallInit(tgt, src, seq);
        ByteBuffer payload = ByteBuffer.allocate(callInit.getSize());
        callInit.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateOnline.packetReceived(pkt);
        SubscriberPeeper peeper= new SubscriberPeeper();
        assertEquals(State.RX, peeper.peepState(su));

        assertEquals(tgt, peeper.peepCallInfo(su).mTargetId);
        assertEquals(src, peeper.peepCallInfo(su).mSourceId);
    }

    @Test
    public void test_online_rxed_callTerm() throws Exception {
        setup();
        stateOnline.entry();

        long tgt = 1000, src = 200;
        short seq = 20;
        short countdown = 9;
        CallTerm callTerm = new CallTerm(tgt, src, seq, countdown);
        ByteBuffer payload = ByteBuffer.allocate(callTerm.getSize());
        callTerm.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateOnline.packetReceived(pkt);
        SubscriberPeeper peeper= new SubscriberPeeper();
        assertEquals(State.CALL_HANG, peeper.peepState(su));

        assertEquals(tgt, peeper.peepCallInfo(su).mTargetId);
        assertEquals(src, peeper.peepCallInfo(su).mSourceId);
        assertEquals(countdown, peeper.peepCountdown(su));
    }

    @Test
    public void test_online_rxed_callData() throws Exception {
        setup();
        stateOnline.entry();

        long tgt = 1000, src = 200;
        short seq = 20;
        ByteBuffer audio = ByteBuffer.allocate(20);
        CallData callData = new CallData(tgt, src, seq, audio);
        ByteBuffer payload = ByteBuffer.allocate(callData.getSize());
        callData.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateOnline.packetReceived(pkt);

        Mockito.verify(spkr, times(1)).offer(any(ByteBuffer.class), eq(seq));

        SubscriberPeeper peeper= new SubscriberPeeper();
        assertEquals(State.RX, peeper.peepState(su));
        assertEquals(tgt, peeper.peepCallInfo(su).mTargetId);
        assertEquals(src, peeper.peepCallInfo(su).mSourceId);
    }

}
