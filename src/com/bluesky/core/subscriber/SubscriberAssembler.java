package com.bluesky.core.subscriber;

import com.bluesky.common.OLog;
import com.bluesky.common.UDPService;
import com.bluesky.core.dsp.SignalSink;
import com.bluesky.core.dsp.SignalSource;
import com.bluesky.core.connectivity.RxedPacketCourier;
import com.bluesky.core.dsp.proxy.SignalSinkProxyOnExecutor;
import com.bluesky.core.dsp.proxy.SignalSourceProxyOnExecutor;
import com.bluesky.core.hal.ReferenceClock;

import java.util.concurrent.ScheduledExecutorService;

/** factory to create/assemble Subscriber/mic/speaker/executor
 * Created by liangc on 14/03/15.
 */
public class SubscriberAssembler {
    /** assemble/create subcriber
     *
     * @param config
     * @param executor provides thread context
     * @param mic, pure mic, to operate through a proxy to execute its callbacks in executor
     * @param spkr, pure spkr, to operate through a proxy to execute its callbacks in executor
     * @param udpService
     * @param clock
     * @param logger
     * @return
     */
    public static Subscriber assemble(Configuration config,
                                      ScheduledExecutorService executor,
                                      SignalSource mic,
                                      SignalSink spkr,
                                      UDPService udpService,
                                      ReferenceClock clock,
                                      OLog logger)
    {
        SignalSourceProxyOnExecutor micProxy = new SignalSourceProxyOnExecutor(mic, executor);
        SignalSinkProxyOnExecutor spkrProxy = new SignalSinkProxyOnExecutor(spkr, executor);
        Subscriber su = new Subscriber(config, executor, micProxy, spkrProxy, udpService, clock, logger);

        RxedPacketCourier pktCourier = new RxedPacketCourier(su, executor);
        udpService.setCompletionHandler(pktCourier);

        return su;
    }
}
