package com.bluesky.core.subscriber;

import com.bluesky.common.NamedTimerTask;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;

/**
 * Created by liangc on 08/03/15.
 */

/** abstraction of state in Subscriber statemachine,
 *
 */
public class StateNode {
    public StateNode(Subscriber sub){
        this.mSub = sub;
    }

    public void entry(){}
    public void exit(){}
    public void ptt(boolean pressed){}
    public void timerExpired(NamedTimerTask timerTask){}
    public void packetReceived(DatagramPacket packet){}
    public void rxEnd(){}
    public void txEnd(){}
    public void micDataAvailable(ByteBuffer compressedAudio){}

    protected Subscriber mSub;
}
