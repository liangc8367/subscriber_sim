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

/** call hang state
 *  - callInit: to call rxing
 *  - callTerm: remain in call hang (trunking mgr should broadcase callTerm during this period)
 *  - callData: validate, and to call rxing
 *  - ptt: to callInit
 *  - monitor callTerm from trunk-mgr, and go idle if countdown reaches 0.
 */
public class StateCallHang extends StateNode {
    public StateCallHang(Subscriber sub){
        super(sub);
    }

    @Override
    public void entry() {
        mSub.mLogger.d(mSub.TAG, "enter Hang");
        armTimer();
    }

    @Override
    public void exit() {
        mSub.mLogger.d(mSub.TAG, "exit Hang");
        mSub.cancelCoarseTimer();
    }

    @Override
    public void ptt(boolean pressed) {
        if(pressed) {
            mSub.mLogger.d(mSub.TAG, "ptt pressed");
            mSub.mState = State.TX_INIT;
        }
    }

    @Override
    public void coarseTimerExpired() {
        mSub.mLogger.d(mSub.TAG, "flywheel timed out");
        mSub.mState = State.ONLINE;
    }

    @Override
    public void packetReceived(DatagramPacket packet) {
        mSub.mLogger.d(mSub.TAG, "rxed: " + ProtocolHelpers.peepProtocol(packet));
        ProtocolBase proto = ProtocolFactory.getProtocol(packet);
        switch( proto.getType()){
            case ProtocolBase.PTYPE_CALL_INIT:
                if(proto.getSource() != mSub.mConfig.mSuid) {
                    mSub.mLogger.d(mSub.TAG, "rxed callInit");
                    mSub.recordCallInfo(proto.getTarget(), proto.getSource());
                    mSub.mState = State.RX;
                }
                break;
            case ProtocolBase.PTYPE_CALL_DATA:
                if(proto.getSource() != mSub.mConfig.mSuid) {
                    mSub.mLogger.d(mSub.TAG, "rxed callData");
                    mSub.recordCallInfo(proto.getTarget(), proto.getSource());
                    mSub.mSpkr.offer(((CallData) proto).getAudioData(), proto.getSequence());
                    mSub.mState = State.RX;
                }
                break;
            case ProtocolBase.PTYPE_CALL_TERM:
                mSub.recordCallInfo(proto.getTarget(), proto.getSource());
                mSub.mCallTermCountdown = ((CallTerm)proto).getCountdown();
                if( mSub.mCallTermCountdown == 0){
                    mSub.mLogger.d(mSub.TAG, "count down reached zero");
                    mSub.mState = State.ONLINE;
                } else {
                    mSub.cancelCoarseTimer();
                    armTimer();
                }
                break;
        }
    }

    private void armTimer(){
        mSub.mScheduledCoarseTimer = mSub.mExecutor.schedule(mSub.mCoarseTimer,
                mSub.mCallTermCountdown * GlobalConstants.CALL_PACKET_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

}
