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

/** tx stopping state, to send up to 3 call term, until rxed call-term from trunking mgr(src=0)
 *  - send  at most 3 call term, in 20ms interval
 *  - if rxed callTerm from trunking mgr(src==0), then transit to call hang
 *  - if didn't rxed callterm from trunking mgr after 3 interval, then transit to idle
 *  - if rxed following pkt from others (not self)
 *  - callInit: to rx:
 *  - callData: to rx:
 *  - callTerm: update countdown, and transit to callhang
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
        mSub.cancelFineTimer();
    }

    @Override
    public void fineTimerExpired() {
        int numSent = mSub.mSeqNumber - mSub.mLastPktSeqNumber;
        if( numSent >= 3 ){
            mSub.mLogger.d(mSub.TAG, "we've sent " + numSent +" callTerm");
            mSub.mState = State.ONLINE;
        } else {
            mSub.sendCallTerm();
            numSent = mSub.mSeqNumber - mSub.mLastPktSeqNumber;
            mSub.mLogger.d(mSub.TAG, "tx timer timed out, " + numSent);
            rearmTxTimer();
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
                mSub.mCallTermCountdown = ((CallTerm)proto).getCountdown();
                mSub.mState = State.CALL_HANG;
                break;
        }
    }

    @Override
    public void txEnd() {
        mSub.mLogger.d(mSub.TAG, "tx end");
    }

    private void armTxTimer(){
        long timeNow = mSub.mClock.currentTimeMillis();
        long delay = GlobalConstants.CALL_PACKET_INTERVAL* (mSub.mSeqNumber + 1 - mSub.mFirstPktSeqNumber)
                        + mSub.mFirstPktTime - timeNow;
        if(delay < 0) {
            mSub.mLogger.w(mSub.TAG, "negative delay:" + delay);
            delay = 1;
        }
        mSub.mScheduledFineTimer = mSub.mExecutor.
            schedule(mSub.mFineTimer, delay, TimeUnit.MILLISECONDS);
    }

    private void rearmTxTimer(){
        mSub.cancelFineTimer();
        armTxTimer();
    }

}
