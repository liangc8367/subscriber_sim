package com.bluesky.core.subscriber;

import com.bluesky.common.GlobalConstants;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.ProtocolHelpers;
import com.bluesky.protocol.CallData;
import com.bluesky.protocol.ProtocolBase;
import com.bluesky.protocol.ProtocolFactory;

import java.net.DatagramPacket;

/** tx stopping state
 *  - send 3 call term, in 20ms interval, and transit to call hang
 *  - callInit: to rx
 *  - callData: to rx
 *  - callTerm: to callhang
 */
public class StateTxStopping extends StateNode {

    public StateTxStopping(Subscriber sub){
        super(sub);
    }
    @Override
    public void entry() {
        mSub.mLogger.d(mSub.TAG, "enter tx stopping");
        mSub.mLastPktSeqNumber = mSub.mSeqNumber;
        armTxTimer();
    }

    @Override
    public void exit() {
        mSub.mLogger.d(mSub.TAG, "exit tx stopping");
        if( mTxTimer != null){
            mTxTimer.cancel();
            mTxTimer = null;
        }
    }

    @Override
    public void timerExpired(NamedTimerTask timerTask) {
        if( timerTask == mTxTimer){
            mSub.sendCallTerm();
            int numSent = mSub.mSeqNumber - mSub.mLastPktSeqNumber;
            if( numSent >= 3 ){
                mSub.mLogger.d(mSub.TAG, "we've sent " + numSent +" callTerm");
                mSub.mState = State.CALL_HANG;
            } else {
                mSub.mLogger.d(mSub.TAG, "tx timer timed out, " + numSent);
                rearmTxTimer();
            }
        }
    }

    @Override
    public void packetReceived(DatagramPacket packet) {
        mSub.mLogger.d(mSub.TAG, "rxed: " + ProtocolHelpers.peepProtocol(packet));

        ProtocolBase proto = ProtocolFactory.getProtocol(packet);
        if( proto.getSource() == mSub.mConfig.mSuid ){
            mSub.mLogger.d(mSub.TAG, "rxed proto from myself");
            return;
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
                mSub.recordCallInfo(proto.getTarget(), proto.getSource());
                mSub.mState = State.CALL_HANG;
                break;
        }
    }

    @Override
    public void txEnd() {
        mSub.mLogger.d(mSub.TAG, "tx end");
    }

    private void armTxTimer(){
        long timeNow = mSub.mExecCtx.currentTimeMillis();
        mTxTimer = mSub.mExecCtx.createTimerTask();
        long delay = GlobalConstants.CALL_PACKET_INTERVAL* (mSub.mSeqNumber + 1 - mSub.mFirstPktSeqNumber)
                        + mSub.mFirstPktTime - timeNow;
        if(delay < 0) {
            mSub.mLogger.w(mSub.TAG, "negative delay:" + delay);
        }
        mSub.mExecCtx.schedule(mTxTimer, delay);
    }

    private void rearmTxTimer(){
        if(mTxTimer !=null){
            mTxTimer.cancel();
        }
        armTxTimer();
    }

    private NamedTimerTask mTxTimer;
}
