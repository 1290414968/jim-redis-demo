package org.study.jim.redis.practice;

import org.study.jim.redis.jedis.JedisUtil;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ZParams;

import java.util.*;

public class ArticleDemo {
    private static String articleKeyPrefix = "article:";
    private static String scoreKey = "score:";
    private static String timeKey = "time:";
    private static String voteKeyPrefix = "vote:";
    private static String groupPrefix = "group:";
    //文章分数的常量值
    private static final int VOTE_SCORE = 432;
    //过期时间常量值
    private static final int ONE_WEEK_IN_SECONDS = 7 * 86400;
    //分页每页个数
    private static final int ARTICLES_PER_PAGE = 25;
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
        //1)、先判断文章是否到了过期时间
        long cutoff = ( System.currentTimeMillis()/1000) - ONE_WEEK_IN_SECONDS;
        if(conn.zscore(timeKey,articleKey)<cutoff){
            return ;
        }
        String articleId = articleKey.substring(articleKey.indexOf(":")+1);
        if(conn.sadd(voteKeyPrefix+articleId,user)==1){//2)、添加文章投票对象
            //文章分数计算加常量值
            conn.zincrby(scoreKey,VOTE_SCORE,articleKey);
            //文章的votes的值递增
            conn.hincrBy(articleKey,"votes",1);
        }
    }
    public static List<Map<String,String>> getArticles(Jedis conn,int page){
        return getArticles(conn,page,scoreKey);
    }
    //3、对发布的文章进行评分排序和发布时间排序,分页的多个文章
    public static List<Map<String,String>> getArticles(Jedis conn,int page,String order){
        int start = (page-1)*ARTICLES_PER_PAGE;
        int end = start+ARTICLES_PER_PAGE-1;
        //按照排序参数对分数集合或者发布时间集合进行从高到低的排序，返回文章key
        Set<String> ids =  conn.zrevrange(order,start,end);
        List<Map<String,String>> articles = new ArrayList<Map<String, String>>();
        for(String id:ids){//循环文章key，获取文章对象并给文章对象设置id属性值
            Map<String,String> article =  conn.hgetAll(id);
            article.put("id",id);
            articles.add(article);
        }
        return articles;
    }
    //4、对文章进行分组
    public static void addGroup(Jedis conn,String articleId,String[] groups){
        String articleKey = articleKeyPrefix+articleId;
        for(String g:groups){//将一篇文章放入多个群组
            conn.sadd(groupPrefix+g,articleKey);
        }
    }
    //5、获取分组分页文章集合
    public static List<Map<String,String>> getGroupArticles(Jedis conn, String group, int page){
        return getGroupArticles(conn, group, page, scoreKey);
    }
    public static List<Map<String,String>> getGroupArticles(Jedis conn, String group, int page, String order) {
        String key = order + group;
        if (!conn.exists(key)) {
            ZParams params = new ZParams().aggregate(ZParams.Aggregate.MAX);
            conn.zinterstore(key, params, groupPrefix + group, order);
            conn.expire(key, 60);
        }
        return getArticles(conn, page, key);
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
