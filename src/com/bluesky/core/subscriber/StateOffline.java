package com.bluesky.core.subscriber;

import com.bluesky.common.GlobalConstants;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.ProtocolHelpers;
import com.bluesky.protocol.Ack;
import com.bluesky.protocol.ProtocolBase;
import com.bluesky.protocol.ProtocolFactory;

import java.net.DatagramPacket;
import java.util.concurrent.TimeUnit;

/**
 * initial/offline state, start registration process
 */
public class StateOffline extends StateNode {
    public StateOffline(Subscriber sub){
        super(sub);
    }

    @Override
    public void entry(){
        mSub.mLogger.d(mSub.TAG, "entry Offline");
        mSub.sendRegistration();
        mSub.mScheduledCoarseTimer = mSub.mExecutor.
                schedule(mSub.mCoarseTimer, GlobalConstants.REGISTRATION_RETRY_TIME, TimeUnit.MILLISECONDS);
        ++mRegAttempts;
    };
    @Override
    public void exit(){
        mSub.cancelCoarseTimer();
        mRegAttempts = 0;
        mSub.mLogger.d(mSub.TAG, "exit Offline");
    };

    @Override
    public void coarseTimerExpired(){
        mSub.sendRegistration();
        mSub.mScheduledCoarseTimer = mSub.mExecutor.
                schedule(mSub.mCoarseTimer, GlobalConstants.REGISTRATION_RETRY_TIME, TimeUnit.MILLISECONDS);
        ++mRegAttempts;
    }

    @Override
    public void packetReceived(DatagramPacket packet){
        mSub.mLogger.d(mSub.TAG, "rxed: " + ProtocolHelpers.peepProtocol(packet));
        ProtocolBase proto = ProtocolFactory.getProtocol(packet);
        if( proto.getType() == ProtocolBase.PTYPE_ACK){
            Ack ack = (Ack) proto;
            if( ack.getAckType() == Ack.ACKTYPE_POSITIVE ){
                // TODO: validate source/target/seq of ack
                mSub.mLogger.i(mSub.TAG, "rxed registeration ack");
                mSub.mState = State.ONLINE;
            }
        }
    }

    private int mRegAttempts = 0;

}
