package test.com.bluesky.core;

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
    }

    public State peepState(Subscriber sub) throws Exception{
        return (State) fieldState.get(sub);
    }

    private Field fieldState;
}
