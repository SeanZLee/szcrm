package com.kakarote.crm9.common.config.plugin;

import com.jfinal.plugin.IPlugin;
import com.jfinal.plugin.redis.IKeyNamingPolicy;
import com.jfinal.plugin.redis.serializer.FstSerializer;
import com.kakarote.crm9.common.config.cache.RedisSentinelCache;
import com.kakarote.crm9.common.config.cache.ClusterRedis;
import com.kakarote.crm9.common.config.cache.SentinelRedis;
import redis.clients.jedis.JedisSentinelPool;
import java.util.Set;

/**
 * Redis集群实现
 * @author zhangzhiwei
 */
public class RedisSentinelPlugin implements IPlugin {
    private String cacheName;
    private Set<String> host;
    private String password;

    public RedisSentinelPlugin(String cacheName, Set<String> host) {
        this.cacheName=cacheName;
        this.host=host;
    }
    public RedisSentinelPlugin(String cacheName, Set<String> host,String password) {
        this.cacheName=cacheName;
        this.host=host;
        this.password=password;
    }
    @Override
    public boolean start(){
        JedisSentinelPool jedisSentinelPool;
        if(this.cacheName!=null&&this.host!=null&&this.host.size()>0&&this.password==null){
            jedisSentinelPool=new JedisSentinelPool(cacheName,host);
        }else if(this.cacheName!=null&&this.host!=null&&this.host.size()>0&&this.password!=null){
            jedisSentinelPool=new JedisSentinelPool(cacheName,host,password);
        }else {
            throw new RuntimeException();
        }
        RedisSentinelCache cache = new RedisSentinelCache(this.cacheName, jedisSentinelPool, FstSerializer.me, IKeyNamingPolicy.defaultKeyNamingPolicy);
        SentinelRedis.addCache(cache);
        return true;
    }
    @Override
    public boolean stop() {
        RedisSentinelCache cache = SentinelRedis.removeCache(this.cacheName);
        if (cache == SentinelRedis.mainCache) {
            ClusterRedis.mainCache = null;
        }
        cache.getJedisSentinelPool().destroy();
        return true;
    }
}
