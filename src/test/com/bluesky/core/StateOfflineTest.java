package test.com.bluesky.core;

import com.bluesky.DataSink;
import com.bluesky.DataSource;
import com.bluesky.common.GlobalConstants;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.OLog;
import com.bluesky.common.UDPService;
import com.bluesky.core.subscriber.*;

import com.bluesky.protocol.Ack;
import com.bluesky.protocol.Registration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.*;
import org.mockito.runners.MockitoJUnitRunner;
import test.com.bluesky.core.helpers.PayloadMatcher;
import test.com.bluesky.core.helpers.SubscriberPeeper;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;

/**
 * Created by liangc on 08/03/15.
 */
@RunWith(MockitoJUnitRunner.class)
public class StateOfflineTest {
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
    StateOffline stateOffline;

    private void setup(){
        Mockito.reset(udpService);
        Mockito.reset(execCtx);
        stub(execCtx.createTimerTask()).toReturn(timerTask);

        config.mSuid = 100;
        su = new Subscriber(config, execCtx, mic, spkr, udpService, logger);
        stateOffline = new StateOffline(su);

    }

    @Test
    public void test_offline_send_reg() throws Exception {
        setup();

        stateOffline.entry();
        Mockito.verify(execCtx, times(1)).createTimerTask();
        Mockito.verify(execCtx, times(1)).schedule(any(NamedTimerTask.class), eq(GlobalConstants.REGISTRATION_RETRY_TIME));
        Mockito.verify(udpService, times(1)).send((ByteBuffer) argThat(
                new PayloadMatcher(GlobalConstants.SUID_TRUNK_MANAGER,
                        config.mSuid,
                        Registration.class)));
    }

    @Test
    public void test_offline_timer_expired() throws Exception {
        setup();
        stateOffline.entry();
        stateOffline.timerExpired(timerTask);
        Mockito.verify(execCtx, times(2)).createTimerTask();
        Mockito.verify(execCtx, times(2)).schedule(any(NamedTimerTask.class), eq(GlobalConstants.REGISTRATION_RETRY_TIME));
        Mockito.verify(udpService, times(2)).send((ByteBuffer) argThat(
                new PayloadMatcher(GlobalConstants.SUID_TRUNK_MANAGER,
                        config.mSuid,
                        Registration.class)));
    }

    @Test
    public void test_offline_rxed_ack() throws Exception {
        setup();
        stateOffline.entry();

        Registration reg = new Registration(GlobalConstants.SUID_TRUNK_MANAGER, config.mSuid, (short)2);
        Ack ack = new Ack(config.mSuid, GlobalConstants.SUID_TRUNK_MANAGER, (short)20, true, reg);
        ByteBuffer payload = ByteBuffer.allocate(ack.getSize());
        ack.serialize(payload);
        DatagramPacket pkt = new DatagramPacket(payload.array(), payload.capacity());

        stateOffline.packetReceived(pkt);

        SubscriberPeeper peeper = new SubscriberPeeper();
        assertEquals(State.ONLINE, peeper.peepState(su));
    }
}
