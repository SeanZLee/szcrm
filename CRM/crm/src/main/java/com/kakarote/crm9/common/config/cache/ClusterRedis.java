package com.kakarote.crm9.common.config.cache;

import com.jfinal.kit.StrKit;

import java.util.concurrent.ConcurrentHashMap;

public class ClusterRedis {
    public static RedisClusterCache mainCache = null;

    private static final ConcurrentHashMap<String, RedisClusterCache> cacheMap = new ConcurrentHashMap<>(32, 0.5F);

    public static void addCache(RedisClusterCache cache) {
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

    public static RedisClusterCache removeCache(String cacheName) {
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
        RedisClusterCache cache = cacheMap.get(cacheName);
        if (cache == null){
            throw new IllegalArgumentException("the cache not exists: " + cacheName);
        }
        ClusterRedis.mainCache = cache;
    }

    public static RedisClusterCache use() {
        return mainCache;
    }

    public static RedisClusterCache use(String cacheName) {
        return cacheMap.get(cacheName);
    }
}
