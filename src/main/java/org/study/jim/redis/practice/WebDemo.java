package org.study.jim.redis.practice;

import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class WebDemo {
    private static final String LOGIN_KEY = "login:";
    private static final String RECENT_KEY = "recent:";
    private static final String VIEW_PREFIX = "viewed:";
    private static final String CART_KEY = "cart:";
    public static void testLoginCookie(Jedis conn) throws InterruptedException {
        String token = UUID.randomUUID().toString();
        //登录更新
        updateToken(conn,token,"u1","book1");
        System.out.println("We just logged-in/updated token: " + token);
        //检查登录
        String r = checkToken(conn,token);
        System.out.println("We just check : " + r);
        //清除登录-全部清除
        CleanSessionsThread thread = new CleanSessionsThread(conn,0);
        thread.start();
        Thread.sleep(1000);
        thread.quit();
        Thread.sleep(2000);
        if(thread.isAlive()){
            throw new RuntimeException("The clean sessions thread is still alive?!?");
        }
        long s = conn.hlen(LOGIN_KEY);
        System.out.println("The current number of sessions still available is: " + s);
        assert  s == 0;
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
    public static class CleanSessionsThread extends Thread{
        private Jedis conn;
        private int limit;
        private boolean quit;
        public CleanSessionsThread(Jedis conn, int limit) {
            this.conn = conn;
            this.limit = limit;
        }
        public void quit(){
            quit = true;
        }
        @Override
        public void run() {
            while(!quit){
                long size = conn.zcard(LOGIN_KEY);
                if(size<=limit){
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                long endIndex = Math.min(size-limit,100);
                Set<String> tokenSet = conn.zrange(RECENT_KEY,0,endIndex-1);//获取需要移除的令牌ID
                String[] tokens = tokenSet.toArray(new String[tokenSet.size()]);
                //封装需要移除的令牌的已浏览的key的集合
                ArrayList<String> sessionKeys = new ArrayList<String>();
                for (String token : tokens) {
                    sessionKeys.add("viewed:" + token);
                }
                //删除令牌的已浏览
                conn.del(sessionKeys.toArray(new String[sessionKeys.size()]));
                //删除登录的令牌
                conn.hdel(LOGIN_KEY,tokens);
                //删除最近登录的令牌
                conn.hdel(RECENT_KEY,tokens);
            }
        }
    }
}

