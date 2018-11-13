package org.study.jim.redis.practice;
import javafx.util.Pair;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.util.*;

import static java.util.Collections.checkedCollection;
import static java.util.Collections.sort;

public class ClickCountDemo {
    private static final String COUNT_PREFIX = "count:";
    private static final String KNOWS_KEY = "known:";
    //更新频率常量
    public static final int[] PRECISION = new int[]{1, 5, 60, 300, 3600, 18000, 86400};
    public static void testCounters(Jedis conn) throws InterruptedException {
        long now = System.currentTimeMillis() / 1000;
        for (int i = 0; i < 10; i++) {
            int count = (int)(Math.random() * 5) + 1;
            updateCounter(conn, "hits", count, now + i);
        }
        //1秒钟点击频率输出
        List<Pair<Integer,Integer>> counter = getCounter(conn,"hits",1);
        for (Pair<Integer,Integer> count : counter){
            System.out.println("  " + count);
        }
        //5秒钟点击频率输出
        counter = getCounter(conn,"hits",5);
        //定时清除点击次数集合的线程
        CleanCountersThread cleanCountersThread = new CleanCountersThread(conn,0,2*86400000);
        cleanCountersThread.start();
        Thread.sleep(1000);
        cleanCountersThread.quit();
        cleanCountersThread.interrupt();
        counter = getCounter(conn,"hits",86400);
        System.out.println("Did we clean out all of the counters? " + (counter.size() == 0));
    }
    //获取点击次数输出
    public static List<Pair<Integer,Integer>> getCounter(Jedis conn, String name, int precision){
        String hash = String.valueOf(precision)+":"+name;
        Map<String,String> data = conn.hgetAll(COUNT_PREFIX+hash);
        ArrayList<Pair<Integer,Integer>> results = new ArrayList<Pair<Integer, Integer>>();
        for(Map.Entry<String,String> entry: data.entrySet()){
            results.add(new Pair<Integer, Integer>(
                    Integer.parseInt(entry.getKey()),
                    Integer.parseInt(entry.getValue())
            ));
        }
        return results;
    }
    //更新点击次数
    public static void updateCounter(Jedis conn,String name,int count){
        updateCounter(conn,name,count,System.currentTimeMillis()/1000);
    }
    public static void updateCounter(Jedis conn,String name,int count,long now){
        Transaction trans = conn.multi();
        for(int prec:PRECISION){
            long pnow = (now / prec) * prec; //获取需要处理的时间片
            String hash = String.valueOf(prec)+":"+name;
            trans.zadd(KNOWS_KEY,0,hash);
            trans.hincrBy(COUNT_PREFIX+hash,String.valueOf(pnow),count);
        }
        trans.exec();
    }
    private static class CleanCountersThread extends Thread {
        private Jedis conn;
        private int sampleCount = 100;
        private boolean quit;
        private long timeOffset;

        public CleanCountersThread(Jedis conn, int sampleCount, long timeOffset) {
            this.conn = conn;
            this.sampleCount = sampleCount;
            this.timeOffset = timeOffset;
        }
        public void quit(){
            quit = true;
        }
        @Override
        public void run() {
            int passes = 0;
            while (!quit){
                long start = System.currentTimeMillis()+timeOffset;
                int index = 0;
                while (index < conn.zcard(KNOWS_KEY)){
                    Set<String> hashSet = conn.zrange(KNOWS_KEY,index,index);
                    index++;
                    if(hashSet.size()==0){
                        break;
                    }
                    String hash = hashSet.iterator().next();
                    int prec = Integer.parseInt(hash.substring(0,hash.indexOf(':')));
                    int bprec = (int)Math.floor(prec/60);
                    if(bprec == 0){
                        bprec = 1;
                    }
                    if((passes % bprec) != 0 ){
                        continue;
                    }
                    String hkey = COUNT_PREFIX+hash;
                    String cutoff = String.valueOf((System.currentTimeMillis()+timeOffset)/1000 - sampleCount * prec);
                    ArrayList<String> samples = new ArrayList<String>(conn.hkeys(hkey));
                    Collections.sort(samples);
                    int remove = bisectRight(samples,cutoff);
                    if(remove!=0){
                        conn.hdel(hkey,samples.subList(0,remove).toArray(new String[0]));
                        if(remove==samples.size()){
                            conn.watch(hkey);
                            if(conn.hlen(hkey)==0){
                                Transaction trans = conn.multi();
                                trans.zrem(KNOWS_KEY,hash);
                                trans.exec();
                                index--;
                            }else{
                                conn.unwatch();
                            }
                        }
                    }
                }
                passes++;
                long duration = Math.min((System.currentTimeMillis()+timeOffset)-start+1000,6000);
                try {
                    sleep(Math.max(60000-duration,1000));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        public int bisectRight(List<String> values, String key) {
            int index = Collections.binarySearch(values, key);
            return index < 0 ? Math.abs(index) - 1 : index + 1;
        }
    }
}
