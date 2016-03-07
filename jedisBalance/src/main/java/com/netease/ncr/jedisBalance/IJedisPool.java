package com.netease.ncr.jedisBalance;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * @author hzweizijun 
 * @date 2015年8月24日 下午6:55:27
 */
public interface IJedisPool {
    /**
     * get a Jedis Connetion from the Pool
     * @return jedis
     */
	public Jedis getResource() throws JedisConnectionException;
}
