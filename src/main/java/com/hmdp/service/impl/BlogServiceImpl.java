package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 *  
 *   
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryBlogById(Long id) {
        //查询blog
        Blog blog = getById(id);
        if (blog==null)
        {
            return Result.fail("笔记不存在");
        }
        //查询blog有关的用户
        queryBlogUser(blog);
        isBlogLike(blog);
        return Result.ok(blog);
    }

    private void isBlogLike(Blog blog) {
        //1.获取登录用户
        Long loginUserId = UserHolder.getUser().getId();
        //2.判断当天登录用户是否已经点赞
        String key=BLOG_LIKED_KEY+blog.getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, loginUserId.toString());
        blog.setIsLike(BooleanUtil.isTrue(isMember));
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    @Override
    public Result queryHotBolg(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
        this.queryBlogUser(blog);
        this.isBlogLike(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //1.获取登录用户
        Long loginUserId = UserHolder.getUser().getId();
        //2.判断当天登录用户是否已经点赞
        String key=BLOG_LIKED_KEY+loginUserId;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, loginUserId.toString());
        if(BooleanUtil.isFalse(isMember))
        {
            //3.如果未点赞可以点赞
            //3.1 数据库点赞数+1
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
            //3.2保存用户到Redis Set集合
            if(isSuccess)
            {
                stringRedisTemplate.opsForSet().add(key,loginUserId.toString());
            }
        }

        //4.如果已经点赞
        //4.1取消点赞数据库点赞数-1
        boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
        //4.2把用户从Redis Set集合中移除
        if (isSuccess)
        {
            stringRedisTemplate.opsForSet().remove(key,loginUserId.toString());
        }
        return Result.ok();
    }
}
