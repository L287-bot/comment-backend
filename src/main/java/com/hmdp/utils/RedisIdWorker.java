package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private  static  final  long BEGIN_TIMESTAMP=1640995200L;
    private static final int COUNT_BIT=32;

    private StringRedisTemplate stringRedisTemplate;
    public RedisIdWorker (StringRedisTemplate stringRedisTemplate)
    {
        this.stringRedisTemplate=stringRedisTemplate;
    }

    public long nextId(String keyPrefix)
    {
        //生成时间戳
        LocalDateTime now=LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //生成序列号
        //获取当天日期精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        //拼接并返回   时间戳移位32位，低位全是0，使用或运算count是什么就是什么，可以实现拼接
        return  timestamp<<COUNT_BIT|count;

    }

}
