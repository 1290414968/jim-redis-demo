package org.study.jim.redis.practice;
import redis.clients.jedis.Jedis;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
public class WebDemo {
    private static final String LOGIN_KEY = "login:";
    private static final String RECENT_KEY = "recent:";
    private static final String VIEW_PREFIX = "viewed:";
    private static final String CART_PREFIX = "cart:";
    private static final String DELAY_KEY = "delay:";
    private static final String SCHEDULE_KEY = "schedule:";

    //登录的cookie存储
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
    //登录的购物车添加
    public static void testCartCookie(Jedis conn) throws InterruptedException {
        String token = UUID.randomUUID().toString();
        //登录更新
        updateToken(conn,token,"u2","apple");
        //购物车添加物品
        addCart(conn,token,"apple",5);
        //输出购物车
        printCart(conn,token);
        //清除购物车及登录等相关数据
        CleanFullSessionsThread thread = new CleanFullSessionsThread(conn,0);
        thread.start();
        Thread.sleep(1000);
        thread.quit();
        Thread.sleep(2000);
        if(thread.isAlive()){
            throw new RuntimeException("The clean sessions thread is still alive?!?");
        }
        printCart(conn,token);
    }
    //缓存行

    //缓存分析之后的浏览量高的请求
    public static void printCart(Jedis conn,String sessionId){
        Map<String,String> r =  conn.hgetAll(CART_PREFIX+sessionId);
        System.out.println("Our shopping cart currently has:");
        for (Map.Entry<String,String> entry : r.entrySet()){
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }
    }
    public static void addCart(Jedis conn,String sessionId,String item,int count){
        if(count<=0){//从购物车删除
            conn.hdel(CART_PREFIX+sessionId,item);
        }else{
            conn.hset(CART_PREFIX+sessionId,item,String.valueOf(count));
        }
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
            conn.zincrby(VIEW_PREFIX,-1,item);//将当前物品放在浏览集合的第一位置
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
                long size = conn.zcard(RECENT_KEY);
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
                    sessionKeys.add(VIEW_PREFIX + token);
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
    public static class CleanFullSessionsThread extends Thread{
        private Jedis conn;
        private int limit;
        private boolean quit;
        public CleanFullSessionsThread(Jedis conn, int limit) {
            this.conn = conn;
            this.limit = limit;
        }
        public void quit(){
            quit = true;
        }
        @Override
        public void run() {
            while(!quit){
                long size = conn.zcard(RECENT_KEY);//最近登录的数量
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
                    sessionKeys.add(VIEW_PREFIX + token);
                    sessionKeys.add(CART_PREFIX+token);
                }
                //删除令牌的已浏览和对应的购物车
                conn.del(sessionKeys.toArray(new String[sessionKeys.size()]));
                //删除登录的令牌
                conn.hdel(LOGIN_KEY,tokens);
                //删除最近登录的令牌
                conn.hdel(RECENT_KEY,tokens);
            }
        }
    }
}

