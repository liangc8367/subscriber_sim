package com.bluesky.core.subscriber;

import com.bluesky.common.GlobalConstants;
import com.bluesky.common.NamedTimerTask;
import com.bluesky.common.ProtocolHelpers;
import com.bluesky.protocol.CallData;
import com.bluesky.protocol.CallInit;
import com.bluesky.protocol.ProtocolBase;
import com.bluesky.protocol.ProtocolFactory;

import java.net.DatagramPacket;

/**
 * call init state:
 * - callInit: from myself, kept sending callInit until 3 callInit, and then transit to callTxing
 * - callInit: from others, to callRxing
 * - callTerm: to callhang
 * - callData: from others, to callRxing
 */
public class StateTxInit extends StateNode{
    public StateTxInit(Subscriber sub){
        super(sub);
    }
    
    @Override
    public void entry() {
        mSub.mLogger.d(mSub.TAG, "enter call init");
        mSub.mFirstPktTime = mSub.mExecCtx.currentTimeMillis();
        mSub.sendCallInit();
        mSub.mFirstPktSeqNumber = mSub.mSeqNumber;
        armTxTimer();
        mbChannelGranted = false;
    }

    @Override
    public void exit() {
        mSub.mLogger.d(mSub.TAG, "exit call init");
        if( mTxTimer != null){
            mTxTimer.cancel();
            mTxTimer = null;
        }
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
                mSub.mState = State.CALL_HANG;
                break;
        }
    }

    @Override
    public void timerExpired(NamedTimerTask timerTask) {
        if( timerTask == mTxTimer){
            int numSent = mSub.mSeqNumber + 1 - mSub.mFirstPktSeqNumber;
            if( numSent >= 3 ){
                mSub.mLogger.d(mSub.TAG, "we've sent " + numSent +" callInit" + ", channel granted=" + mbChannelGranted);
                if( mbChannelGranted ){
                    mSub.mState = State.TX;
                } else {
                    mSub.mState = State.ONLINE;
                }
            } else {
                mSub.mLogger.d(mSub.TAG, "tx timer timed out, " + numSent);
                mSub.sendCallInit();
                rearmTxTimer();
            }
        }
    }

    private void armTxTimer(){
        long timeNow = mSub.mExecCtx.currentTimeMillis();
        mTxTimer = mSub.mExecCtx.createTimerTask();
        long delay = GlobalConstants.CALL_PACKET_INTERVAL* (mSub.mSeqNumber + 1 - mSub.mFirstPktSeqNumber)
                - (int)((timeNow - mSub.mFirstPktTime));
        if(delay < 0) {
            mSub.mLogger.w(mSub.TAG, "negative delay:" + delay);
            // try to catch up
            timerExpired(mTxTimer);
        } else {
            mSub.mExecCtx.schedule(mTxTimer, delay);
        }
    }

    private void rearmTxTimer(){
        if(mTxTimer !=null){
            mTxTimer.cancel();
        }
        armTxTimer();
    }

    private NamedTimerTask mTxTimer;
    private boolean mbChannelGranted;
}
