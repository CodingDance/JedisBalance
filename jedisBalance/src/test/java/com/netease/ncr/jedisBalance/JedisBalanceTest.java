package com.netease.ncr.jedisBalance;

import java.util.ArrayList;
import java.util.List;


import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class JedisBalanceTest {

    public static void main(String args[]){
        JedisPoolConfig jpc = new JedisPoolConfig();
        List<String> address = new ArrayList<String>();
        address.add("10.180.157.189:6379");
        address.add("10.180.157.197:6379");
        IJedisPool jfp = new JedisFixedPool(jpc, address,"123456");
        int[] count = new int[4];

        int cnt = 0, counter = 0;
        while (true){
            Jedis jedis = null;
            try{
                jedis = jfp.getResource();
                jedis.set(String.valueOf(cnt), "abc");
            }catch(JedisConnectionException e){
                System.out.println("No. " + counter++);
                e.printStackTrace();
            }
            finally{
                if (jedis != null)
                    jedis.close();
                ++cnt;
                for (int j =0; j< 4; ++j){
                    System.out.println("redis " + j + " " + count[j]);
                }
                cnt = cnt == 100000 ? 0: cnt;
            }
        }

    }
}