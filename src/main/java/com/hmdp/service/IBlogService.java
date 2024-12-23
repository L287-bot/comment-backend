package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 *  
 *   
 */
public interface IBlogService extends IService<Blog> {

    Result queryBlogById(Long id);


    Result queryHotBolg(Integer current);

    Result likeBlog(Long id);
}
