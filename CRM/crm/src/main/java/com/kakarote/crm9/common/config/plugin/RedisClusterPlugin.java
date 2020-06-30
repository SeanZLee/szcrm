package com.kakarote.crm9.common.config.plugin;

import com.jfinal.plugin.IPlugin;
import com.kakarote.crm9.common.config.cache.ClusterRedis;
import com.kakarote.crm9.common.config.cache.RedisClusterCache;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPoolConfig;
import java.util.Set;


public class RedisClusterPlugin implements IPlugin {
    private Integer maxTotal = 1000;
    private Integer maxIdle = 200;
    private Long maxWaitMillis = 2000L;
    private Boolean testOnBorrow = true;
    private String defaultName = "main";
    private Set<HostAndPort> jedisCluster;


    public RedisClusterPlugin(Set<HostAndPort> jedisCluster) {
        this.jedisCluster = jedisCluster;
    }

    @Override
    public boolean start() {
        if (jedisCluster != null && jedisCluster.size() > 0) {
            JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
            jedisPoolConfig.setMaxTotal(maxTotal);
            jedisPoolConfig.setMaxIdle(maxIdle);
            jedisPoolConfig.setMaxWaitMillis(maxWaitMillis);
            jedisPoolConfig.setTestOnBorrow(testOnBorrow);
            JedisCluster jedisCluster = new JedisCluster(getJedisCluster(), jedisPoolConfig);
            ClusterRedis.addCache(new RedisClusterCache(jedisCluster));
            return true;
        }
        return false;
    }

    @Override
    public boolean stop() {

        return true;
    }

    public Integer getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(Integer maxTotal) {
        this.maxTotal = maxTotal;
    }

    public Integer getMaxIdle() {
        return maxIdle;
    }

    public void setMaxIdle(Integer maxIdle) {
        this.maxIdle = maxIdle;
    }

    public Long getMaxWaitMillis() {
        return maxWaitMillis;
    }

    public void setMaxWaitMillis(Long maxWaitMillis) {
        this.maxWaitMillis = maxWaitMillis;
    }

    public Boolean getTestOnBorrow() {
        return testOnBorrow;
    }

    public void setTestOnBorrow(Boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }

    public Set<HostAndPort> getJedisCluster() {
        return jedisCluster;
    }

    public void setJedisCluster(Set<HostAndPort> jedisCluster) {
        this.jedisCluster = jedisCluster;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public void setDefaultName(String defaultName) {
        this.defaultName = defaultName;
    }
}
