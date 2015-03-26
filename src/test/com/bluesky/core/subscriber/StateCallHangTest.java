package test.com.bluesky.core.subscriber;

import com.bluesky.common.GlobalConstants;
import com.bluesky.core.dsp.SignalSink;
import com.bluesky.core.dsp.SignalSource;
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
public class StateCallHangTest {
    @Mock
    SignalSink spkr;
    @Mock
    SignalSource mic;
    @Mock
    UDPService udpService;
    @Mock
    OLog logger;
    @Mock
    ReferenceClock clock;
    @Mock
    ScheduledExecutorService executor;

    final Configuration config = new Configuration();
    final NamedTimerTask timerTask = new NamedTimerTask(20) {
        @Override
        public void run() {

        }
    };

    Subscriber su;
    StateCallHang stateCallHang;
    long tgt = 1000, src = 200;
    short orig_countdown = 20;

    private void setup() throws Exception {
        Mockito.reset(udpService);
        Mockito.reset(executor);

        config.mSuid = src;
        su = new Subscriber(config, executor, mic, spkr, udpService, clock, logger);
        SubscriberPeeper peeper = new SubscriberPeeper();
        peeper.setState(su, State.CALL_HANG);
        peeper.peepCallInfo(su).mSourceId = config.mSuid;
        peeper.peepCallInfo(su).mTargetId = tgt;
        peeper.setCountdown(su, orig_countdown);

        stateCallHang = new StateCallHang(su);
    }

    /*
 *  - callInit: to call rxing
 *  - callTerm: remain in call hang (trunking mgr should broadcase callTerm during this period)
 *  - callData: validate, and to call rxing
 *  - ptt: to callInit
 *  - timeout: to idle/online (after last hang period following last callTerm)
     */

    @Test
    public void test_callHang_ptt_pressed() throws Exception {
        setup();
        stateCallHang.entry();
        Mockito.verify(executor, times(1)).
                schedule(any(Runnable.class),
                        eq(orig_countdown * GlobalConstants.CALL_PACKET_INTERVAL),
                        eq(TimeUnit.MILLISECONDS) );
        stateCallHang.ptt(true);
        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.TX_INIT, peeper.peepState(su));
    }

    @Test
    public void test_callHang_rxed_callInit() throws Exception {
        setup();
        stateCallHang.entry();

        long alien = 300;
        short seq = 20;
        CallInit callInit = new CallInit(tgt, alien, seq);
        ByteBuffer payload = ByteBuffer.allocate(callInit.getSize());
        callInit.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateCallHang.packetReceived(pkt);
        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.RX, peeper.peepState(su));

        assertEquals(peeper.peepCallInfo(su).mTargetId, tgt);
        assertEquals(peeper.peepCallInfo(su).mSourceId, alien);
    }

    @Test
    public void test_callHang_rxed_self_callInit() throws Exception {
        setup();
        stateCallHang.entry();


        short seq = 20;
        CallInit callInit = new CallInit(tgt, src, seq);
        ByteBuffer payload = ByteBuffer.allocate(callInit.getSize());
        callInit.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateCallHang.packetReceived(pkt);
        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.CALL_HANG, peeper.peepState(su));

        assertEquals(peeper.peepCallInfo(su).mTargetId, tgt);
        assertEquals(peeper.peepCallInfo(su).mSourceId, src);
    }

    @Test
    public void test_callHang_rxed_callTerm() throws Exception {
        setup();
        stateCallHang.entry();

        long alien = 300;
        short seq = 20;
        short countdown = 9;
        CallTerm callTerm = new CallTerm(tgt, alien, seq, countdown);
        ByteBuffer payload = ByteBuffer.allocate(callTerm.getSize());
        callTerm.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateCallHang.packetReceived(pkt);
        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.CALL_HANG, peeper.peepState(su));

        assertEquals(peeper.peepCallInfo(su).mTargetId, tgt);
        assertEquals(peeper.peepCallInfo(su).mSourceId, alien);
        assertEquals(countdown, peeper.peepCountdown(su));

        Mockito.verify(executor, times(1)).
                schedule(any(Runnable.class),
                        eq(countdown * GlobalConstants.CALL_PACKET_INTERVAL),
                        eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void test_callHang_rxed_self_callTerm() throws Exception {
        setup();
        stateCallHang.entry();

        short seq = 20;
        short countdown = 9;
        CallTerm callTerm = new CallTerm(tgt, src, seq, countdown);
        ByteBuffer payload = ByteBuffer.allocate(callTerm.getSize());
        callTerm.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateCallHang.packetReceived(pkt);
        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.CALL_HANG, peeper.peepState(su));

        assertEquals(peeper.peepCallInfo(su).mTargetId, tgt);
        assertEquals(peeper.peepCallInfo(su).mSourceId, src);
        assertEquals(countdown, peeper.peepCountdown(su));

        Mockito.verify(executor, times(1)).
                schedule(any(Runnable.class),
                        eq(countdown * GlobalConstants.CALL_PACKET_INTERVAL),
                        eq(TimeUnit.MILLISECONDS) );

    }

    @Test
    public void test_callHang_rxed_callData() throws Exception {
        setup();
        stateCallHang.entry();

        long alien = 300;
        short seq = 20;
        ByteBuffer audio = ByteBuffer.allocate(20);
        CallData callData = new CallData(tgt, alien, seq, audio);
        ByteBuffer payload = ByteBuffer.allocate(callData.getSize());
        callData.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateCallHang.packetReceived(pkt);

        Mockito.verify(spkr, times(1)).offer(any(ByteBuffer.class), eq(seq));

        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.RX, peeper.peepState(su));
        assertEquals(peeper.peepCallInfo(su).mTargetId, tgt);
        assertEquals(peeper.peepCallInfo(su).mSourceId, alien);
    }

    @Test
    public void test_callHang_rxed_self_callData() throws Exception {
        setup();
        stateCallHang.entry();

        short seq = 20;
        ByteBuffer audio = ByteBuffer.allocate(20);
        CallData callData = new CallData(tgt, src, seq, audio);
        ByteBuffer payload = ByteBuffer.allocate(callData.getSize());
        callData.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateCallHang.packetReceived(pkt);

        Mockito.verify(spkr, times(0)).offer(any(ByteBuffer.class), eq(seq));

        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.CALL_HANG, peeper.peepState(su));
        assertEquals(peeper.peepCallInfo(su).mTargetId, tgt);
        assertEquals(peeper.peepCallInfo(su).mSourceId, src);
    }

    @Test
    public void test_callHang_guard_time_out() throws Exception {
        setup();
        stateCallHang.entry();

        stateCallHang.coarseTimerExpired();
        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.ONLINE, peeper.peepState(su));

    }
}