package com.netease.ncr.jedisBalance;

import redis.clients.jedis.ShardedJedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * @author hzweizijun 
 * @date 2015年8月24日 下午10:53:26
 */
public interface IShardedJedisPool {
    /**
     * get a Jedis Connetion from the Pool
     * @return jedis
     */
	public ShardedJedis getResource() throws JedisConnectionException;
}
