package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class RedisSimpleLock implements ILock{

    private StringRedisTemplate redisTemplate;

    private  String name;
    private  static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    public RedisSimpleLock(StringRedisTemplate redisTemplate, String name) {
        this.redisTemplate = redisTemplate;
        this.name = name;
    }

    private static final String KEY_PREFIX="lock";
    private static final String ID_PREFIX= UUID.randomUUID().toString(true)+"-";
    @Override
    public boolean tryLock(long timeoutSec) {
        //获得线程标识
        String threadId =ID_PREFIX+ Thread.currentThread().getId();
        //获取锁
     Boolean flag=   redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name,threadId,timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

//    @Override
//    public void unLock() {
//        //获取线程标识
//      String ThreadId=  ID_PREFIX+ Thread.currentThread().getId();
//        //获取锁中的线程标识
//        String id = redisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if (ThreadId.equals(id))
//        {
//            redisTemplate.delete(KEY_PREFIX+name);
//        }
//
//
//    }
@Override
public void unLock() {
//调用lua脚本
redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX+name),ID_PREFIX+Thread.currentThread().getId());

}
}
