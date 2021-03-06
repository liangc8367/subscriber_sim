package com.bluesky.core.subscriber;

import com.bluesky.common.GlobalConstants;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.ProtocolHelpers;
import com.bluesky.protocol.CallData;
import com.bluesky.protocol.CallTerm;
import com.bluesky.protocol.ProtocolBase;
import com.bluesky.protocol.ProtocolFactory;

import java.net.DatagramPacket;
import java.util.concurrent.TimeUnit;

/** online state, idle
 *  - ptt pressed: transit to call init state
 *  - pkt rxed, callInit: transit to call rxing state, except from self
 *  - ptk rxed, callData: transit to call rxing state, except from self
 *  - pkt rxed, callTerm, transit to call hang state, except from self
 *  - pkt rxed (from self)<== must be lingering pkt, response with callTerm (so trunk mgr can
 *              start callhang process
 *  - ptt: to TxInit (using default configuration)
 *
 *  - keepalive timer expired: return to offline
 *
 */
public class StateOnline extends StateNode {
    public StateOnline(Subscriber sub){
        super(sub);
    }

    @Override
    public void entry(){
        mSub.mLogger.d(mSub.TAG, "entry Online");
        mSub.mScheduledCoarseTimer = mSub.mExecutor.
                schedule(mSub.mCoarseTimer, GlobalConstants.REGISTRATION_RETRY_MAX_TIME, TimeUnit.MILLISECONDS);
    };

    @Override
    public void exit(){
        mSub.mLogger.d(mSub.TAG, "exit Online");
        mSub.cancelCoarseTimer();
    }

    @Override
    public void ptt(boolean pressed) {
        if(pressed) {
            mSub.mLogger.d(mSub.TAG, "ptt pressed");
            mSub.recordCallInfo(mSub.mConfig.mTgtid, mSub.mConfig.mSuid);
            mSub.mState = State.TX_INIT;
        }
    }

    @Override
    public void packetReceived(DatagramPacket packet) {
        mSub.mLogger.d(mSub.TAG, "rxed: " + ProtocolHelpers.peepProtocol(packet));
        //TODO: to validate packet source and seq
        ProtocolBase proto = ProtocolFactory.getProtocol(packet);
        if( proto.getSource() == mSub.mConfig.mSuid ){
            mSub.mLogger.d(mSub.TAG, "rxed self lingering pkt");
            mSub.sendCallTerm();
        }
        switch( proto.getType()){
            case ProtocolBase.PTYPE_CALL_INIT:
                mSub.mLogger.d(mSub.TAG, "rxed callInit");
                mSub.recordCallInfo(proto.getTarget(), proto.getSource());
                mSub.mState = State.RX;
                break;
            case ProtocolBase.PTYPE_CALL_DATA:
                mSub.mLogger.d(mSub.TAG, "rxed callData");
                mSub.recordCallInfo(proto.getTarget(), proto.getSource());
                mSub.mSpkr.offer(((CallData) proto).getAudioData(), proto.getSequence());
                mSub.mState = State.RX;
                break;
            case ProtocolBase.PTYPE_CALL_TERM:
                mSub.mLogger.d(mSub.TAG, "rxed callTerm");
                mSub.mCallTermCountdown = ((CallTerm)proto).getCountdown();
                mSub.recordCallInfo(proto.getTarget(), proto.getSource());
                mSub.mState = State.CALL_HANG;
                break;
        }
    }

    @Override
    public void coarseTimerExpired(){
        mSub.mLogger.d(mSub.TAG, "keepalive timed out");
        mSub.mState = State.OFFLINE;
    }

}
