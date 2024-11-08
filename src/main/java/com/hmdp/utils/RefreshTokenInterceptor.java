package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {
   private StringRedisTemplate stringRedisTemplate;

   public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate)
   {
       this.stringRedisTemplate=stringRedisTemplate;
   }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
       //获取请求头中的token
        String token=request.getHeader("authorization");
        //基于token从redis中获取用户
      if(StrUtil.isBlank(token))
      {

          return true;
      }
      String key=RedisConstants.LOGIN_USER_KEY+token;
       Map<Object,Object> userMap =stringRedisTemplate.opsForHash().entries(key);
        //判断用户是否存在
        if (userMap.isEmpty())
        {
            return true;
        }
        //将查询到的hash对象转换成UserDTO对象
        UserDTO user= BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);
        //存在保存用户信息到ThreadLocal
        UserHolder.saveUser(user);
        //刷新token有效期
        stringRedisTemplate.expire(token,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
       //移除用户
        UserHolder.removeUser();
    }
}
