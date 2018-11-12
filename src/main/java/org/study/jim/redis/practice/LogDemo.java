package org.study.jim.redis.practice;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class LogDemo {
    //定义5种日志级别
    public static final String DEBUG = "debug";
    public static final String INFO = "info";
    public static final String WARNING = "warning";
    public static final String ERROR = "error";
    public static final String CRITICAL = "critical";

    private static final String RECENT_KEY_PREFIX = "recent:";
    private static final String COMMON_KEY_PREFIX = "common:";
    public static final SimpleDateFormat TIMESTAMP = new SimpleDateFormat("EEE MMM dd HH:00:00 yyyy");
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
    //最近日志

    //最常日志

    //日志通用记录 --> 记录日志然后进行最新日志的轮换
    public static void logCommon(Jedis conn,String name,String message,String severity,int timeout){

    }
    public static void logRecent(Jedis conn,String  name,String message){
        logRecent(conn,name,message,INFO);
    }
    public static void logRecent(Jedis conn,String  name,String message,String severity){
        String destination = RECENT_KEY_PREFIX+name+":"+severity;
        Pipeline pipeline = conn.pipelined();
        pipeline.lpush(destination,TIMESTAMP.format(new Date())+' '+message);
        pipeline.ltrim(destination,0,99);//裁剪列表元素
        pipeline.sync();
    }
}
