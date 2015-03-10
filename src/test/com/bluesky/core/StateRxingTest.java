package test.com.bluesky.core;

import com.bluesky.DataSink;
import com.bluesky.DataSource;
import com.bluesky.common.CallInformation;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.OLog;
import com.bluesky.common.UDPService;
import com.bluesky.core.subscriber.*;
import com.bluesky.protocol.CallData;
import com.bluesky.protocol.CallTerm;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import test.com.bluesky.core.helpers.SubscriberPeeper;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.stub;
import static org.mockito.Mockito.times;

/**
 *  - callData: offer to spkr module
 *  - callTerm: offer to spkr module
 *  - rxEnd: transit to call hang
 */
@RunWith(MockitoJUnitRunner.class)
public class StateRxingTest {
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
    StateRxing stateRxing;

    private void setup() throws Exception{
        Mockito.reset(udpService);
        Mockito.reset(execCtx);
        stub(execCtx.createTimerTask()).toReturn(timerTask);

        config.mSuid = 100;
        su = new Subscriber(config, execCtx, mic, spkr, udpService, logger);
        SubscriberPeeper peeper = new SubscriberPeeper();
        peeper.setState(su, State.RX);

        stateRxing = new StateRxing(su);
    }

    @Test
    public void test_rxing_rxed_callData_same_call() throws Exception {
        setup();

        long tgt = 1000, src = 200;
        short seq = 20;

        // setup present call information to Subscriber
        SubscriberPeeper peeper = new SubscriberPeeper();
        CallInformation callInfo = peeper.peepCallInfo(su);
        callInfo.mTargetId = tgt;
        callInfo.mSourceId = src;

        stateRxing.entry();

        //
        ByteBuffer audio = ByteBuffer.allocate(20);
        CallData callData = new CallData(tgt, src, seq, audio);
        ByteBuffer payload = ByteBuffer.allocate(callData.getSize());
        callData.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateRxing.packetReceived(pkt);
        Mockito.verify(spkr, times(1)).offerData(any(ByteBuffer.class), eq(seq));

    }

    @Test
    public void test_rxing_rxed_callData_diff_call() throws Exception {
        setup();

        long tgt = 1000, src = 200;
        short seq = 20;

        // setup present call information to Subscriber
        SubscriberPeeper peeper = new SubscriberPeeper();
        CallInformation callInfo = peeper.peepCallInfo(su);
        callInfo.mTargetId = tgt;
        callInfo.mSourceId = src;

        stateRxing.entry();

        //
        long alian = 2000;
        ByteBuffer audio = ByteBuffer.allocate(20);
        CallData callData = new CallData(alian, src, seq, audio);
        ByteBuffer payload = ByteBuffer.allocate(callData.getSize());
        callData.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateRxing.packetReceived(pkt);
        Mockito.verify(spkr, times(0)).offerData(any(ByteBuffer.class), eq(seq));

    }

    @Test
    public void test_rxing_rxed_callTerm_same_call() throws Exception {
        setup();

        long tgt = 1000, src = 200;
        short seq = 20;

        // setup present call information to Subscriber
        SubscriberPeeper peeper = new SubscriberPeeper();
        CallInformation callInfo = peeper.peepCallInfo(su);
        callInfo.mTargetId = tgt;
        callInfo.mSourceId = src;

        stateRxing.entry();

        //
        CallTerm callTerm = new CallTerm(tgt, src, seq);
        ByteBuffer payload = ByteBuffer.allocate(callTerm.getSize());
        callTerm.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateRxing.packetReceived(pkt);
        Mockito.verify(spkr, times(1)).offerData(any(ByteBuffer.class), eq(seq));

    }

    @Test
    public void test_rxing_rxed_callTerm_diff_call() throws Exception {
        setup();

        long tgt = 1000, src = 200;
        short seq = 20;

        // setup present call information to Subscriber
        SubscriberPeeper peeper = new SubscriberPeeper();
        CallInformation callInfo = peeper.peepCallInfo(su);
        callInfo.mTargetId = tgt;
        callInfo.mSourceId = src;

        stateRxing.entry();

        //
        long alian = 2000;
        CallTerm callTerm = new CallTerm(alian, src, seq);
        ByteBuffer payload = ByteBuffer.allocate(callTerm.getSize());
        callTerm.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateRxing.packetReceived(pkt);
        Mockito.verify(spkr, times(0)).offerData(any(ByteBuffer.class), eq(seq));
    }

    @Test
    public void test_rxing_rx_end() throws Exception {
        setup();
        stateRxing.entry();
        stateRxing.rxEnd();
        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.CALL_HANG, peeper.peepState(su));
    }
}
