package org.study.jim.redis.practice;

import redis.clients.jedis.Jedis;

import java.util.UUID;

public class WebDemo {
    private static final String LOGIN_KEY = "login:";
    private static final String RECENT_KEY = "recent:";
    private static final String VIEW_PREFIX = "view:";

    public static void testLoginCookie(Jedis conn){
        String token = UUID.randomUUID().toString();
        //登录更新
        updateToken(conn,token,"u1","book1");
        System.out.println("We just logged-in/updated token: " + token);
        //检查登录
        String r = checkToken(conn,token);
        System.out.println("We just check : " + r);
        //清除登录

    }
    public static String checkToken(Jedis conn,String token){
        return conn.hget(LOGIN_KEY, token);
    }
    public static void updateToken(Jedis conn,String token,String user,String item){
        long timestamp =  System.currentTimeMillis()/1000;
        //存储登录的令牌映射对象
        conn.hset(LOGIN_KEY,token,user);
        //添加令牌的最近登录时间
        conn.zadd(RECENT_KEY,timestamp,token);
        if(item!=null){
            //将令牌的最近查看物品放入到集合中
            conn.zadd(VIEW_PREFIX+token,timestamp,item);
            conn.zremrangeByRank(VIEW_PREFIX+token,0,-26);//移除旧的记录，只保存最近浏览的25个
            conn.zincrby(VIEW_PREFIX,-1,item);//将当前物品放入浏览集合
        }
    }
    public class CleanSessionsThread extends Thread{
        private Jedis conn;
        private int limit;
        private boolean quit;
        public CleanSessionsThread(Jedis conn, int limit) {
            this.conn = conn;
            this.quit = quit;
        }
    }
}

