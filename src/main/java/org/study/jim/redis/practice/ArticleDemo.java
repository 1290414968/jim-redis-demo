package org.study.jim.redis.practice;

import org.study.jim.redis.jedis.JedisUtil;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Map;

public class ArticleDemo {
    private static String articleKeyPrefix = "article:";
    private static String scoreKey = "score:";
    private static String timeKey = "time:";
    private static String voteKeyPrefix = "vote:";
    //文章分数的常量值
    private static final int VOTE_SCORE = 432;
    //过期时间常量值
    private static final int ONE_WEEK_IN_SECONDS = 7 * 86400;

    //1、生成文章对象并存储入Hash
    public static String postArticle(Jedis conn,String user,String title,String link){
        String articleId = String.valueOf(conn.incr(articleKeyPrefix));
        String articleKey = "article:"+articleId;
        long now = System.currentTimeMillis()/1000;
        //1.1）、存储文章对象
        HashMap<String,String> articleData = new HashMap<String,String>();
        articleData.put("title",title);
        articleData.put("link",link);
        articleData.put("poster",user);
        articleData.put("time",String.valueOf(now));
        articleData.put("votes","1");
        conn.hmset(articleKey,articleData);
        //1.2）、存储文章分数对象
        conn.zadd(scoreKey,now+VOTE_SCORE,articleKey);
        //1.3）、存储文章时间对象
        conn.zadd(timeKey,now,articleKey);
        //1.4)、存储文章投票对象
        String voteKey = voteKeyPrefix+articleId;
        conn.sadd(voteKey,user);
        conn.expire(voteKey,ONE_WEEK_IN_SECONDS);
        return articleId;
    }
    //2、对已生成的文章进行投票
    public static void articleVote(Jedis conn,String user,String articleKey){

    }

    public static void printArticle(Jedis conn,String articleId){
        String articleKey = articleKeyPrefix+articleId;
        Map<String, String> dataMap =  conn.hgetAll(articleKey);
        for(Map.Entry<String,String> entry:dataMap.entrySet()){
            System.out.println("    " + entry.getKey() + ": " + entry.getValue());
        }
    }
    public static void main(String[] args) {
        Jedis jedis =  JedisUtil.getJedis();
        String articleId = postArticle(jedis,"jim","about netty","http://netty.com");
        printArticle(jedis,articleId);
    }
}
