package com.bluesky.main;

import com.bluesky.common.GlobalConstants;
import com.bluesky.common.UDPService;
import com.bluesky.common.XLog;
import com.bluesky.core.dsp.SignalSink;
import com.bluesky.core.dsp.SignalSource;
import com.bluesky.core.hal.ReferenceClock;
import com.bluesky.core.subscriber.Configuration;
import com.bluesky.core.dsp.stubs.DummyMic;
import com.bluesky.core.dsp.stubs.DummySpkr;
import com.bluesky.core.subscriber.Subscriber;
import com.bluesky.core.subscriber.SubscriberAssembler;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Main {

    /** syntax:
     *      simulator suid <config-xml-file>
     * @param args
     */
    public static void main(String[] args) {
        System.out.println("subscriber simulator.");
        if( args.length != 1){
            System.err.println("syntax: simulator [suid]");
            System.exit(-1);
        }

        long suid = -1;
        try{
            suid = Long.parseLong(args[0]);
        } catch (Exception e){
            System.err.println("Ex: " + e);
            System.exit(-1);
        }

        XLog logger = new XLog();

        UDPService.Configuration udpSvcConfig = new UDPService.Configuration();
        udpSvcConfig.addrLocal = new InetSocketAddress(0); //any local port
        udpSvcConfig.addrRemote = new InetSocketAddress(
                GlobalConstants.TRUNK_CENTER_ADDR,
                GlobalConstants.TRUNK_CENTER_PORT
                );
        udpSvcConfig.clientMode = true;
        UDPService udpSvc = new UDPService(udpSvcConfig, logger);
        udpSvc.startService();

        try {
            Thread.currentThread().sleep(1000); //
        }catch (InterruptedException e){
            System.out.println("Interrupted " + e);
            System.exit(-2);
        }

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        Configuration config = new Configuration();
        config.mSuid = suid;

        SignalSource mic = new DummyMic();
        SignalSink spkr = new DummySpkr();

        ReferenceClock clock =new ReferenceClock();

        Subscriber sub = SubscriberAssembler.assemble(config, executor, mic, spkr, udpSvc, clock, logger);
        SimpleUI ui = new SimpleUI(sub);
        sub.registerStateListener(ui);

        sub.start();

        try {
            while (true) {
                Thread.currentThread().sleep(1000);
            }
        }catch (InterruptedException e){
            System.out.println("Interrupted " + e);
        }

//        su.stop();
        udpSvc.stopService();
    }
}
