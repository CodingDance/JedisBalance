package com.netease.ncr.jedisBalance;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

import com.google.common.collect.ImmutableList;

/**
 * JedisFixedPool provided the function for you to get a active jedis connection from some Redis Server and
 * to ensure the load balancing of the Servers.
 * <p>
 * JedisFixedPool is a pool contains many PoolObject,every PoolObject is a poolwhich contains some redis
 * connection to a Redis Server.</p>
 * <p>
 * JedisFixedPool used the Round-Robin Scheduling algorithm to get an active jedis connection from some Redis
 * Server andto ensure the load balancing of the Servers.
 * JedisFixedPool used the Evictor thread {@link com.netease.ncr.jedisBalance.JedisFixedPool.Evictor} to ping
 * everyPoolObject of the JedisFixedPool,if the PoolObject is active,we can ensure the Redis Server is active,
 * so we can get a Jedis connection from this PoolObject; otherwise we skip this PoolObject until it's channged
 * to active.
 * </p>
 * <p>Example:
 * <example>
 *             JedisPoolConfig poolConfig = new JedisPoolConfig();
 *             poolConfig.setMinIdle(5);
 *             poolConfig.setMaxTotal(8);
 *             List<String> addressList = new ArrayList<String>();
 *             addressList.add("10.180.156.74:379");
 *             addressList.add("10.180.156.78:6379");
 *             IJedisPool pool = new JedisFixedPool(poolConfig, addressList, "123456");
 *             Jedis jedis = null;
 *             try {
 *                   jedis = pool.getResource();
 *                   jedis.set("key","value");
 *                }catch (JedisConnectionException e) {
 *                   e.printStackTrace();
 *                } finally {
 *                   if(jedis!=null){
 *                        jedis.close();
 *                    }
 *                }
 * </example>
 * </p>
 *
 * @author hzweizijun
 * @date 2015年8月24日 下午3:55:56
 */
public class JedisFixedPool implements IJedisPool {
    private static final Logger logger = Logger.getLogger(JedisFixedPool.class);
    private static final int DEFAULT_TIMER_DELAY = 1000;

    private volatile ImmutableList<PooledObject> pools = ImmutableList.of();

    private final JedisPoolConfig poolConfig;
    private int timeout = Protocol.DEFAULT_TIMEOUT;
    private String password = null;

    private final AtomicInteger nextIdx = new AtomicInteger(-1);

    private Timer timer = new Timer();

    private int delay = DEFAULT_TIMER_DELAY;

    /**
     * Init the JedisFixedPool
     *
     * @param poolConfig  jedis poolconfig
     * @param addressList a list contains the Redis Server host string info,example:
     *                    List<String> addressList = new ArrayList<String>();
     *                    addressList.add("127.0.0.1:6379");
     *                    addressList.add("127.0.0.2:6379");
     * @param timeout     set the connetion timeout,and socket timeout.
     * @param password    jedis password
     */
    public JedisFixedPool(JedisPoolConfig poolConfig, List<String> addressList, int timeout, String password) {
        this.poolConfig = poolConfig;
        this.timeout = timeout;
        this.password = password;
        initPool(addressList, timeout, password);
        startEvictor(delay);
    }

    /**
     * Init the JedisFixedPool without set password.
     * the constructor is linked with {@link #JedisFixedPool(JedisPoolConfig, List, int, String)}
     *
     * @param poolConfig  jedis poolconfig
     * @param addressList server host list
     * @param timeout     the connetion timeout,and socket timeout.
     */
    public JedisFixedPool(JedisPoolConfig poolConfig, List<String> addressList, int timeout) {
        this(poolConfig, addressList, timeout, null);
    }

    /**
     * Init the JedisFixedPool without set password and timeout time,uesd default timeout Protocol.DEFAULT_TIMEOUT{@link Protocol},
     * the constructor is linked with {@link #JedisFixedPool(JedisPoolConfig, List, int, String)}
     *
     * @param addressList server host list
     * @param poolConfig  jedis poolconfig
     */
    public JedisFixedPool(JedisPoolConfig poolConfig, List<String> addressList) {
        this(poolConfig, addressList, Protocol.DEFAULT_TIMEOUT, null);
    }

    /**
     * Init the JedisFixedPool without set password and timeout time,uesd default timeout Protocol.DEFAULT_TIMEOUT {@link Protocol},
     * the constructor is linked with {@link #JedisFixedPool(JedisPoolConfig, List, int, String)}
     * @param poolConfig  jedis poolconfig
     * @param addressList server host list
     * @param password    jedis password
     */
    public JedisFixedPool(JedisPoolConfig poolConfig, List<String> addressList, String password) {
        this(poolConfig, addressList, Protocol.DEFAULT_TIMEOUT, password);
    }


    private void initPool(List<String> addressList, int timeout, String password) {
        ImmutableList.Builder<PooledObject> builder = ImmutableList.builder();
        for (String address : addressList) {

            String[] arr = address.split(":");
            if (arr.length != 2) {
                throw new JedisException("address invalid:" + address);
            }
            String host = arr[0];
            int port = Integer.valueOf(arr[1]);
            JedisPool pool = new JedisPool(poolConfig, host, port, timeout, password);
            PooledObject pooledObject = new PooledObject(host, port, pool);
            builder.add(pooledObject);
        }
        this.pools = builder.build();
    }

    private void startEvictor(long delay) {
        timer.schedule(new Evictor(), delay, delay);
    }


    /**
     * Get a Jedis Connetion from the Pool
     *
     * @return jedis
     * @throws JedisConnectionException when can not get a jedis connection.
     */
    @Override
    public Jedis getResource() throws JedisConnectionException {
        ImmutableList<PooledObject> pools = this.pools;
        if (pools.isEmpty()) {
            throw new JedisException("Proxy list empty");
        }

        int getCount = 0;
        int poolSize = pools.size();
        /**
         * loop to get a active connection,if every PoolObject is not active ,throw JedisConnectionException
         */
        for (; ; ) {
            int current = nextIdx.get();
            int next = current >= poolSize - 1 ? 0 : current + 1;
            if (nextIdx.compareAndSet(current, next)) {
                getCount++;
                PooledObject pooledObject = pools.get(next);
                if (pooledObject.isActive()) {
                    try {
                        return pooledObject.pool.getResource();
                    } catch (Exception e) {
                        logger.warn("get node error,node host:" + pooledObject.host + ", port:" + pooledObject.port, e);
                    }
                }

                if (getCount >= poolSize) {
                    throw new JedisConnectionException("Could not get a resource from the pool");
                }
            }
        }
    }

    /**
     * PoolObject contains a redis object pool
     */
    private final class PooledObject {
        /**
         * DEFAULT_MAX_HEARTBEAT_DELAY uesd for the Evictor thread to ensure
         * this poolobject is active.
         */
        private static final int DEFAULT_MAX_HEARTBEAT_DELAY = 10000;

        public final String host;
        public final int port;

        public final JedisPool pool;

        public final Jedis jedis;

        private volatile boolean isActive = true;
        private long lastHeartBeatTime = 0;

        private boolean isAuth = false;

        /**
         * @param host the redis server's ip
         * @param port the redis server's port
         * @param pool the config of JedisPool
         */
        public PooledObject(String host, int port, JedisPool pool) {
            this.host = host;
            this.port = port;
            this.pool = pool;
            /**
             * this jedis is uesd for Evictor thread
             */
            jedis = new Jedis(host, port, timeout);
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

        /**
         * Check HeartBeat of PoolObject ,ensure the state of the PoolObject,
         * if the state ischanged,evict the PoolObject of the JedisFixedPool
         *
         * @return isc
         */
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

        /**
         * Auth the jedis,if true modify the Parameter {isAuth} to true.
         */
        public void authIfNeed() {
            if (password != null && !isAuth) {
                jedis.auth(password);
                isAuth = true;
            }
        }

        /**
         * Close the jedis connection.
         */
        public void close() {
            jedis.close();
            isAuth = false;
        }


    }




    /**
     * Evicetor is an inner class,to check the PoolObject is active
     */
    class Evictor extends TimerTask {

        @Override
        public void run() {
            for (PooledObject pooledObject : pools) {
                try {
                    pooledObject.connectIfNeed();
                    pooledObject.authIfNeed();
                    pooledObject.checkConnect();
                } catch (Exception e) {
                    pooledObject.close();
                }
                pooledObject.checkHeartBeat();
            }
        }
    }
}
