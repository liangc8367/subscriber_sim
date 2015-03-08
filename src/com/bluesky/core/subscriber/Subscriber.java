package com.bluesky.core.subscriber;

import com.bluesky.DataSink;
import com.bluesky.DataSource;
import com.bluesky.common.*;
import com.bluesky.protocol.*;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.EnumMap;

/** subscriber state machine, all methods should be called in the same thread context.
 *
 * Created by liangc on 01/02/15.
 */
public class Subscriber {
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

    public void micDataAvailable(ByteBuffer compressedAudio){
        saveStateContext();
        mStateNode.micDataAvailable(compressedAudio);
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
    void sendRegistration(){
        Registration reg = new Registration(GlobalConstants.SUID_TRUNK_MANAGER, mConfig.mSuid, ++mSeqNumber);
        ByteBuffer payload  = ByteBuffer.allocate(reg.getSize());
        reg.serialize(payload);
        System.out.println("to send reg: seq=" + mSeqNumber);
        mUdpSvc.send(payload);
    }

    /** start calling procedure */
    void sendCallInit(){
        CallInit preamble = new CallInit(mConfig.mTgtid, mConfig.mSuid, ++mSeqNumber);
        ByteBuffer payload = ByteBuffer.allocate(preamble.getSize());
        preamble.serialize(payload);
        System.out.println("to send callInit: seq=" + mSeqNumber);
        mUdpSvc.send(payload);
    }

    /** send compressed audio data */
    void sendCallData(ByteBuffer compressedAudio){
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
    void sendCallTerm(){
        CallTerm callTerm = new CallTerm(
                mConfig.mTgtid,
                mConfig.mSuid,
                ++mSeqNumber
        );
        ByteBuffer payload = ByteBuffer.allocate(callTerm.getSize());
        callTerm.serialize(payload);
        System.out.println("to send callTerm: seq=" + mSeqNumber);
        mUdpSvc.send(payload);
    }

    /** enable/disable packet receiving fast path, i.e. route received callData/callTerm to Spkr module directly
     *
     */
    void enableRxFastPath(boolean enable){
        //TODO:
    }

    /** record call information
     *
     */
    void recordCallInfo(long target, long source){
        mCallInfo.mTargetId = target;
        mCallInfo.mSuid = source;
    }

    Configuration mConfig;
    private CallInformation mCallInfo = new CallInformation();
    final DataSource mMic;
    final DataSink mSpkr;
    final UDPService mUdpSvc;
    final OLog mLogger;
    final SubscriberExecContext mExecCtx;
    final static String TAG = "Su";

    private SubscriberStateListener mStateListener = null;


    private void initializeSM(){
        StateNode aState;
        aState = new StateOffline(this);
        mStateMap.put(State.OFFLINE, aState);
        aState = new StateOnline(this);
        mStateMap.put(State.ONLINE, aState);
        aState = new StateRxing(this);
        mStateMap.put(State.RX, aState);
        aState = new StateCallHang(this);
        mStateMap.put(State.CALL_HANG, aState);
        aState = new StateTxInit(this);
        mStateMap.put(State.TX_INIT, aState);
        aState = new StateTx(this);
        mStateMap.put(State.TX, aState);
        aState = new StateTxStopping(this);
        mStateMap.put(State.TX_STOPPING, aState);

        mState = State.OFFLINE;
        mStateNode = mStateMap.get(mState);
    }

    short mSeqNumber = 0;
    short mFirstPktSeqNumber, mLastPktSeqNumber;
    long mFirstPktTime; // in milliseconds
    State   mState;

    private State mStateOrig;
    private StateNode mStateNode;
    private final EnumMap<State, StateNode> mStateMap = new EnumMap<State, StateNode>(State.class);
}
