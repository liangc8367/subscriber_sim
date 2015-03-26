package com.bluesky.core.subscriber;

import com.bluesky.common.GlobalConstants;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.ProtocolHelpers;
import com.bluesky.protocol.*;

import java.net.DatagramPacket;
import java.util.concurrent.TimeUnit;

/**
 * call init state:
 * - callInit: from myself, kept sending callInit until 3 callInit, and then transit to callTxing
 * - callInit: from others, to callRxing
 * - callTerm: to callhang
 * - callData: from others, to callRxing
 * - ptt release: send callTerm, and to callhang
 */
public class StateTxInit extends StateNode{
    public StateTxInit(Subscriber sub){
        super(sub);
    }
    
    @Override
    public void entry() {
        mSub.mLogger.d(mSub.TAG, "enter call init");
        mSub.mFirstPktTime = mSub.mClock.currentTimeMillis();
        mSub.sendCallInit();
        mSub.mFirstPktSeqNumber = mSub.mSeqNumber;
        armTxTimer();
        mbChannelGranted = false;
    }

    @Override
    public void exit() {
        mSub.mLogger.d(mSub.TAG, "exit call init");
        mSub.cancelFineTimer();
    }

    @Override
    public void ptt(boolean pressed) {
        mSub.mLogger.d(mSub.TAG, "ptt released");
        mSub.mState = State.TX_STOPPING;
    }

    @Override
    public void packetReceived(DatagramPacket packet) {
        mSub.mLogger.d(mSub.TAG, "rxed: " + ProtocolHelpers.peepProtocol(packet));
        //TODO: to validate packet source and seq
        // validate target/src
        ProtocolBase proto = ProtocolFactory.getProtocol(packet);
        switch( proto.getType()){
            case ProtocolBase.PTYPE_CALL_INIT:
                CallInit callInit = (CallInit) proto;
                if( callInit.getSource() == mSub.mConfig.mSuid ){
                    if( callInit.getTarget() == mSub.mConfig.mTgtid ) {
                        mSub.mLogger.d(mSub.TAG, "rxed callInit, initiated from myself");
                        mbChannelGranted = true;
                    }
                } else {
                    mSub.mLogger.d(mSub.TAG, "rxed callInit from other");
                    mSub.recordCallInfo(proto.getTarget(), proto.getSource());
                    mSub.mState = State.RX;
                }
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
    public void fineTimerExpired() {
        int numSent = mSub.mSeqNumber + 1 - mSub.mFirstPktSeqNumber;
        if( numSent >= 3 ){
            mSub.mLogger.d(mSub.TAG, "we've sent " + numSent +" callInit" + ", channel granted=" + mbChannelGranted);
            if( mbChannelGranted ){
                mSub.mState = State.TX;
            } else {
                mSub.mState = State.ONLINE;
            }
        } else {
            mSub.sendCallInit();
            numSent = mSub.mSeqNumber + 1 - mSub.mFirstPktSeqNumber;
            mSub.mLogger.d(mSub.TAG, "tx timer timed out, " + numSent);
            rearmTxTimer();
        }
    }

    private void armTxTimer(){
        long timeNow = mSub.mClock.currentTimeMillis();
        long delay = GlobalConstants.CALL_PACKET_INTERVAL* (mSub.mSeqNumber + 1 - mSub.mFirstPktSeqNumber)
                - (int)((timeNow - mSub.mFirstPktTime));
        if(delay < 0) {
            mSub.mLogger.w(mSub.TAG, "negative delay:" + delay);
            delay = 1;
        } else {
            mSub.mScheduledFineTimer = mSub.mExecutor.schedule(mSub.mFineTimer, delay, TimeUnit.MILLISECONDS);
        }
    }

    private void rearmTxTimer(){
        mSub.cancelFineTimer();
        armTxTimer();
    }

    private boolean mbChannelGranted;
}
