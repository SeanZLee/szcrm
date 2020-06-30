package com.kakarote.crm9.common.config.cache;

import com.jfinal.plugin.redis.Cache;
import com.jfinal.plugin.redis.IKeyNamingPolicy;
import com.jfinal.plugin.redis.serializer.ISerializer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisSentinelPool;

/**
 * Redis集群
 * @author zhangzhiwei
 */
public class RedisSentinelCache extends Cache {
    private JedisSentinelPool jedisSentinelPool;
    public RedisSentinelCache(String name, JedisSentinelPool jedisSentinelPool, ISerializer serializer, IKeyNamingPolicy keyNamingPolicy) {
        this.name = name;
        this.jedisSentinelPool = jedisSentinelPool;
        this.serializer = serializer;
        this.keyNamingPolicy = keyNamingPolicy;
    }
    @Override
    public Jedis getJedis() {
        Jedis jedis = this.threadLocalJedis.get();
        return jedis != null ? jedis : this.getJedisSentinelPool().getResource();
    }

    public JedisSentinelPool getJedisSentinelPool() {
        return jedisSentinelPool;
    }
}
