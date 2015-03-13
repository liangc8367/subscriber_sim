package com.bluesky.core.subscriber;

import com.bluesky.common.GlobalConstants;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.ProtocolHelpers;
import com.bluesky.protocol.CallData;
import com.bluesky.protocol.ProtocolBase;
import com.bluesky.protocol.ProtocolFactory;

import java.net.DatagramPacket;

/** call hang state
 *  - callInit: to call rxing
 *  - callTerm: remain in call hang (trunking mgr should broadcase callTerm during this period)
 *  - callData: validate, and to call rxing
 *  - ptt: to callInit
 *  - timeout: to idle/online (after last hang period following last callTerm)
 */
public class StateCallHang extends StateNode {
    public StateCallHang(Subscriber sub){
        super(sub);
    }

    @Override
    public void entry() {
        mSub.mLogger.d(mSub.TAG, "enter Hang");
        mCallHangGuardTimer = mSub.mExecCtx.createTimerTask();
        mSub.mExecCtx.schedule(mCallHangGuardTimer, GlobalConstants.CALL_HANG_PERIOD); //hmm... should be 20ms*x, x<5
    }

    @Override
    public void exit() {
        mSub.mLogger.d(mSub.TAG, "exit Hang");
        if( mCallHangGuardTimer!=null){
            mCallHangGuardTimer.cancel();
            mCallHangGuardTimer = null;
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
    public void timerExpired(NamedTimerTask timerTask) {
        if( timerTask == mCallHangGuardTimer){
            mSub.mLogger.d(mSub.TAG, "hang gard timed out");
            mSub.mState = State.ONLINE;
        }
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
                if(proto.getSource() != mSub.mConfig.mSuid) {
                    mSub.mLogger.d(mSub.TAG, "rxed callTerm");
                    mSub.recordCallInfo(proto.getTarget(), proto.getSource());
                }
                mCallHangGuardTimer.cancel();
                mCallHangGuardTimer = mSub.mExecCtx.createTimerTask();
                mSub.mExecCtx.schedule(mCallHangGuardTimer, GlobalConstants.CALL_HANG_PERIOD);
                break;
        }
    }

    private NamedTimerTask mCallHangGuardTimer;

}
