package com.bluesky.core.subscriber;

/**
 * Created by liangc on 08/03/15.
 */
public enum State {
    OFFLINE,
    ONLINE,
    RX,
    CALL_HANG,
    TX,
    TX_INIT,
    TX_STOPPING,
}
