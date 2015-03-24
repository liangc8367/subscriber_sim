package com.bluesky.TestHarness;

import com.bluesky.common.SubscriberDatabase;
import com.bluesky.core.database.SubscriberDatabaseHelper;

/** create and execute integration test harness
 * Created by liangc on 21/03/15.
 */
public class Main {


    public static void main(String[] args) {
        if( args.length != 1){
            System.err.println("Syntax int-test [config.json]");
            System.exit(-1);
        }

        System.out.println("start integration test... " + args[0]);
        SubscriberDatabase database = SubscriberDatabaseHelper.createDatabaseFromJson(args[0]);
        if(database == null){
            System.err.println("invalid database");
            System.exit(-1);
        }

        Farmer farmer = new Farmer(database);
        farmer.start();

        try{
            Thread.currentThread().sleep(5000);
        } catch (InterruptedException e){

        }

        Monkey monkey = new Monkey(farmer.getSubscribers());
        monkey.start();

        while(true) {
            try {
                Thread.currentThread().sleep(1000); //
            } catch (InterruptedException e) {
                System.out.println("Interrupted " + e);
                System.exit(-2);
            }
        }
    }

}
