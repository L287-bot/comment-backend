package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 *  
 *   
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
   @Resource
   private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
//        //从redis查询商户信息
//        String shopMessage=stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);
//        //判断是否存在
//        if (StrUtil.isNotBlank(shopMessage))
//        {
//            //存在直接返回
//            Shop shop = JSONUtil.toBean(shopMessage, Shop.class);
//           return Result.ok(shop);
//        }
//        //判断命中是的否是空值
//        if (shopMessage!=null)
//        {
//            return Result.fail("店铺不存在");
//        }
//        //不存在 查询数据库
//        Shop shop=baseMapper.selectById(id);
//        if (shop==null)
//        {    //存入空值
//            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//           //返回错误信息
//            return  Result.fail("商铺不存在");
//        }
//        //存在写入缓存
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //返回给前端
//        return Result.ok(shop);


        //缓存穿透
//        Shop shop =queryWithPathThrough(id);

//        //互斥锁解决缓存穿透
//        Shop shop=queryWithMutex(id);

        Shop shop=queryWithLogicExpire(id);
        if (shop==null)
        {
            return  Result.fail("店铺不存在!");
        }
        return Result.ok(shop);
    }

    /**
     * 互斥锁
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id)  {
        //从redis查询商户信息
        String shopMessage=stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);
        //判断是否存在
        if (StrUtil.isNotBlank(shopMessage))
        {
            //存在直接返回
            Shop shop = JSONUtil.toBean(shopMessage, Shop.class);
            return shop;
        }
        //判断命中是的否是空值
        if (shopMessage!=null)
        {
            return null;
        }
        //未命中缓存，也没命中空值，那是真的未命中了bro
        //开始实现缓存重建

        Shop shop= null;
        try {   //获取互斥锁
            boolean isLock= tryLock(RedisConstants.LOCK_SHOP_KEY+id);
            //是否成功
            if(!isLock)
            {
                //失败，则休眠重试
                Thread.sleep(50);
             return    queryWithMutex(id);
            }
            //成功根据id查询数据库
            shop = baseMapper.selectById(id);
            if (shop==null)
            {    //存入空值
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回错误信息
                return  null;
            }
            //存在写入缓存
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            unLock(RedisConstants.LOCK_SHOP_KEY+id);
        }
        //返回给前端
        return shop;
    }
public void saveShopToRedis(Long id,Long expireSeconds)
 {
     //查询店铺数据
     Shop shop=baseMapper.selectById(id);
     //封装逻辑过期时间
     RedisData redisData=new RedisData();
     redisData.setData(shop);
     redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
     stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop));
 }
 private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期
     * @param id
     * @return
     */
    public Shop queryWithLogicExpire(Long id)
    {
        //从redis查询商户信息
        String shopMessage=stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);
        //判断是否存在
        if (StrUtil.isBlank(shopMessage))
        {
            //未命中直接返回
            return null;
        }
        //命中，需要json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopMessage, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now()))
        {
            //未过期，直接返回店铺信息
            return shop;
        }

        //已过期，需要构建缓存
        //获取互斥锁
        boolean isLock= tryLock(RedisConstants.LOCK_SHOP_KEY+id);
        //判断是否获取锁成功
        if (isLock)
        {
            //成功，开启独立线程 ，开始重建
            try {
                CACHE_REBUILD_EXECUTOR.submit(()->this.saveShopToRedis(id,30L));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }finally {
                //释放锁
                unLock(RedisConstants.LOCK_SHOP_KEY+id);
            }

        }
        //失败，返回过期店铺信息
        return shop;

    }
    public Shop queryWithPathThrough(Long id)
    {
        //从redis查询商户信息
        String shopMessage=stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY+id);
        //判断是否存在
        if (StrUtil.isNotBlank(shopMessage))
        {
            //存在直接返回
            Shop shop = JSONUtil.toBean(shopMessage, Shop.class);
            return shop;
        }
        //判断命中是的否是空值
        if (shopMessage!=null)
        {
            return null;
        }
        //不存在 查询数据库
        Shop shop=baseMapper.selectById(id);
        if (shop==null)
        {    //存入空值
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return  null;
        }
        //存在写入缓存
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回给前端
        return shop;
    }


    /**
     * 尝试获得锁
     * @param key
     * @return
     */
    private boolean tryLock(String key)
    {
       Boolean flag=  stringRedisTemplate.opsForValue().setIfAbsent(key,"1",RedisConstants.LOCK_SHOP_TTL,TimeUnit.SECONDS);
       return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unLock(String key)
    {
        stringRedisTemplate.delete(key);
    }


    @Override
    @Transactional
    public Result updateByIdWithCache(Shop shop) {
    //更新数据库
        if (shop.getId()<=0)
        {
            return Result.fail("店铺id错误");
        }
        this.updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+shop.getId());
        return null;
    }
}
