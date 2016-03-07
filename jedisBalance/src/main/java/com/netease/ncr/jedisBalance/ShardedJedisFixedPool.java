package com.netease.ncr.jedisBalance;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.ShardedJedis;
import redis.clients.jedis.ShardedJedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

import com.google.common.collect.ImmutableList;

/**
 * ShardedJedisFixedPool provided the function for you to get a active jedis connection from some
 * Redis Server and shard the jedis connection with consistent hashing.
 * <p>
 * ShardedJedisFixedPool is a pool contains many PoolObject,every PoolObject is a poolwhich contains
 * some redis connection to a Redis Server.</p>
 * <p>
 * ShardedJedisFixedPool used the consistent hashing  algorithm to hash the jedis connection to all the Redis Server.
 * ShardedJedisFixedPool used the Evictor thread {@link ShardedJedisFixedPool.Evictor} to ping every
 * PoolObject{@link ShardedJedisFixedPool.PooledObject} of the ShardedJedisFixedPool,if the PoolObject
 * is active,we can ensure the Redis Server is active,so we can get aJedis connection from this PoolObject;
 * otherwise we will rebulid ShardedJedisPool{@link ShardedJedisPool} to ensure all the get jedis connetion
 * request is success，so the risk is you hash is changed。But if you use NCR's redis service， you don't need
 * to worry，because the NCR use the redis-proxy to shard redis.
 * </p>
 * <p>Example：
 *     <example>
 *            JedisPoolConfig poolConfig = new JedisPoolConfig();
 *            poolConfig.setMinIdle(5);
 *            poolConfig.setMaxTotal(8);
 *            List<String> addressList = new ArrayList<String>();
 *            addressList.add("10.180.156.74:6379");
 *            addressList.add("10.180.156.78:6379");
 *            IShardedJedisPool pool = new ShardedJedisFixedPool(addressList, "123456", poolConfig);
 *            ShardedJedis jedis = null;
 *            try {
 *               jedis = pool.getResource();
 *               jedis.set("key","value");
 *            } catch (JedisConnectionException e) {
 *               e.printStackTrace();
 *            } finally {
 *                if(jedis!=null){
 *                   jedis.close();
 *                }
 *             }
 *      </example>
 * </p>
 *
 * @author weizijun,yiting
 * @date 2015年8月24日 下午3:55:56
 */
public class ShardedJedisFixedPool implements IShardedJedisPool {
    private static final int DEFAULT_MAX_WAIT_TIME = 2000;
    private static final int DEFAULT_TIMER_DELAY = 1000;
    private final JedisPoolConfig poolConfig;
    private int timeout = Protocol.DEFAULT_TIMEOUT;



    private Timer timer = new Timer();

    private int delay = DEFAULT_TIMER_DELAY;

    private volatile ShardedJedisPool shardedJedisPool;

    private volatile ImmutableList<PooledObject> pools = ImmutableList.of();

    /**
     * Init the ShardedJedisFixedPool
     *
     * @param poolConfig  jedis poolconfig
     * @param addressList a list contains the Redis Server host string info,example:
     *                    List<String> addressList = new ArrayList<String>();
     *                    addressList.add("127.0.0.1:6379");
     *                    addressList.add("127.0.0.2:6379");
     * @param timeout     set the connetion timeout.
     * @param password    jedis password
     */
    public ShardedJedisFixedPool(JedisPoolConfig poolConfig, List<String> addressList, int timeout, String password) {
        this.poolConfig = poolConfig;
        if (poolConfig.getMaxWaitMillis() == JedisPoolConfig.DEFAULT_MAX_WAIT_MILLIS) {
            this.poolConfig.setMaxWaitMillis(DEFAULT_MAX_WAIT_TIME);
        }
        this.timeout = timeout;
        initPool(addressList, password);

        startEvictor(delay);
    }

    /**
     * Init the ShardedJedisFixedPool without set password.
     * the constructor is linked with {@link #ShardedJedisFixedPool(JedisPoolConfig, List, int, String)}
     *
     * @param addressList server host list
     * @param timeout     the connetion timeout,and socket timeout.
     * @param poolConfig  jedis poolconfig
     */
    public ShardedJedisFixedPool(JedisPoolConfig poolConfig, List<String> addressList, int timeout) {
        this(poolConfig, addressList, timeout, null);
    }

    /**
     * Init the ShardedJedisFixedPool without set password and timeout time,uesd default timeout Protocol.DEFAULT_TIMEOUT{@link Protocol},
     * the constructor is linked with {@link #ShardedJedisFixedPool(JedisPoolConfig, List, int, String)}
     *
     * @param poolConfig  jedis poolconfig
     * @param addressList server host list
     */
    public ShardedJedisFixedPool(JedisPoolConfig poolConfig, List<String> addressList) {
        this(poolConfig, addressList, Protocol.DEFAULT_TIMEOUT, null);
    }


    /**
     * Init the ShardedJedisFixedPool without set password and timeout time,uesd default timeout Protocol.DEFAULT_TIMEOUT {@link Protocol},
     * the constructor is linked with {@link #ShardedJedisFixedPool(JedisPoolConfig, List, int, String)}
     *
     * @param poolConfig  jedis poolconfig
     * @param addressList server host list
     * @param password    jedis password
     */
    public ShardedJedisFixedPool(JedisPoolConfig poolConfig, List<String> addressList, String password) {
        this(poolConfig, addressList, Protocol.DEFAULT_TIMEOUT, password);
    }

    private void initPool(List<String> addressList, String password) {
        List<JedisShardInfo> shards = new ArrayList<JedisShardInfo>();
        ImmutableList.Builder<PooledObject> builder = ImmutableList.builder();
        for (String address : addressList) {

            String[] arr = address.split(":");
            if (arr.length != 2) {
                throw new JedisException("address invalid:" + address);
            }

            String host = arr[0];
            int port = Integer.valueOf(arr[1]);

            JedisShardInfo info = new JedisShardInfo(host, port, timeout);
            info.setPassword(password);
            shards.add(info);

            PooledObject pooledObject = new PooledObject(info);
            builder.add(pooledObject);
        }

        this.shardedJedisPool = new ShardedJedisPool(poolConfig, shards);
        this.pools = builder.build();
    }

    private void resetPools() {
        ShardedJedisPool newShardedJedisPool;
        List<JedisShardInfo> shards = new ArrayList<JedisShardInfo>();
        for (PooledObject pooledObject : pools) {
            if (pooledObject.isActive()) {
                shards.add(pooledObject.getJedisShardInfo());
            }
        }

        newShardedJedisPool = new ShardedJedisPool(poolConfig, shards);
        this.shardedJedisPool = newShardedJedisPool;
    }

    private void startEvictor(long delay) {
        timer.schedule(new Evictor(), delay, delay);
    }

    /**
     * Get a Jedis Connetion from the Pool
     *
     * @return ShardedJedis
     * @throws JedisConnectionException when can not get a jedis connection.
     */
    @Override
    public ShardedJedis getResource() throws JedisConnectionException {
        ShardedJedisPool localShardedJedisPool = shardedJedisPool;
        ShardedJedis shardedJedis = localShardedJedisPool.getResource();
        if (shardedJedis.getAllShardInfo().size() == 0) {
            throw new JedisConnectionException("Could not get a resource from the pool");
        }
        return shardedJedis;
    }

    private final class PooledObject {
        private static final int DEFAULT_MAX_HEARTBEAT_DELAY = 10000;

        private final JedisShardInfo jedisShardInfo;

        public final Jedis jedis;

        private volatile boolean isActive = true;
        private long lastHeartBeatTime = 0;

        private boolean isAuth = false;

        public PooledObject(JedisShardInfo info) {
            jedisShardInfo = info;
            jedis = new Jedis(info.getHost(), info.getPort(), timeout);
        }

        /**
         * To judge the PoolObject is Active.
         *
         * @return isActive==true
         */
        public boolean isActive() {
            return isActive;
        }

        /**
         * Ping the redis server,if ping is Ok ,modify the lastHeartBeatTime,
         */
        public void checkConnect() {
            jedis.ping();
            this.lastHeartBeatTime = System.currentTimeMillis();
        }

        public boolean checkHeartBeat() {
            boolean isChanged = false;
            long now = System.currentTimeMillis();

            if ((now <= (lastHeartBeatTime + DEFAULT_MAX_HEARTBEAT_DELAY))
                    && !isActive) {
                isActive = true;
                isChanged = true;
            } else if ((now > (lastHeartBeatTime + DEFAULT_MAX_HEARTBEAT_DELAY))
                    && isActive) {
                isActive = false;
                isChanged = true;
            }

            return isChanged;
        }

        public void connectIfNeed() {
            if (!jedis.isConnected()) {
                jedis.connect();
            }
        }

        public void authIfNeed() {
            if (jedisShardInfo.getPassword() != null && !isAuth) {
                jedis.auth(jedisShardInfo.getPassword());
                isAuth = true;
            }
        }

        public void close() {
            jedis.close();
            isAuth = false;
        }

        public JedisShardInfo getJedisShardInfo() {
            return jedisShardInfo;
        }


    }

    /**
     * Evicetor is an inner class,to check the PoolObject is active
     */
    class Evictor extends TimerTask {
        @Override
        public void run() {
            boolean isChanged = false;
            for (PooledObject pooledObject : pools) {
                try {
                    pooledObject.connectIfNeed();

                    pooledObject.authIfNeed();

                    pooledObject.checkConnect();
                } catch (Exception e) {
                    pooledObject.close();
                }

                boolean checkResult = pooledObject.checkHeartBeat();
                if (checkResult) {
                    isChanged = true;
                }
            }

            if (isChanged) {
                resetPools();
            }
        }
    }

}
