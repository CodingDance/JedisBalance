# JedisBalance
redis load balance，use java jedis api to implemt redis loadbalance。Include FixedJedisPool And SharedJedisPool。

FixJedisPoll use fix numbers of jedis pool 。Every pool pointer to a redis-server。we use a TimeTask thread schedule to check the connection is alive。when  a redis server crushed，the connection will be not alive，we will use roudrobin algorithm to pass the usrs' request to another redis-server implement loadbalance and HA.

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


SharedJedisPool:
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
