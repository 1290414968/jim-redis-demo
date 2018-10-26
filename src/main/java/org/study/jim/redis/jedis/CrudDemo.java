package org.study.jim.redis.jedis;
import org.study.jim.redis.AddressConstant;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;

import java.util.HashSet;
import java.util.Set;

public class CrudDemo {
    private static String testKey1 = "test1";
    public static void main(String[] args) {
//        simpleCrud();
//        getKey();
        sentinelOpe();
    }
    private static void sentinelOpe(){
        Set<String> sentinels = new HashSet<String>();
        JedisSentinelPool sentinelPool = new JedisSentinelPool(AddressConstant.REDIS_ADDRESS_IP,sentinels);
        Jedis jedis =  sentinelPool.getResource();
        System.out.println(jedis.get(testKey1));
    }
    private static void getKey(){
        Jedis jedis = new Jedis(AddressConstant.REDIS_ADDRESS_IP);
        jedis.select(15);
        String value = jedis.get(testKey1);
        System.out.println(value);
        jedis.close();
    }
    private static void simpleCrud(){
        Jedis jedis = new Jedis(AddressConstant.REDIS_ADDRESS_IP);
        jedis.select(15);
        jedis.sadd(testKey1,"first add test");
        jedis.set(testKey1,"first update");
        String value = jedis.get(testKey1);
        System.out.println(value);
//        jedis.del(testKey1);
        jedis.close();
//        jedis.expire(testKey1,10);//设置过期时间
    }
}
