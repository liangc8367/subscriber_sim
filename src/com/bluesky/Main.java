package com.bluesky;

import com.bluesky.common.UDPService;
import com.bluesky.common.GlobalConstants;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    /** syntax:
     *      simulator suid <config-xml-file>
     * @param args
     */
    public static void main(String[] args) {
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
        UDPService udpSvc = new UDPService(udpSvcConfig, logger);
        udpSvc.startService();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        SubscriberSimulator.Configuration config = new SubscriberSimulator.Configuration();
        config.mSuid = suid;

        SubscriberSimulator su = new SubscriberSimulator(executor, config);
        su.start();

        try {
            while (true) {
                Thread.currentThread().sleep(1000);
            }
        }catch (InterruptedException e){
            System.out.println("Interrupted " + e);
        }

        su.stop();
        udpSvc.stopService();
    }
}
