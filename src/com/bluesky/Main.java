package com.bluesky;

import com.bluesky.common.GlobalConstants;
import com.bluesky.common.UDPService;
import com.bluesky.common.XLog;
import com.bluesky.core.Subscriber;
import com.bluesky.stubs.MicSim;
import com.bluesky.stubs.SpkrSim;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    /** syntax:
     *      simulator suid <config-xml-file>
     * @param args
     */
    public static void main(String[] args) {
        System.out.println("Subscriber simulator.");
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

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Subscriber.Configuration config = new Subscriber.Configuration();
        config.mSuid = suid;


        DataSource mic = new MicSim();
        DataSink spkr = new SpkrSim();
        //UDPService udpService, OLog logger

//        Subscriber su = new Subscriber(config, executor, mic, spkr, udpSvc, logger);
//        su.start();

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
