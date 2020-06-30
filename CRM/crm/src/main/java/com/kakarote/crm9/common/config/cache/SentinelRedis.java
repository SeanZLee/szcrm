package com.kakarote.crm9.common.config.cache;

import com.jfinal.kit.StrKit;
import com.jfinal.plugin.redis.ICallback;
import redis.clients.jedis.Jedis;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis集群
 * @author zhangzhiwei
 */
public class SentinelRedis{

    public static RedisSentinelCache mainCache = null;

    private static final ConcurrentHashMap<String, RedisSentinelCache> cacheMap = new ConcurrentHashMap<>(32, 0.5F);

    public static void addCache(RedisSentinelCache cache) {
        if (cache == null) {
            throw new IllegalArgumentException("cache can not be null");
        }
        if (cacheMap.containsKey(cache.getName())) {
            throw new IllegalArgumentException("The cache name already exists");
        }

        cacheMap.put(cache.getName(), cache);
        if (mainCache == null) {
            mainCache = cache;
        }
    }

    public static RedisSentinelCache removeCache(String cacheName) {
        return cacheMap.remove(cacheName);
    }

    /**
     * 提供一个设置设置主缓存 mainCache 的机会，否则第一个被初始化的 Cache 将成为 mainCache
     */
    public static void setMainCache(String cacheName) {
        if (StrKit.isBlank(cacheName)) {
            throw new IllegalArgumentException("cacheName can not be blank");
        }
        cacheName = cacheName.trim();
        RedisSentinelCache cache = cacheMap.get(cacheName);
        if (cache == null){
            throw new IllegalArgumentException("the cache not exists: " + cacheName);
        }
        SentinelRedis.mainCache = cache;
    }

    public static RedisSentinelCache use() {
        return mainCache;
    }

    public static RedisSentinelCache use(String cacheName) {
        return cacheMap.get(cacheName);
    }

    public static <T> T call(ICallback callback) {
        return call(callback, use());
    }

    public static <T> T call(ICallback callback, String cacheName) {
        return call(callback, use(cacheName));
    }

    private static <T> T call(ICallback callback, RedisSentinelCache cache) {
        Jedis jedis = cache.getThreadLocalJedis();
        boolean notThreadLocalJedis = (jedis == null);
        if (notThreadLocalJedis) {
            jedis = cache.getJedisSentinelPool().getResource();
            cache.setThreadLocalJedis(jedis);
        }
        try {
            return callback.call(cache);
        }
        finally {
            if (notThreadLocalJedis) {
                cache.removeThreadLocalJedis();
                jedis.close();
            }
        }
    }
}
