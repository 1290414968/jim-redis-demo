package org.study.jim.redis.practice;
import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Tuple;

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
    private static final String INV_KEY = "inv:";
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
    public static void testCacheRows(Jedis conn) throws InterruptedException {
        scheduleRowCache(conn,"itemX",5);
        System.out.println("Our schedule looks like:");
        Set<Tuple> tupleSet =  conn.zrangeWithScores(SCHEDULE_KEY,0,-1);
        for (Tuple tuple : tupleSet){
            System.out.println("  " + tuple.getElement() + ", " + tuple.getScore());
        }
        CacheRowsThread cacheRowsThread = new CacheRowsThread(conn);
        cacheRowsThread.start();
        Thread.sleep(1000);
        System.out.println("Our cached data looks like:");
        String r = conn.get(INV_KEY+"itemX");
        System.out.println(r);
        assert r != null;
        System.out.println();
        //执行5秒的缓存
        System.out.println("We'll check again in 5 seconds...");
        Thread.sleep(5000);
        System.out.println("Notice that the data has changed...");
        String r2 = conn.get(INV_KEY+"itemX");
        System.out.println(r2);
        System.out.println();
        assert r2 != null;
        assert !r.equals(r2);

        //清楚缓存行
        scheduleRowCache(conn, "itemX", -1);

        Thread.sleep(1000);
        r = conn.get(INV_KEY+"itemX");
        System.out.println("The cache was cleared? " + (r == null));
        assert r == null;

        //退出
        cacheRowsThread.quit();
        Thread.sleep(2000);
        if (cacheRowsThread.isAlive()){
            throw new RuntimeException("The database caching thread is still alive?!?");
        }
    }
    private static void scheduleRowCache(Jedis conn,String rowId,int delay){
        conn.zadd(DELAY_KEY,delay,rowId);
        conn.zadd(SCHEDULE_KEY,System.currentTimeMillis()/1000,rowId);
    }
    //缓存分析之后的浏览量高的请求

    //打印购物车数据
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
    public static class CacheRowsThread extends Thread{
        private Jedis conn;
        private boolean quit;
        public CacheRowsThread(Jedis conn) {
            this.conn = conn;
        }
        public void quit() {
            quit = true;
        }
        @Override
        public void run() {
            Gson gson = new Gson();
            while (!quit){
                //取出调度集合中的所有行及缓存时间
                Set<Tuple> range = conn.zrangeWithScores(SCHEDULE_KEY,0,0);
                Tuple next = range.size() > 0 ? range.iterator().next() : null;
                long now = System.currentTimeMillis() / 1000;
                if (next == null || next.getScore() > now){ //为了控制能缓存行进行时间的判断，条件成立则进行sleep(50) 再继续循环
                    try {
                        sleep(50);
                    }catch(InterruptedException ie){
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                String rowId = next.getElement();
                double delay = conn.zscore(DELAY_KEY,rowId);
                if(delay<=0){//当设置为0时，删除相关的数据
                    conn.zrem(DELAY_KEY,rowId);
                    conn.zrem(SCHEDULE_KEY,rowId);
                    conn.del(INV_KEY+rowId);
                    continue;
                }
                Inventory row = Inventory.get(rowId);
                conn.zadd(SCHEDULE_KEY, now + delay, rowId);
                conn.set(INV_KEY + rowId, gson.toJson(row));
            }
        }
    }
    public static class Inventory {
        private String id;
        private String data;
        private long time;

        private Inventory (String id) {
            this.id = id;
            this.data = "data to cache...";
            this.time = System.currentTimeMillis() / 1000;
        }

        public static Inventory get(String id) {
            return new Inventory(id);
        }
    }
}

