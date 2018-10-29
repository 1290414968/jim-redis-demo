package org.study.jim.redis.jedis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.util.UUID;

public class LockDemo {
    //获取锁
    public String acquireLock(String lockName,long acquireTimeout,long lockTimeout){
        String identifier = UUID.randomUUID().toString();
        String key = "lock:"+lockName;
        int lockExpire = (int)lockTimeout/1000;
        Jedis jedis =  JedisUtil.getJedis();
        long end = System.currentTimeMillis()+acquireTimeout;
        try{
            while (System.currentTimeMillis()<end){
                if(jedis.setnx(key,identifier) == 1){//设置值成功即获取锁成功
                    jedis.expire(key,lockExpire);
                    //让key值过期，其他线程可以获取锁，当没有释放锁的逻辑没有被调用时可以自动释放锁
                    return identifier;
                }
                if(jedis.ttl(key) ==  -1){
                    jedis.expire(key,lockExpire);
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }finally {
            jedis.close();
        }
        return null;
    }
    //释放锁
    public boolean releaseLock(String lockKey,String identifier){
        System.out.println("开始释放锁:"+lockKey);
        Jedis jedis = JedisUtil.getJedis();
        lockKey = "lock:"+lockKey;
        boolean release = false;
        while (true){
            jedis.watch(lockKey);
            if(identifier.equals(jedis.get(lockKey))){//释放锁
                Transaction transaction =  jedis.multi();
                transaction.del(lockKey);
                if(transaction.exec().isEmpty()){
                    continue;
                }
                release = true;
            }
            jedis.unwatch();
            break;
        }
        return release;
    }
}
