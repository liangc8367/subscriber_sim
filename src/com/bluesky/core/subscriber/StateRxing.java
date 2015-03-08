package com.bluesky.core.subscriber;

import com.bluesky.common.ProtocolHelpers;
import com.bluesky.protocol.CallData;
import com.bluesky.protocol.ProtocolBase;
import com.bluesky.protocol.ProtocolFactory;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;

/** Rxing state,
 *  - callInit: ignore
 *  - callData: offer to spkr module
 *  - callTerm: offer to spkr module
 *  - rxEnd: transit to call hang
 *
 */
class StateRxing extends StateNode{
    public StateRxing(Subscriber sub){
        super(sub);
    }
    @Override
    public void entry() {
        mSub.mLogger.d(mSub.TAG, "enter Rxing");
        mSub.enableRxFastPath(true);
    }

    @Override
    public void exit() {
        mSub.mLogger.d(mSub.TAG, "exit Rxing");
        mSub.enableRxFastPath(false);
    }

    @Override
    public void packetReceived(DatagramPacket packet) {
        mSub.mLogger.d(mSub.TAG, "rxed: " + ProtocolHelpers.peepProtocol(packet));
        //TODO: to validate packet source and seq
        // validate target/src
        ProtocolBase proto = ProtocolFactory.getProtocol(packet);
        switch( proto.getType()){
            case ProtocolBase.PTYPE_CALL_INIT:
                mSub.mLogger.d(mSub.TAG, "rxed callInit");
                break;
            case ProtocolBase.PTYPE_CALL_DATA:
                mSub.mLogger.d(mSub.TAG, "rxed callData");
                mSub.mSpkr.offerData(((CallData) proto).getAudioData(), proto.getSequence());
                break;
            case ProtocolBase.PTYPE_CALL_TERM:
                mSub.mLogger.d(mSub.TAG, "rxed callTerm");
                ByteBuffer eof = ByteBuffer.allocate(0);
                mSub.mSpkr.offerData( eof, proto.getSequence());
                break;
        }
    }

    @Override
    public void rxEnd() {
        mSub.mLogger.d(mSub.TAG, "incoming call ended");
        mSub.mState = State.CALL_HANG;
    }

}
