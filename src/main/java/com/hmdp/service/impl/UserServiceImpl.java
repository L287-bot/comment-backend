package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 *  
 *   
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
   @Resource
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone))
        {    //不符合返回错误信息
            return Result.fail("手机号格式错误");
        }
        //符合发送验证码
        String code= RandomUtil.randomNumbers(6);
//        //保存验证码到Session
//        session.setAttribute("code",code);
        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY+phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //发送验证码
        log.info("发送验证码:{}",code);
        //返回ok
        return  Result.ok();
    }

    @Override
    public Result login(String phone, String code, String password, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone))
        {
            return Result.fail("手机号格式错误");
        }
        String checkCode=stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY +phone);
        if (checkCode==null||!checkCode.equals(code))
        {
            return Result.fail("验证码错误");
        }
        //根据手机号查询用户
        QueryWrapper<User> userQueryWrapper=new QueryWrapper<>();
        userQueryWrapper.eq("phone",phone);
        User loginUser=baseMapper.selectOne(userQueryWrapper);
        //判断用户是否存在
        if (loginUser==null)
        {  //创建
           loginUser=createUserWithPhone(phone);
        }
        //保存登录的用户信息
        //随机生成token，作为登录令牌
        String token= UUID.randomUUID().toString(true);
        //将User对象转为Hash存储
      UserDTO userDTO=  BeanUtil.copyProperties(loginUser, UserDTO.class);
      //转换成map
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString())
                );
        //存储
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+token,stringObjectMap);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,RedisConstants.LOGIN_USER_TTL,TimeUnit.MINUTES);
        //返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //不存在，创建新用户并保存
        User newUser=new User();
        newUser.setNickName(SystemConstants.USER_NICK_NAME_PREFIX +RandomUtil.randomString(5));
        newUser.setPhone(phone);
        this.save(newUser);
        return newUser;
    }
}
