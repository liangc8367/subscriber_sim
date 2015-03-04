package test.com.bluesky.core;

import com.bluesky.DataSink;
import com.bluesky.DataSource;
import com.bluesky.common.GlobalConstants;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.OLog;
import com.bluesky.common.UDPService;
import com.bluesky.core.Subscriber;
import com.bluesky.core.SubscriberExecContext;
import com.bluesky.protocol.*;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.AdditionalMatchers.gt;
import static org.mockito.AdditionalMatchers.leq;
import static org.mockito.Mockito.*;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.DatagramPacket;
import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * Subscriber Offline test Tester.
 *
 * @author <Authors name>
 * @since <pre>Feb 27, 2015</pre>
 * @version 1.0
 */
@RunWith(MockitoJUnitRunner.class)
public class SubscriberOnline2TxInitTest {

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

    private static class StateListener implements Subscriber.SubscriberStateListener{
        @Override
        public void stateChanged(Subscriber.State newState){
            mState = newState;
        }
        public Subscriber.State mState;
    }

    private static class IsExpectedPayload extends ArgumentMatcher {
        public IsExpectedPayload(long target, long source, Class cls){
            this.target = target;
            this.source = source;
            this.cls = cls;
        }
        public boolean matches(Object payload) {
            ByteBuffer pload = (ByteBuffer) ((Buffer) payload).flip();
            ProtocolBase proto = ProtocolFactory.getProtocol(pload);
            return (proto.getTarget() == target && proto.getSource() == source && proto.getClass() == cls);
        }

        @Override
        public void describeTo(org.hamcrest.Description description){
            description.appendText("Expected payload: " + cls + ":" + "target=" + target + ", source=" + source);
        }

        private long target, source;
        private Class cls;
    }

    private void resetMocks(){
        Mockito.reset(udpService);
        Mockito.reset(execCtx);
    }

    @Test
    public void test_Sub_offline() throws Exception {
        Subscriber.Configuration config = new Subscriber.Configuration();
        config.mSuid = 100;
        config.mTgtid = 1000;

        NamedTimerTask timerTask = new NamedTimerTask(20) {
            @Override
            public void run() {

            }
        };

        stub(execCtx.createTimerTask()).toReturn(timerTask);

        Subscriber su = new Subscriber(config, execCtx, mic, spkr, udpService, logger);

        StateListener stateListener = new StateListener();
        su.registerStateListener(stateListener);

        // offline => online
        su.start();
        su.timerExpired(timerTask);

        Registration reg = new Registration(GlobalConstants.SUID_TRUNK_MANAGER, config.mSuid, (short)2);
        Ack ack = new Ack(config.mSuid, GlobalConstants.SUID_TRUNK_MANAGER, (short)20, true, reg);
        ByteBuffer payload = ByteBuffer.allocate(ack.getSize());
        ack.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        su.packetReceived(pkt);

        assertEquals(stateListener.mState, Subscriber.State.ONLINE);

        //
        resetMocks();
        stub(execCtx.createTimerTask()).toReturn(timerTask);

        // ptt
        su.ptt(true);
        assertEquals(stateListener.mState, Subscriber.State.TXINIT);

        // verify it start to send callInit packets
//        Mockito.verify(execCtx, times(1)).createTimerTask();
//        Mockito.verify(execCtx, times(1)).schedule(any(NamedTimerTask.class),
//                and(gt(0L), leq(GlobalConstants.CALL_PACKET_INTERVAL)));
//        Mockito.verify(udpService, times(1)).send((ByteBuffer) argThat(
//                new IsExpectedPayload(config.mTgtid,
//                        config.mSuid,
//                        CallInit.class)));

        su.timerExpired(timerTask);

//        Mockito.verify(execCtx, times(2)).createTimerTask();
//        Mockito.verify(execCtx, times(2)).schedule(any(NamedTimerTask.class),
//                leq(GlobalConstants.CALL_PACKET_INTERVAL));
//        Mockito.verify(execCtx, times(2)).schedule(any(NamedTimerTask.class),
//                and(gt(0L), leq(GlobalConstants.CALL_PACKET_INTERVAL * 2))); //<== important, coz mock is not real-time
//        Mockito.verify(udpService, times(2)).send((ByteBuffer) argThat(
//                new IsExpectedPayload(config.mTgtid,
//                        config.mSuid,
//                        CallInit.class)));

        // verify a callInit from trunk mgr will trigger SU to txing state, AND that callInit must
        // came from the SU itself
        CallInit callInit = new CallInit(GlobalConstants.SUID_TRUNK_MANAGER, config.mSuid, (short)3);
        payload = ByteBuffer.allocate(callInit.getSize());
        callInit.serialize(payload);
        pkt = new DatagramPacket(payload.array(), payload.capacity());

        su.packetReceived(pkt);

        assertEquals(stateListener.mState, Subscriber.State.TXINIT);

        callInit = new CallInit(config.mTgtid, config.mSuid, (short)3);
        payload = ByteBuffer.allocate(callInit.getSize());
        callInit.serialize(payload);
        pkt = new DatagramPacket(payload.array(), payload.capacity());

        su.packetReceived(pkt);

        su.timerExpired(timerTask);

        su.timerExpired(timerTask);

        assertEquals(stateListener.mState, Subscriber.State.TX);


    }


}
