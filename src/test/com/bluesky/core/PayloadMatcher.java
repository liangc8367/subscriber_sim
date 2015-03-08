package test.com.bluesky.core;

import com.bluesky.protocol.ProtocolBase;
import com.bluesky.protocol.ProtocolFactory;
import org.mockito.ArgumentMatcher;

import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * matcher, to match payload against expected protocol
 */
public class PayloadMatcher extends ArgumentMatcher {
    public PayloadMatcher(long target, long source, Class cls){
        this.target = target;
        this.source = source;
        this.cls = cls;
    }
    public boolean matches(Object payload) {
        ByteBuffer pload = (ByteBuffer) ((Buffer) payload).flip();
        ProtocolBase proto = ProtocolFactory.getProtocol(pload);
        return (proto.getTarget() == target && proto.getSource() == source && proto.getClass() == cls);
    }

    private long target, source;
    private Class cls;
}

