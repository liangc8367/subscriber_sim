package com.bluesky.core.subscriber;

import java.nio.ByteBuffer;

/** call Txing state
 *  - on data available: sent data
 *  - ptt release: stop tx, and transit to TxStopping
 *  - txEnd: tx stopped prior to ptt release: call hang
 */
class StateTx extends StateNode {
    public StateTx(Subscriber sub){
        super(sub);
    }
    @Override
    public void entry() {
        mSub.mLogger.d(mSub.TAG, "enter txing");
        mSub.mMic.start();
    }

    @Override
    public void exit() {
        mSub.mLogger.d(mSub.TAG, "exit txing");
        mSub.mMic.stop();
    }

    @Override
    public void ptt(boolean pressed) {
        if( !pressed ){
            mSub.mLogger.d(mSub.TAG, "ptt released");
            mSub.mState = State.TX_STOPPING;
        }
    }

    @Override
    public void txEnd() {
        mSub.mLogger.d(mSub.TAG, "tx end prior to ptt released");
        mSub.mState = State.CALL_HANG;
    }

    @Override
    public void micDataAvailable(ByteBuffer compressedAudio){
        mSub.sendCallData(compressedAudio);
    }
}
