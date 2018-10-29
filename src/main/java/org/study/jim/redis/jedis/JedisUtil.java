package org.study.jim.redis.jedis;

import org.study.jim.redis.AddressConstant;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisUtil {
    private static JedisPool pool = null;
    static {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(100);
        pool = new JedisPool(config,AddressConstant.REDIS_ADDRESS_IP);
    }
    public static Jedis getJedis(){
        return pool.getResource();
    }
}
