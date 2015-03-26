package test.com.bluesky.core.subscriber;

import com.bluesky.core.dsp.SignalSink;
import com.bluesky.core.dsp.SignalSource;
import com.bluesky.common.*;
import com.bluesky.core.hal.ReferenceClock;
import com.bluesky.core.subscriber.*;
import com.bluesky.protocol.CallData;
import com.bluesky.protocol.CallInit;
import com.bluesky.protocol.CallTerm;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import test.com.bluesky.core.subscriber.helpers.PayloadMatcher;
import test.com.bluesky.core.subscriber.helpers.SubscriberPeeper;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
public class StateTxInitTest {
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
    StateTxInit stateTxInit;

    private void setup() throws Exception{
        Mockito.reset(udpService);
        Mockito.reset(executor);
        Mockito.reset(clock);
        when(clock.currentTimeMillis())
                .thenReturn(100L)
                .thenReturn(102L) // sent #1
                .thenReturn(122L) // sent #2
                .thenReturn(141L) // sent #3
                .thenReturn(161L); // fall to online
        config.mSuid = 100;
        config.mTgtid = 1000;
        su = new Subscriber(config, executor, mic, spkr, udpService, clock, logger);
        SubscriberPeeper peeper = new SubscriberPeeper();
        peeper.setState(su, State.TX_INIT);

        CallInformation callInfo = new CallInformation();
        callInfo.mSourceId = config.mSuid;
        callInfo.mTargetId = config.mTgtid;
        peeper.setCallInfo(su, callInfo);

        stateTxInit = new StateTxInit(su);
    }

    /*
     * - callInit: from myself, kept sending callInit until 3 callInit, and then transit to callTxing
 * - callInit: from others, to callRxing
 * - callTerm: to callhang
 * - callData: from others, to callRxing
     */

    @Test
    public void test_txinit_send_3_callInit_rxed_none_b4_20msx3() throws Exception {
        setup();

        stateTxInit.entry();

        stateTxInit.fineTimerExpired();
        stateTxInit.fineTimerExpired();

        Mockito.verify(executor, times(3)).
                schedule(any(Runnable.class), leq(GlobalConstants.CALL_PACKET_INTERVAL), eq(TimeUnit.MILLISECONDS));
        Mockito.verify(udpService, times(3)).send((ByteBuffer) argThat(
                new PayloadMatcher(
                        config.mTgtid,
                        config.mSuid,
                        CallInit.class)));
        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.TX_INIT, peeper.peepState(su));

        stateTxInit.fineTimerExpired();
        assertEquals(State.ONLINE, peeper.peepState(su));
    }

    @Test
    public void test_txinit_send_3_callInit_rxed_callInit_self_b4_20msx3() throws Exception {
        setup();

        stateTxInit.entry();

        stateTxInit.fineTimerExpired();
        stateTxInit.fineTimerExpired();

        short seq = 20;
        CallInit callInit = new CallInit(config.mTgtid, config.mSuid, seq);
        ByteBuffer payload = ByteBuffer.allocate(callInit.getSize());
        callInit.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());
        stateTxInit.packetReceived(pkt);

        stateTxInit.fineTimerExpired();

        Mockito.verify(executor, times(3)).
                schedule(any(Runnable.class), leq(GlobalConstants.CALL_PACKET_INTERVAL), eq(TimeUnit.MILLISECONDS));
        Mockito.verify(udpService, times(3)).send((ByteBuffer) argThat(
                new PayloadMatcher(
                        config.mTgtid,
                        config.mSuid,
                        CallInit.class)));

        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.TX, peeper.peepState(su));
    }

    @Test
    public void test_txinit_send_2_callInit_rxed_callInit_other() throws Exception {
        setup();

        stateTxInit.entry();

        stateTxInit.fineTimerExpired();

        long alian = 200;
        short seq = 20;
        CallInit callInit = new CallInit(config.mTgtid, alian, seq);
        ByteBuffer payload = ByteBuffer.allocate(callInit.getSize());
        callInit.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());
        stateTxInit.packetReceived(pkt);

        Mockito.verify(executor, times(2)).
                schedule(any(Runnable.class), leq(GlobalConstants.CALL_PACKET_INTERVAL), eq(TimeUnit.MILLISECONDS));
        Mockito.verify(udpService, times(2)).send((ByteBuffer) argThat(
                new PayloadMatcher(
                        config.mTgtid,
                        config.mSuid,
                        CallInit.class)));

        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.RX, peeper.peepState(su));
        assertEquals(peeper.peepCallInfo(su).mTargetId, config.mTgtid);
        assertEquals(peeper.peepCallInfo(su).mSourceId, alian);

    }

    @Test
    public void test_txinit_send_2_callInit_rxed_callData() throws Exception {
        setup();

        stateTxInit.entry();

        stateTxInit.fineTimerExpired();

        long alian = 200;
        short seq = 20;
        ByteBuffer audio = ByteBuffer.allocate(20);
        CallData callData = new CallData(config.mTgtid, alian, seq, audio);
        ByteBuffer payload = ByteBuffer.allocate(callData.getSize());
        callData.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateTxInit.packetReceived(pkt);

        Mockito.verify(executor, times(2)).
                schedule(any(Runnable.class), leq(GlobalConstants.CALL_PACKET_INTERVAL), eq(TimeUnit.MILLISECONDS));
        Mockito.verify(udpService, times(2)).send((ByteBuffer) argThat(
                new PayloadMatcher(
                        config.mTgtid,
                        config.mSuid,
                        CallInit.class)));

        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.RX, peeper.peepState(su));
        assertEquals(peeper.peepCallInfo(su).mTargetId, config.mTgtid);
        assertEquals(peeper.peepCallInfo(su).mSourceId, alian);

    }

    @Test
    public void test_txinit_send_2_callInit_rxed_callTerm() throws Exception {
        setup();

        stateTxInit.entry();

        stateTxInit.fineTimerExpired();

        long alian = 200;
        short seq = 20;
        short countdown = 9;
        CallTerm callTerm = new CallTerm(config.mTgtid, alian, seq, countdown);
        ByteBuffer payload = ByteBuffer.allocate(callTerm.getSize());
        callTerm.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());
        stateTxInit.packetReceived(pkt);

        Mockito.verify(executor, times(2)).
                schedule(any(Runnable.class), leq(GlobalConstants.CALL_PACKET_INTERVAL), eq(TimeUnit.MILLISECONDS));
        Mockito.verify(udpService, times(2)).send((ByteBuffer) argThat(
                new PayloadMatcher(
                        config.mTgtid,
                        config.mSuid,
                        CallInit.class)));

        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.CALL_HANG, peeper.peepState(su));
        assertEquals(config.mTgtid, peeper.peepCallInfo(su).mTargetId);
        assertEquals(alian, peeper.peepCallInfo(su).mSourceId);
        assertEquals(countdown, peeper.peepCountdown(su));

    }

    @Test
    public void test_txinit_send_2_callInit_ptt_released() throws Exception {
        setup();

        stateTxInit.entry();

        stateTxInit.fineTimerExpired();

        stateTxInit.ptt(false);

        Mockito.verify(executor, times(2)).
                schedule(any(Runnable.class), leq(GlobalConstants.CALL_PACKET_INTERVAL), eq(TimeUnit.MILLISECONDS));
        Mockito.verify(udpService, times(2)).send((ByteBuffer) argThat(
                new PayloadMatcher(
                        config.mTgtid,
                        config.mSuid,
                        CallInit.class)));

        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.TX_STOPPING, peeper.peepState(su));
        assertEquals(peeper.peepCallInfo(su).mTargetId, config.mTgtid);
        assertEquals(peeper.peepCallInfo(su).mSourceId, config.mSuid);
    }

}