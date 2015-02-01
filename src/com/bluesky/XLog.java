package com.bluesky;

import com.bluesky.common.OLog;

import java.util.logging.Logger;

/**
 * Created by liangc on 01/02/15.
 */
public class XLog implements OLog {

    public XLog(){

    }

    @Override
    public void e(String s, String s1) {
        LOGGER.severe(s +":" +s1);
    }

    @Override
    public void w(String s, String s1) {
        LOGGER.warning(s +":" +s1);
    }

    @Override
    public void i(String s, String s1) {
        LOGGER.info(s +":" +s1);
    }

    @Override
    public void d(String s, String s1) {
        LOGGER.fine(s +":" +s1);
    }

    private final static Logger LOGGER = Logger.getLogger("com.bluesky");
}
