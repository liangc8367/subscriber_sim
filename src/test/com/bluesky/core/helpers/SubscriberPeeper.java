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
    }

    public State peepState(Subscriber sub) throws Exception{
        return (State) fieldState.get(sub);
    }

    public CallInformation peepCallInfo(Subscriber sub) throws Exception{
        return (CallInformation) fieldCallInfo.get(sub);
    }

    private Field fieldState, fieldCallInfo;
}
