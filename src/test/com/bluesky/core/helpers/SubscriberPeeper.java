package test.com.bluesky.core.helpers;

import com.bluesky.common.CallInformation;
import com.bluesky.core.subscriber.State;
import com.bluesky.core.subscriber.Subscriber;

import java.lang.reflect.Field;

/**
 * peep subscriber private members
 */
public class SubscriberPeeper {
    public SubscriberPeeper() throws Exception{
        fieldState = Subscriber.class.getDeclaredField("mState");
        fieldState.setAccessible(true);

        fieldCallInfo = Subscriber.class.getDeclaredField("mCallInfo");
        fieldCallInfo.setAccessible(true);

        fieldFirstCallSeq = Subscriber.class.getDeclaredField("mFirstPktSeqNumber");
        fieldFirstCallSeq.setAccessible(true);

        fieldFirstCallTime = Subscriber.class.getDeclaredField("mFirstPktTime");
        fieldFirstCallTime.setAccessible(true);

        fieldSeqNumber = Subscriber.class.getDeclaredField("mSeqNumber");
        fieldSeqNumber.setAccessible(true);
    }

    public State peepState(Subscriber sub) throws Exception{
        return (State) fieldState.get(sub);
    }

    public void setState(Subscriber sub, State state) throws Exception{
        fieldState.set(sub, state);
    }

    public CallInformation peepCallInfo(Subscriber sub) throws Exception{
        return (CallInformation) fieldCallInfo.get(sub);
    }

    public void setFirstCallSeq(Subscriber sub, short seq) throws Exception {
        fieldFirstCallSeq.set(sub, seq);
    }

    public void setFirstCallTime(Subscriber sub, long ms) throws Exception {
        fieldFirstCallTime.set(sub, ms);
    }

    public void setSeqNumber(Subscriber sub, short seq) throws Exception {
        fieldSeqNumber.set(sub, seq);
    }

    private Field fieldState, fieldCallInfo, fieldFirstCallSeq, fieldFirstCallTime, fieldSeqNumber;
}
