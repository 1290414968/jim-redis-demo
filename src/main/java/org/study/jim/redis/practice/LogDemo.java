package org.study.jim.redis.practice;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.Tuple;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class LogDemo {
    //定义5种日志级别
    public static final String DEBUG = "debug";
    public static final String INFO = "info";
    public static final String WARNING = "warning";
    public static final String ERROR = "error";
    public static final String CRITICAL = "critical";

    private static final String RECENT_KEY_PREFIX = "recent:";
    private static final String COMMON_KEY_PREFIX = "common:";
    private static final String START_SUFFIX = ":start";
    private static final String LAST_SUFFIX = ":last";
    private static final String PSTART_SUFFIX = ":pstart";

    public static final SimpleDateFormat TIMESTAMP = new SimpleDateFormat("EEE MMM dd HH:00:00 yyyy");
    private static final SimpleDateFormat ISO_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:00:00");
    public static final Collator COLLATOR = Collator.getInstance();
    public static void testLogRecent(Jedis conn){
        System.out.println("testLogRecent.......");
        for(int i = 0;i<5;i++){
            logRecent(conn,"uTest","this is message"+i);
        }
        //去除最近日志进行输出查看
        List<String> recent = conn.lrange(RECENT_KEY_PREFIX+"uTest"+":"+INFO,0,-1);
        System.out.println(
                "The current recent message log has this many messages: " +
                        recent.size());
        for (String message : recent){
            System.out.println(message);
        }
    }
    public static void testLogCommon(Jedis conn){
        System.out.println("testLogComm.......");
        for(int count = 1;count<6;count++){
            for (int i = 0; i < count; i ++) {
                logCommon(conn, "test", "message-" + count);
            }
        }
        Set<Tuple> common = conn.zrevrangeWithScores(COMMON_KEY_PREFIX+"test"+":info", 0, -1);
        System.out.println("The current number of common messages is: " + common.size());
        System.out.println("Those common messages are:");
        for (Tuple tuple : common){
            System.out.println("  " + tuple.getElement() + ", " + tuple.getScore());
        }
    }
    public static void logCommon(Jedis conn, String name, String message) {
        logCommon(conn, name, message, INFO, 5000);
    }
    //常用日志记录 --> 记录常用日志然后进行最新日志的轮换
    public static void logCommon(Jedis conn,String name,String message,String severity,int timeout){
        String commonDest = COMMON_KEY_PREFIX+name+":"+severity;
        String startKey = commonDest+START_SUFFIX;
        long end = System.currentTimeMillis()+timeout;
        while (System.currentTimeMillis()<end){
            conn.watch(startKey);
            String hourStart = ISO_FORMAT.format(new Date());
            String existing = conn.get(startKey);
            Transaction trans = conn.multi();
            if(existing!=null && COLLATOR.compare(existing,hourStart) < 0){//备份旧的常用日志，更新当前所处的小时数
                trans.rename(commonDest,commonDest+LAST_SUFFIX);
                trans.rename(startKey,commonDest+PSTART_SUFFIX);
                trans.set(startKey,hourStart);
            }
            trans.zincrby(commonDest,1,message);
            String recentDest = RECENT_KEY_PREFIX+name+":"+severity;
            trans.lpush(recentDest,TIMESTAMP.format(new Date())+' '+message);
            trans.ltrim(recentDest,0,-1);
            List<Object> results =  trans.exec();
            if (results == null){
                continue;
            }
            return;
        }
    }
    public static void logRecent(Jedis conn,String  name,String message){
        logRecent(conn,name,message,INFO);
    }
    //最近日志记录
    public static void logRecent(Jedis conn,String  name,String message,String severity){
        String destination = RECENT_KEY_PREFIX+name+":"+severity;
        Pipeline pipeline = conn.pipelined();
        pipeline.lpush(destination,TIMESTAMP.format(new Date())+' '+message);
        pipeline.ltrim(destination,0,99);//裁剪列表元素
        pipeline.sync();
    }
}
