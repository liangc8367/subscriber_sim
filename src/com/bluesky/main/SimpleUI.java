package com.bluesky.main;

import com.bluesky.common.CallInformation;
import com.bluesky.core.subscriber.State;
import com.bluesky.core.subscriber.Subscriber;

/**
 * Created by liangc on 14/03/15.
 */
public class SimpleUI implements Subscriber.StateListener{
    final private Subscriber sub;
    public SimpleUI(Subscriber sub){
        this.sub = sub;
    }
    @Override
    public void stateChanged(State newState) {
        CallInformation callInfo = sub.getCallInfo();
        System.out.println("Su: state[" + newState +
                "], target = " + callInfo.mTargetId +
                ", source = " + callInfo.mSourceId);
    }

}
