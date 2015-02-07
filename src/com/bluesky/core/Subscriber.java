package com.bluesky.core;

import com.bluesky.DataSink;
import com.bluesky.DataSource;
import com.bluesky.common.*;
import com.bluesky.protocol.*;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Timer;
import java.util.concurrent.ExecutorService;

/**
 * Created by liangc on 01/02/15.
 */
public class Subscriber {
    public static class Configuration{
        public long mSuid;
        public long mTgtid;
    }

    public Subscriber(Configuration config, ExecutorService exec, DataSource mic, DataSink spkr, UDPService udpService, OLog logger){
        mConfig = config;
        mMic = mic;
        mSpkr = spkr;
        mUdpSvc = udpService;
        mLogger = logger;
        mExecCtx = new SubscriberExecContext(this, exec, mLogger);
        initializeSM();
    }

    public void start(){
        mStateNode.entry();
    }

    public void stop(){

    }

    /**
     *
     * @param pressed true if pressed, otherwise released
     */
    public void ptt(boolean pressed){
        saveStateContext();
        mStateNode.ptt(pressed);
        updateStateContext();
    }

    public void timerExpired(NamedTimerTask timerTask){
        saveStateContext();
        mStateNode.timerExpired(timerTask);
        updateStateContext();
    }

    public void packetReceived(DatagramPacket packet){
        saveStateContext();
        mStateNode.packetReceived(packet);
        updateStateContext();
    }

    private void saveStateContext(){
        mStateOrig = mState;
    }
    private void updateStateContext(){
        if(mState != mStateOrig ){
            mStateNode.exit();
            mStateNode = mStateMap.get(mState);
            mStateNode.entry();
        }
    }

    /** send registration */
    private void sendRegistration(){
        Registration reg = new Registration();
        reg.setSequence(++mSeqNumber);
        reg.setSUID(mConfig.mSuid);
        ByteBuffer payload  = ByteBuffer.allocate(reg.getSize());
        reg.serialize(payload);
        mUdpSvc.send(payload);
    }

    /** start calling procedure */
    private void sendPreamble(){
        CallInit preamble = new CallInit(mConfig.mTgtid, mConfig.mSuid);
        preamble.setSequence(++mSeqNumber);
        ByteBuffer payload = ByteBuffer.allocate(preamble.getSize());
        preamble.serialize(payload);
        mUdpSvc.send(payload);
    }

    /** send compressed audio data */
    private void sendCompressedAudioData(ByteBuffer compressedAudio, short seq){
        CallData callData = new CallData(
                mConfig.mTgtid,
                mConfig.mSuid,
                seq,
                compressedAudio);
        callData.setSequence(++mSeqNumber);
        ByteBuffer payload = ByteBuffer.allocate(callData.getSize());
        callData.serialize(payload);
        mUdpSvc.send(payload);
    }

    /** send call terminator */
    private void sendCallTerminator( short audioSeq){
        CallTerm callTerm = new CallTerm(
                mConfig.mTgtid,
                mConfig.mSuid,
                audioSeq
        );
        callTerm.setSequence(++mSeqNumber);
        ByteBuffer payload = ByteBuffer.allocate(callTerm.getSize());
        callTerm.serialize(payload);
        mUdpSvc.send(payload);
    }



    Configuration mConfig;
    final DataSource mMic;
    final DataSink mSpkr;
    final UDPService mUdpSvc;
    final OLog mLogger;
    final SubscriberExecContext mExecCtx;


    final Timer mTimer = new Timer("SuTm");


    /** private methods and members */
    private enum State {
        OFFLINE,
        ONLINE,
        RX,
        HANG,
        TX
    }

    private class StateNode {
        public void entry(){};
        public void exit(){};
        public void ptt(boolean pressed){};
        public void timerExpired(NamedTimerTask timerTask){}
        public void packetReceived(DatagramPacket packet){}
    }

    /** initial/offline state, start registration process
     *
     */
    private class StateOffline extends StateNode{
        public void entry(){
            mLogger.d(TAG, "entry Offline");
            sendRegistration();
            mRegistrationTimer = mExecCtx.createTimerTask();
            mTimer.schedule(mRegistrationTimer, GlobalConstants.REGISTRATION_RETRY_TIME);
            ++mRegAttempts;
        };
        public void exit(){
            if( mRegistrationTimer != null ){
                mRegistrationTimer.cancel();
                mRegistrationTimer = null;
            }
            mLogger.d(TAG, "exit Offline");
        };

        public void timerExpired(NamedTimerTask timer){
            if( timer != mRegistrationTimer ){
                return;
            }
            sendRegistration();
            mRegistrationTimer = mExecCtx.createTimerTask();
            mTimer.schedule(mRegistrationTimer, GlobalConstants.REGISTRATION_RETRY_TIME);
            ++mRegAttempts;
        }

        public void packetReceived(DatagramPacket packet){
            //TODO: validate the sender
            mLogger.d(TAG, "rxed: " + ProtocolHelpers.peepProtocol(packet));
            short protoType = ProtocolBase.peepType(ByteBuffer.wrap(packet.getData()));
            if( protoType == ProtocolBase.PTYPE_ACK){
                Ack proto = (Ack)ProtocolFactory.getProtocol(ByteBuffer.wrap(packet.getData()));
                if(proto.getAckType() == Ack.ACKTYPE_POSITIVE){
                    //TODO: validate ack...
                     mLogger.i(TAG, "rxed registeration ack");
                    mState = State.ONLINE;
                }
            }
        }

        NamedTimerTask mRegistrationTimer = null;
        int mRegAttempts = 0;
    }


    private class StateOnline extends StateNode{
        public void entry(){
            mLogger.d(TAG, "entry Online");
        };
        public void exit(){
            mLogger.d(TAG, "exit Online");
        };
    }

    private void initializeSM(){
        StateNode aState;
        aState = new StateOffline();
        mStateMap.put(State.OFFLINE, aState);
        aState = new StateOnline();
        mStateMap.put(State.ONLINE, aState);

        mState = State.OFFLINE;
        mStateNode = mStateMap.get(mState);
    }

    short mSeqNumber = 0;
    State   mState, mStateOrig;
    StateNode mStateNode;
    final EnumMap<State, StateNode> mStateMap = new EnumMap<State, StateNode>(State.class);

    final static String TAG = "Su";
}
