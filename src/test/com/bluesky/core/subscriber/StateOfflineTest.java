package test.com.bluesky.core.subscriber;

import com.bluesky.core.dsp.SignalSink;
import com.bluesky.core.dsp.SignalSource;
import com.bluesky.common.GlobalConstants;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.OLog;
import com.bluesky.common.UDPService;
import com.bluesky.core.hal.ReferenceClock;
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
import test.com.bluesky.core.subscriber.helpers.PayloadMatcher;
import test.com.bluesky.core.subscriber.helpers.SubscriberPeeper;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by liangc on 08/03/15.
 */
@RunWith(MockitoJUnitRunner.class)
public class StateOfflineTest {
    @Mock
    SignalSink spkr;
    @Mock
    SignalSource mic;
    @Mock
    UDPService udpService;
    @Mock
    ScheduledExecutorService exectuor;
    @Mock
    OLog logger;
    @Mock
    ReferenceClock clock;

    final Configuration config = new Configuration();
    final NamedTimerTask timerTask = new NamedTimerTask(20) {
        @Override
        public void run() {

        }
    };

    Subscriber su;
    StateOffline stateOffline;

    private void setup() throws Exception{
        Mockito.reset(udpService);
        Mockito.reset(clock);
        Mockito.reset(exectuor);

        config.mSuid = 100;
        su = new Subscriber(config, exectuor, mic, spkr, udpService, clock, logger);
        SubscriberPeeper peeper = new SubscriberPeeper();
        peeper.setState(su, State.OFFLINE);

        stateOffline = new StateOffline(su);
    }

    @Test
    public void test_offline_send_reg() throws Exception {
        setup();

        stateOffline.entry();
        Mockito.verify(exectuor, times(1)).
                schedule(any(Runnable.class), eq(GlobalConstants.REGISTRATION_RETRY_TIME), eq(TimeUnit.MILLISECONDS));
        Mockito.verify(udpService, times(1)).send((ByteBuffer) argThat(
                new PayloadMatcher(GlobalConstants.SUID_TRUNK_MANAGER,
                        config.mSuid,
                        Registration.class)));
    }

    @Test
    public void test_offline_timer_expired() throws Exception {
        setup();
        stateOffline.entry();
        stateOffline.coarseTimerExpired();
        Mockito.verify(exectuor, times(2)).
                schedule(any(Runnable.class), eq(GlobalConstants.REGISTRATION_RETRY_TIME), eq(TimeUnit.MILLISECONDS));
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
