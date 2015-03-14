package com.bluesky.core.connectivity;

import com.bluesky.common.UDPService;
import com.bluesky.core.subscriber.Subscriber;

import java.net.DatagramPacket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** relay udp received packets, and process it in executor context
 * Created by liangc on 14/03/15.
 */
public class RxedPacketCourier implements Runnable, UDPService.CompletionHandler {
    private Subscriber mSu;
    private ScheduledExecutorService mExecutor;
    public RxedPacketCourier(final Subscriber su, ScheduledExecutorService scheduler){
        mSu = su;
        mExecutor = scheduler;
    }

    @Override
    public void run(){
        DatagramPacket pkt;
        while((pkt=mQueue.poll())!=null){
            mSu.packetReceived(pkt);
        }
    }

    @Override
    public void completed(DatagramPacket packet) {
        mQueue.add(packet);
        mExecutor.schedule(this, 0, TimeUnit.MILLISECONDS);
    }

    final ConcurrentLinkedQueue<DatagramPacket> mQueue = new ConcurrentLinkedQueue<DatagramPacket>();
}
