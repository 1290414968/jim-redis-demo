package org.study.jim.redis.practice;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.util.List;

public class PurchaseItemDemo {
    private static String INVENTORY_PREFIX = "inventory:";
    private static String MARKET_KEY = "market:";
    private static String USER_KEY = "users:";
    //使用Transaction 命令来处理事务逻辑
    //列出买卖市场中的物品
    public static boolean listItem(Jedis conn,String itemId,String sellerId,double price){
        String inventory = INVENTORY_PREFIX+sellerId;
        String item = itemId+"."+sellerId;
        long end = System.currentTimeMillis()+5000;
        while (System.currentTimeMillis() < end){
            conn.watch(inventory);
            if(!conn.sismember(inventory,item)){//判断物品是否在集合中
                conn.unwatch();
                return false;
            }
            //将商品添加到买卖市场中
            Transaction trans = conn.multi();
            trans.zadd(MARKET_KEY,price,item);
            trans.srem(inventory,itemId);
            List<Object> results =  trans.exec();
            if (results == null){
                continue;
            }
            return true;
        }
        return false;
    }
    public static boolean purchaseItem(Jedis conn,String buyerId,String itemId,
                                       String sellerId,double lprice){
        String buyer = USER_KEY+buyerId;
        String seller = USER_KEY+sellerId;
        String item = itemId+"."+sellerId;
        String buyerInventory = INVENTORY_PREFIX+buyerId;
        long end = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis()<end){
            conn.watch(MARKET_KEY,buyer); //对商品买卖市场和买家进行监视
            double price = conn.zscore(MARKET_KEY,item);//检查市场上商品的状态
            double funds = Double.parseDouble(conn.hget(buyer,"funds"));//检查买家是否有足够的钱
            if (price != lprice || price > funds){
                conn.unwatch();
                return false;
            }
            Transaction trans = conn.multi();
            trans.hincrBy(seller,"funds",(int)price);
            trans.hincrBy(buyer,"funds",(int)-price);
            trans.sadd(buyerInventory,itemId);
            trans.zrem(MARKET_KEY,item);
            List<Object> results = trans.exec();
            // null response indicates that the transaction was aborted due to
            // the watched key changing.
            if (results == null){
                continue;
            }
            return true;
        }
        return false;
    }
}
