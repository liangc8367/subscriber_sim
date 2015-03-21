package com.bluesky.core.database;

import com.bluesky.common.SubscriberDatabase;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.FileReader;
import java.util.Iterator;

/**
 * Created by liangc on 21/03/15.
 */
public class SubscriberDatabaseHelper {
    /** create database from given json file
     *
     * @param configFile
     * @return
     */
    public static SubscriberDatabase createDatabaseFromJson(String configFile){
        SubscriberDatabase database = null;
        try {
            database = new SubscriberDatabase();
            JSONParser parser = new JSONParser();
            JSONObject obj = (JSONObject)parser.parse(new FileReader(configFile));

            JSONArray grps = (JSONArray)obj.get("groups");
            Iterator grpItr = grps.iterator();
            while(grpItr.hasNext()){
                JSONObject grp = (JSONObject)grpItr.next();
                Long grpId = (Long) grp.get("id");
                database.addGroup(grpId);
            }

            JSONArray subs = (JSONArray)obj.get("subscribers");
            Iterator subItr = subs.iterator();
            while(subItr.hasNext()){
                JSONObject sub = (JSONObject)subItr.next();
                Long subid = (Long) sub.get("id");
                database.addSubscriber(subid);
                JSONArray belongs = (JSONArray)sub.get("belongs");
                Iterator grp = belongs.iterator();
                while(grp.hasNext()){
                    Long grpid = (Long) grp.next();
                    database.signup(subid, grpid);
                }
            }

        }catch(Exception e){
            System.err.println("exception: " + e);
            database = null;
        }
        return database;
    }
}
