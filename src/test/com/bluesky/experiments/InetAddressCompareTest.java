package test.com.bluesky.experiments;

import junit.framework.Assert;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import java.net.Inet4Address;
import java.net.InetAddress;

import static junit.framework.TestCase.assertEquals;

/**
 * Created by liangc on 21/03/15.
 */
public class InetAddressCompareTest {

    @Test
    public void testTwoInet4Address() throws Exception{
        Inet4Address addr1, addr2;
        String hostName = "127.0.0.1";
        addr1 = (Inet4Address) InetAddress.getByName(hostName);
        addr2 = (Inet4Address) InetAddress.getByName(hostName);
        assertEquals(addr1, addr2);
        if( addr1 != addr2){
            System.out.println("unequal address, addr1=" + addr1 + ", addr2=" + addr2);
        }
    }
}
