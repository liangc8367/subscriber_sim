package com.bluesky;

import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.OLog;
import com.bluesky.common.UDPService;

import java.net.DatagramPacket;

/**
 * Created by liangc on 01/02/15.
 */
public class Subscriber {
    public static class Configuration{
        public long mSuid;
    }

    public Subscriber(Configuration config, DataSource mic, DataSink spkr, UDPService udpService, OLog logger){
        mConfig = config;
        mMic = mic;
        mSpkr = spkr;
        mUdpSvc = udpService;
        mLogger = logger;
    }

    public void start(){

    }

    public void stop(){

    }

    /**
     *
     * @param pressed true if pressed, otherwise released
     */
    public void ptt(boolean pressed){

    }

    public void timerExpired(NamedTimerTask timerTask){

    }

    public void packetReceived(DatagramPacket packet){

    }






    private Configuration mConfig;
    private DataSource mMic;
    private DataSink mSpkr;
    private UDPService mUdpSvc;
    private OLog mLogger;

    /** private methods and members */
    private enum State {
        OFFLINE,
        ONLINE,
        RX,
        HANG,
        TX
    }

    private class StateNode {
        void entry(){};
        void exit(){};
        void ptt(boolean pressed){};
        public void timerExpired(NamedTimerTask timerTask){}
        public void packetReceived(DatagramPacket packet){}
    }

    private class StateOffline extends StateNode{
        void entry(){};
        void exit(){};
    }

    private class StateOnline extends StateNode{
        void entry(){};
        void exit(){};
    }

}
