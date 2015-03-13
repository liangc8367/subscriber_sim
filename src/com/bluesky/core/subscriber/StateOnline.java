package com.bluesky.core.subscriber;

import com.bluesky.common.GlobalConstants;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.ProtocolHelpers;
import com.bluesky.protocol.CallData;
import com.bluesky.protocol.ProtocolBase;
import com.bluesky.protocol.ProtocolFactory;

import java.net.DatagramPacket;

/** online state, idle
 *  - ptt pressed: transit to call init state
 *  - pkt rxed, callInit: transit to call rxing state
 *  - ptk rxed, callData: transit to call rxing state
 *  - pkt rxed, callTerm, transit to call hang state
 *  - ptt: to TxInit
 *
 *  - keepalive timer expired: return to offline
 *
 */
public class StateOnline extends StateNode {
    public StateOnline(Subscriber sub){
        super(sub);
    }
    public void entry(){
        mSub.mLogger.d(mSub.TAG, "entry Online");
        mKeepAliveTimer = mSub.mExecCtx.createTimerTask();
        mSub.mExecCtx.schedule(mKeepAliveTimer, GlobalConstants.REGISTRATION_RETRY_MAX_TIME);
    };
    public void exit(){
        mSub.mLogger.d(mSub.TAG, "exit Online");
        if(mKeepAliveTimer != null){
            mKeepAliveTimer.cancel();
            mKeepAliveTimer = null;
        }
    }

    @Override
    public void ptt(boolean pressed) {
        if(pressed) {
            mSub.mLogger.d(mSub.TAG, "ptt pressed");
            mSub.mState = State.TX_INIT;
        }
    }

    @Override
    public void packetReceived(DatagramPacket packet) {
        mSub.mLogger.d(mSub.TAG, "rxed: " + ProtocolHelpers.peepProtocol(packet));
        //TODO: to validate packet source and seq
        ProtocolBase proto = ProtocolFactory.getProtocol(packet);
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
                mSub.recordCallInfo(proto.getTarget(), proto.getSource());
                mSub.mState = State.CALL_HANG;
                break;
        }
    }

    public void timerExpired(NamedTimerTask timer){
        if( timer != mKeepAliveTimer ){
            return;
        }
        mSub.mLogger.d(mSub.TAG, "keepalive timed out");
        mSub.mState = State.OFFLINE;
    }

    private NamedTimerTask mKeepAliveTimer = null;
}
