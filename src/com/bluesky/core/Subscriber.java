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

/** subscriber state machine, all methods should be called in the same thread context.
 *
 * Created by liangc on 01/02/15.
 */
public class Subscriber {
    public static class Configuration{
        public long mSuid;
        public long mTgtid;
    }

    public static enum State {
        OFFLINE,
        ONLINE,
        RX,
        HANG,
        TX,
        TXINIT,
        TXSTOPPING,
    }

    public interface SubscriberStateListener {
        public void stateChanged(State newState);
    }

    public Subscriber(Configuration config, SubscriberExecContext execCtx, /*ExecutorService exec,*/ DataSource mic, DataSink spkr, UDPService udpService, OLog logger){
        mConfig = config;
        mMic = mic;
        mSpkr = spkr;
        mUdpSvc = udpService;
        mLogger = logger;
        mExecCtx = execCtx; //new SubscriberExecContext(this, exec,mUdpSvc, mLogger);
        initializeSM();
    }

    public void start(){
        mStateNode.entry();
    }

    public void stop(){

    }

    public void registerStateListener( SubscriberStateListener listener){
        mStateListener = listener;
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

    /** end of Mic path, indicating that Mic path returned to its uninitialized state.
     *
     */
    public void txEnd(){
        saveStateContext();
        mStateNode.txEnd();
        updateStateContext();
    }

    /** end of Spkr path, indicating that Spkr path received callTerm or empty of jitter buffer
     *
     */
    public void rxEnd(){
        saveStateContext();
        mStateNode.rxEnd();
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
            if(mStateListener != null){
                mStateListener.stateChanged(mState);
            }
        }
    }

    /** send registration */
    private void sendRegistration(){
        Registration reg = new Registration(GlobalConstants.SUID_TRUNK_MANAGER, mConfig.mSuid, ++mSeqNumber);
        ByteBuffer payload  = ByteBuffer.allocate(reg.getSize());
        reg.serialize(payload);
        mUdpSvc.send(payload);
    }

    /** start calling procedure */
    private void sendCallInit(){
        CallInit preamble = new CallInit(mConfig.mTgtid, mConfig.mSuid, ++mSeqNumber);
        ByteBuffer payload = ByteBuffer.allocate(preamble.getSize());
        preamble.serialize(payload);
        mUdpSvc.send(payload);
    }

    /** send compressed audio data */
    private void sendCallData(ByteBuffer compressedAudio){
        CallData callData = new CallData(
                mConfig.mTgtid,
                mConfig.mSuid,
                ++mSeqNumber,
                compressedAudio);
        ByteBuffer payload = ByteBuffer.allocate(callData.getSize());
        callData.serialize(payload);
        mUdpSvc.send(payload);
    }

    /** send call terminator */
    private void sendCallTerm(){
        CallTerm callTerm = new CallTerm(
                mConfig.mTgtid,
                mConfig.mSuid,
                ++mSeqNumber
        );
        ByteBuffer payload = ByteBuffer.allocate(callTerm.getSize());
        callTerm.serialize(payload);
        mUdpSvc.send(payload);
    }

    /** enable/disable packet receiving fast path, i.e. route received callData/callTerm to Spkr module directly
     *
     */
    private void enableRxFastPath(boolean enable){
        //TODO:
    }

    private Configuration mConfig;
    private final DataSource mMic;
    private final DataSink mSpkr;
    private final UDPService mUdpSvc;
    private final OLog mLogger;
    private final SubscriberExecContext mExecCtx;
    private SubscriberStateListener mStateListener = null;


//    final Timer mTimer = new Timer("SuTm");


    /** private methods and members */
    private class StateNode {
        public void entry(){};
        public void exit(){};
        public void ptt(boolean pressed){};
        public void timerExpired(NamedTimerTask timerTask){}
        public void packetReceived(DatagramPacket packet){}
        public void rxEnd(){}
        public void txEnd(){}
        public void micDataAvailable(ByteBuffer compressedAudio){}
    }

    /** initial/offline state, start registration process
     *
     */
    private class StateOffline extends StateNode{
        public void entry(){
            mLogger.d(TAG, "entry Offline");
            sendRegistration();
            mRegistrationTimer = mExecCtx.createTimerTask();
//            mTimer.schedule(mRegistrationTimer, GlobalConstants.REGISTRATION_RETRY_TIME);
            mExecCtx.schedule(mRegistrationTimer, GlobalConstants.REGISTRATION_RETRY_TIME);
            ++mRegAttempts;
        };
        public void exit(){
            if( mRegistrationTimer != null ){
                mRegistrationTimer.cancel();
                mRegistrationTimer = null;
            }
            mRegAttempts = 0;
            mLogger.d(TAG, "exit Offline");
        };

        public void timerExpired(NamedTimerTask timer){
            if( timer != mRegistrationTimer ){
                return;
            }
            sendRegistration();
            mRegistrationTimer = mExecCtx.createTimerTask();
//            mTimer.schedule(mRegistrationTimer, GlobalConstants.REGISTRATION_RETRY_TIME);
            mExecCtx.schedule(mRegistrationTimer, GlobalConstants.REGISTRATION_RETRY_TIME);
            ++mRegAttempts;
        }

        public void packetReceived(DatagramPacket packet){
            mLogger.d(TAG, "rxed: " + ProtocolHelpers.peepProtocol(packet));
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            if( proto.getType() == ProtocolBase.PTYPE_ACK){
                Ack ack = (Ack) proto;
                if( ack.getAckType() == Ack.ACKTYPE_POSITIVE ){
                    // TODO: validate source/target/seq of ack
                    mLogger.i(TAG, "rxed registeration ack");
                    mState = State.ONLINE;
                }
            }
        }

        private NamedTimerTask mRegistrationTimer = null;
        private int mRegAttempts = 0;
    }


    /** online state, idle
     *  - ptt pressed: transit to call init state
     *  - pkt rxed, callInit: transit to call rxing state
     *  - ptk rxed, callData: transit to call rxing state
     *  - pkt rxed, callTerm, transit to call hang state
     *
     *  - keepalive timer
     *
     */
    private class StateOnline extends StateNode{
        public void entry(){
            mLogger.d(TAG, "entry Online");
            mKeepAliveTimer = mExecCtx.createTimerTask();
//            mTimer.schedule(mRegistrationTimer, GlobalConstants.REGISTRATION_RETRY_TIME);
            mExecCtx.schedule(mKeepAliveTimer, GlobalConstants.REGISTRATION_RETRY_MAX_TIME);
            ++mKeepAliveAttempts;
        };
        public void exit(){
            mLogger.d(TAG, "exit Online");
            if(mKeepAliveTimer != null){
                mKeepAliveTimer.cancel();
                mKeepAliveTimer = null;
            }
            mKeepAliveAttempts = 0;
        }

        @Override
        public void ptt(boolean pressed) {
            if(pressed) {
                mLogger.d(TAG, "ptt pressed");
                mState = State.TXINIT;
            }
        }

        @Override
        public void packetReceived(DatagramPacket packet) {
            mLogger.d(TAG, "rxed: " + ProtocolHelpers.peepProtocol(packet));
            //TODO: to validate packet source and seq
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            switch( proto.getType()){
                case ProtocolBase.PTYPE_CALL_INIT:
                    mLogger.d(TAG, "rxed callInit");
                    mState = State.RX;
                    break;
                case ProtocolBase.PTYPE_CALL_DATA:
                    mLogger.d(TAG, "rxed callData");
                    mSpkr.offerData(((CallData) proto).getAudioData(), proto.getSequence());
                    mState = State.RX;
                    break;
                case ProtocolBase.PTYPE_CALL_TERM:
                    mLogger.d(TAG, "rxed callTerm");
                    mState = State.HANG;
                    break;
                case ProtocolBase.PTYPE_ACK:
                    Ack ack = (Ack) proto;
                    if( ack.getAckType() == Ack.ACKTYPE_POSITIVE ){
                        // TODO: validate source/target/seq of ack
                        mLogger.i(TAG, "rxed registeration ack");
                        sendRegistration();
                        if(mKeepAliveTimer !=null){
                            mKeepAliveTimer.cancel();
                        }
                        mKeepAliveTimer = mExecCtx.createTimerTask();
                        mExecCtx.schedule(mKeepAliveTimer, GlobalConstants.REGISTRATION_RETRY_TIME);
                        ++mKeepAliveAttempts;
                    }
                    break;
            }
        }

        public void timerExpired(NamedTimerTask timer){
            if( timer != mKeepAliveTimer ){
                return;
            }
            mLogger.d(TAG, "keepalive timed out");
            mState = State.OFFLINE;
        }

        private NamedTimerTask mKeepAliveTimer = null;
        private int mKeepAliveAttempts = 0;
    }

    /** Rxing state,
     *  - callInit: ignore
     *  - callData: offer to spkr module
     *  - callTerm: offer to spkr module
     *  - rxEnd: transit to call hang
     *
     */
    private class StateRxing extends StateNode {

        @Override
        public void entry() {
            mLogger.d(TAG, "enter Rxing");
            enableRxFastPath(true);
        }

        @Override
        public void exit() {
            mLogger.d(TAG, "exit Rxing");
            enableRxFastPath(false);
        }

        @Override
        public void packetReceived(DatagramPacket packet) {
            mLogger.d(TAG, "rxed: " + ProtocolHelpers.peepProtocol(packet));
            //TODO: to validate packet source and seq
            // validate target/src
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            switch( proto.getType()){
                case ProtocolBase.PTYPE_CALL_INIT:
                    mLogger.d(TAG, "rxed callInit");
                    break;
                case ProtocolBase.PTYPE_CALL_DATA:
                    mLogger.d(TAG, "rxed callData");
                    mSpkr.offerData(((CallData) proto).getAudioData(), proto.getSequence());
                    break;
                case ProtocolBase.PTYPE_CALL_TERM:
                    mLogger.d(TAG, "rxed callTerm");
                    ByteBuffer eof = ByteBuffer.allocate(0);
                    mSpkr.offerData( eof, proto.getSequence());
                    break;
            }
        }

        @Override
        public void rxEnd() {
            mLogger.d(TAG, "incoming call ended");
            mState = State.HANG;
        }
    }

    /** call hang state
     *  - callInit: to call rxing
     *  - callTerm: remain in call hang (trunking mgr should broadcase callTerm during this period)
     *  - callData: validate, and to call rxing
     *  - ptt: to callInit
     *  - timeout: to idle/online (after last hang period following last callTerm)
     */
    private class StateHang extends StateNode {

        @Override
        public void entry() {
            mLogger.d(TAG, "enter Hang");
            mCallHangGuardTimer = mExecCtx.createTimerTask();
            mExecCtx.schedule(mCallHangGuardTimer, GlobalConstants.CALL_HANG_PERIOD); //hmm... should be 20ms*x, x<5
        }

        @Override
        public void exit() {
            mLogger.d(TAG, "exit Hang");
            if( mCallHangGuardTimer!=null){
                mCallHangGuardTimer.cancel();
                mCallHangGuardTimer = null;
            }
        }

        @Override
        public void ptt(boolean pressed) {
            if(pressed) {
                mLogger.d(TAG, "ptt pressed");
                mState = State.TXINIT;
            }
        }

        @Override
        public void timerExpired(NamedTimerTask timerTask) {
            if( timerTask == mCallHangGuardTimer){
                mLogger.d(TAG, "hang gard timed out");
                mState = State.ONLINE;
            }
        }

        @Override
        public void packetReceived(DatagramPacket packet) {
            mLogger.d(TAG, "rxed: " + ProtocolHelpers.peepProtocol(packet));
            //TODO: to validate packet source and seq
            // validate target/src
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            switch( proto.getType()){
                case ProtocolBase.PTYPE_CALL_INIT:
                    mLogger.d(TAG, "rxed callInit");
                    mState = State.RX;
                    break;
                case ProtocolBase.PTYPE_CALL_DATA:
                    mLogger.d(TAG, "rxed callData");
                    mSpkr.offerData(((CallData) proto).getAudioData(), proto.getSequence());
                    mState = State.RX;
                    break;
                case ProtocolBase.PTYPE_CALL_TERM:
                    mLogger.d(TAG, "rxed callTerm");
                    mCallHangGuardTimer.cancel();
                    mCallHangGuardTimer = mExecCtx.createTimerTask();
                    mExecCtx.schedule(mCallHangGuardTimer, GlobalConstants.CALL_HANG_PERIOD);
                    break;
            }
        }

        private NamedTimerTask mCallHangGuardTimer;
    }

    /**
     * call init state:
     * - callInit: from myself, kept sending callInit until 3 callInit, and then transit to callTxing
     * - callInit: from others, to callRxing
     * - callTerm: to callhang
     * - callData: from others, to callRxing
     */
    private class StateInit extends StateNode {

        @Override
        public void entry() {
            mLogger.d(TAG, "enter call init");
            mFirstPktTime = System.nanoTime();
            sendCallInit();
            mFirstPktSeqNumber = mSeqNumber;
            armTxTimer();
            mbChannelGranted = false;
        }

        @Override
        public void exit() {
            mLogger.d(TAG, "exit call init");
            if( mTxTimer != null){
                mTxTimer.cancel();
                mTxTimer = null;
            }
        }

        @Override
        public void packetReceived(DatagramPacket packet) {
            mLogger.d(TAG, "rxed: " + ProtocolHelpers.peepProtocol(packet));
            //TODO: to validate packet source and seq
            // validate target/src
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            switch( proto.getType()){
                case ProtocolBase.PTYPE_CALL_INIT:
                    CallInit callInit = (CallInit) proto;
                    if( callInit.getSource() == mConfig.mSuid ){
                        mLogger.d(TAG, "rxed callInit, initiated from myself");
                        mbChannelGranted = true;
                    } else {
                        mLogger.d(TAG, "rxed callInit from other");
                        mState = State.RX;
                    }
                    break;
                case ProtocolBase.PTYPE_CALL_DATA:
                    //TODO: validate
                    mLogger.d(TAG, "rxed callData");
                    mSpkr.offerData(((CallData) proto).getAudioData(), proto.getSequence());
                    mState = State.RX;
                    break;
                case ProtocolBase.PTYPE_CALL_TERM:
                    mLogger.d(TAG, "rxed callTerm");
                    mState = State.HANG;
                    break;
            }
        }

        @Override
        public void timerExpired(NamedTimerTask timerTask) {
            if( timerTask == mTxTimer){
                int numSent = mSeqNumber - mFirstPktSeqNumber;
                if( numSent >= 3 ){
                    mLogger.d(TAG, "we've sent " + numSent +" callInit" + ", channel granted=" + mbChannelGranted);
                    if( mbChannelGranted ){
                        mState = State.TX;
                    } else {
                        mState = State.ONLINE;
                    }
                } else {
                    mLogger.d(TAG, "tx timer timed out, " + numSent);
                    sendCallInit();
                    rearmTxTimer();
                }
            }
        }

        private void armTxTimer(){
            long timeNow = System.nanoTime();
            mTxTimer = mExecCtx.createTimerTask();
            long delay = GlobalConstants.CALL_PACKET_INTERVAL* (mSeqNumber - mFirstPktSeqNumber) - (int)((timeNow - mFirstPktTime)/(1000*1000));
            if(delay < 0) {
                mLogger.w(TAG, "negative delay:" + delay);
            }
            mExecCtx.schedule(mTxTimer, delay);
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

    /** call Txing state
     *  - on data available: sent data
     *  - ptt release: stop tx, and transit to TxStopping
     *  - txEnd: tx stopped prior to ptt release: call hang
     */
    private class StateTxing extends StateNode {
        @Override
        public void entry() {
            mLogger.d(TAG, "enter txing");
            mMic.start();
        }

        @Override
        public void exit() {
            mLogger.d(TAG, "exit txing");
            mMic.stop();
        }

        @Override
        public void ptt(boolean pressed) {
            if( !pressed ){
                mLogger.d(TAG, "ptt released");
                mState = State.TXSTOPPING;
            }
        }

        @Override
        public void txEnd() {
            mLogger.d(TAG, "tx end prior to ptt released");
            mState = State.HANG;
        }

        @Override
        public void micDataAvailable(ByteBuffer compressedAudio){
            sendCallData(compressedAudio);
        }
    }


    /** tx stopping state
     *  - send 3 call term, in 20ms interval, and transit to call hang
     *  - callInit: to rx
     *  - callData: to rx
     *  - callTerm: to callhang
     */
    private class StateTxStopping extends StateNode {
        @Override
        public void entry() {
            mLogger.d(TAG, "enter tx stopping");
            mLastPktSeqNumber = mSeqNumber;
            armTxTimer();
        }

        @Override
        public void exit() {
            mLogger.d(TAG, "exit tx stopping");
            if( mTxTimer != null){
                mTxTimer.cancel();
                mTxTimer = null;
            }
        }

        @Override
        public void timerExpired(NamedTimerTask timerTask) {
            if( timerTask == mTxTimer){
                sendCallTerm();
                int numSent = mSeqNumber - mLastPktSeqNumber;
                if( numSent >= 3 ){
                    mLogger.d(TAG, "we've sent " + numSent +" callTerm");
                    mState = State.HANG;
                } else {
                    mLogger.d(TAG, "tx timer timed out, " + numSent);
                    rearmTxTimer();
                }
            }
        }

        @Override
        public void packetReceived(DatagramPacket packet) {
            mLogger.d(TAG, "rxed: " + ProtocolHelpers.peepProtocol(packet));
            //TODO: to validate packet source and seq
            // validate target/src
            ProtocolBase proto = ProtocolFactory.getProtocol(packet);
            switch( proto.getType()){
                case ProtocolBase.PTYPE_CALL_INIT:
                    mLogger.d(TAG, "rxed callInit");
                    mState = State.RX;
                    break;
                case ProtocolBase.PTYPE_CALL_DATA:
                    mLogger.d(TAG, "rxed callData");
                    mSpkr.offerData(((CallData) proto).getAudioData(), proto.getSequence());
                    mState = State.RX;
                    break;
                case ProtocolBase.PTYPE_CALL_TERM:
                    mLogger.d(TAG, "rxed callTerm");
                    CallTerm callTerm = (CallTerm) proto;
                    if( callTerm.getSource() == mConfig.mSuid ){
                        //ignore
                    } else {
                        mState = State.HANG;
                    }
                    break;
            }
        }

        @Override
        public void txEnd() {
            mLogger.d(TAG, "tx end");
        }

        private void armTxTimer(){
            long timeNow = System.nanoTime();
            mTxTimer = mExecCtx.createTimerTask();
            long delay = GlobalConstants.CALL_PACKET_INTERVAL* (mLastPktSeqNumber + 1 - mFirstPktSeqNumber)
                                    - (int)((timeNow - mFirstPktTime)/(1000*1000));
            if(delay < 0) {
                mLogger.w(TAG, "negative delay:" + delay);
            }
            mExecCtx.schedule(mTxTimer, delay);
        }

        private void rearmTxTimer(){
            if(mTxTimer !=null){
                mTxTimer.cancel();
            }
            long timeNow = System.nanoTime();
            mTxTimer = mExecCtx.createTimerTask();
            long delay = GlobalConstants.CALL_PACKET_INTERVAL* (mSeqNumber - mFirstPktSeqNumber)
                    - (int)((timeNow - mFirstPktTime)/(1000*1000));
            if(delay < 0) {
                mLogger.w(TAG, "negative delay:" + delay);
            }
            mExecCtx.schedule(mTxTimer, delay);
        }

        private NamedTimerTask mTxTimer;
    }

    private void initializeSM(){
        StateNode aState;
        aState = new StateOffline();
        mStateMap.put(State.OFFLINE, aState);
        aState = new StateOnline();
        mStateMap.put(State.ONLINE, aState);
        aState = new StateRxing();
        mStateMap.put(State.RX, aState);
        aState = new StateHang();
        mStateMap.put(State.HANG, aState);
        aState = new StateInit();
        mStateMap.put(State.TXINIT, aState);
        aState = new StateTxing();
        mStateMap.put(State.TX, aState);
        aState = new StateTxStopping();
        mStateMap.put(State.TXSTOPPING, aState);

        mState = State.OFFLINE;
        mStateNode = mStateMap.get(mState);
    }

    private short mSeqNumber = 0;
    private short mFirstPktSeqNumber, mLastPktSeqNumber;
    private long mFirstPktTime;
    private State   mState, mStateOrig;
    private StateNode mStateNode;
    private final EnumMap<State, StateNode> mStateMap = new EnumMap<State, StateNode>(State.class);

    private final static String TAG = "Su";
}
