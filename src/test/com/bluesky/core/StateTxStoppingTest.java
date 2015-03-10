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
public class StateTxStoppingTest {
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
    StateTxStopping stateTxStopping;

    private void setup() throws Exception{
        Mockito.reset(udpService);
        Mockito.reset(execCtx);
        stub(execCtx.createTimerTask()).toReturn(timerTask);
        when(execCtx.currentTimeMillis())
                .thenReturn(250L) // entry, for first packet@100L, seq=1, last audio seq = 8
                .thenReturn(261L) // sent #1
                .thenReturn(283L) // sent #2
                .thenReturn(301L); // sent #3
        config.mSuid = 100;
        config.mTgtid = 1000;
        su = new Subscriber(config, execCtx, mic, spkr, udpService, logger);
        SubscriberPeeper peeper = new SubscriberPeeper();
        peeper.setState(su, State.TX_STOPPING);

        stateTxStopping = new StateTxStopping(su);
    }

    /*
 *  - send 3 call term, in 20ms interval, and transit to call hang
 *  - callInit: to rx
 *  - callData: to rx
 *  - callTerm: to callhang
 *  - call proto from self: ignore
     */

    @Test
    public void test_txinit_send_3_callTerm() throws Exception {
        setup();

        SubscriberPeeper peeper = new SubscriberPeeper();
        peeper.setFirstCallSeq(su, (short)1);
        peeper.setFirstCallTime(su, (long)100);
        peeper.setSeqNumber(su, (short)8);

        stateTxStopping.entry();

        stateTxStopping.timerExpired(timerTask);
        stateTxStopping.timerExpired(timerTask);
        stateTxStopping.timerExpired(timerTask);

        Mockito.verify(execCtx, times(3)).createTimerTask();
        Mockito.verify(execCtx, times(3)).schedule(any(NamedTimerTask.class), leq(GlobalConstants.CALL_PACKET_INTERVAL));
        Mockito.verify(udpService, times(3)).send((ByteBuffer) argThat(
                new PayloadMatcher(
                        config.mTgtid,
                        config.mSuid,
                        CallTerm.class)));

        assertEquals(State.CALL_HANG, peeper.peepState(su));
    }

    @Test
    public void test_txinit_send_1_callTerm_rxed_callInit() throws Exception {
        setup();

        SubscriberPeeper peeper = new SubscriberPeeper();
        peeper.setFirstCallSeq(su, (short)1);
        peeper.setFirstCallTime(su, (long)100);
        peeper.setSeqNumber(su, (short)8);

        stateTxStopping.entry();

        stateTxStopping.timerExpired(timerTask); // to send callTerm

        long target=2000, source = 200;
        short seq = 20;
        CallInit callInit = new CallInit(target, source, seq);
        ByteBuffer payload = ByteBuffer.allocate(callInit.getSize());
        callInit.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());
        stateTxStopping.packetReceived(pkt);

        assertEquals(State.RX, peeper.peepState(su));
        assertEquals(target, peeper.peepCallInfo(su).mTargetId);
        assertEquals(source, peeper.peepCallInfo(su).mSourceId);
    }

    @Test
    public void test_txinit_send_1_callTerm_rxed_callTerm() throws Exception {
        setup();

        SubscriberPeeper peeper = new SubscriberPeeper();
        peeper.setFirstCallSeq(su, (short)1);
        peeper.setFirstCallTime(su, (long)100);
        peeper.setSeqNumber(su, (short)8);

        stateTxStopping.entry();

        stateTxStopping.timerExpired(timerTask); // to send callTerm

        long target=2000, source = 200;
        short seq = 20;
        CallTerm callTerm = new CallTerm(target, source, seq);
        ByteBuffer payload = ByteBuffer.allocate(callTerm.getSize());
        callTerm.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());
        stateTxStopping.packetReceived(pkt);

        assertEquals(State.CALL_HANG, peeper.peepState(su));
        assertEquals(target, peeper.peepCallInfo(su).mTargetId);
        assertEquals(source, peeper.peepCallInfo(su).mSourceId);
    }

    @Test
    public void test_txinit_send_1_callTerm_rxed_self_callInit() throws Exception {
        setup();

        SubscriberPeeper peeper = new SubscriberPeeper();
        peeper.setFirstCallSeq(su, (short)1);
        peeper.setFirstCallTime(su, (long)100);
        peeper.setSeqNumber(su, (short)8);

        peeper.peepCallInfo(su).mSourceId = config.mSuid;
        peeper.peepCallInfo(su).mTargetId = config.mTgtid;

        stateTxStopping.entry();

        stateTxStopping.timerExpired(timerTask); // to send callTerm

        short seq = 20;
        CallInit callInit = new CallInit(config.mTgtid, config.mSuid, seq);
        ByteBuffer payload = ByteBuffer.allocate(callInit.getSize());
        callInit.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());
        stateTxStopping.packetReceived(pkt);

        assertEquals(State.TX_STOPPING, peeper.peepState(su));
        assertEquals(config.mTgtid, peeper.peepCallInfo(su).mTargetId);
        assertEquals(config.mSuid, peeper.peepCallInfo(su).mSourceId);
    }
}