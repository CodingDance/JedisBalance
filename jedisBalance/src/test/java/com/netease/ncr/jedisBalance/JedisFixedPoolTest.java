package com.netease.ncr.jedisBalance;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

/**
 * @author hzyiting
 * @date 2015年8月24日 下午4:36:44
 */
public class JedisFixedPoolTest {

    @Test
    public void test() {
        //对象池的配置属性，可根据服务器性能进行相应配置调优，如不配置，则采用对象池默认属性
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMinIdle(5);
        poolConfig.setMaxTotal(8);

        //设置redis实例地址，或者redis proxy地址
        List<String> addressList = new ArrayList<String>();
        addressList.add("127.0.0.1:6379");
        addressList.add("127.0.0.1:6380");

        //初始化JedisFixedPool
        IJedisPool pool = new JedisFixedPool(poolConfig, addressList, "123456");
        Jedis jedis = null;
        try {
            //池中获取对象
            jedis = pool.getResource();
            //调用相应指令
            jedis.set("key","value");
        } catch (JedisConnectionException e) {
            e.printStackTrace();
        } finally {
            if(jedis!=null){
                jedis.close();  //最后进行关闭，归还对象到池
            }
        }

    }

}
