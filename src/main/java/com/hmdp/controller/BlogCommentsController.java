package com.hmdp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {
    @Resource private IBlogCommentsService commentsService;
    @Resource private IBlogService blogService;
    @Resource private IUserService userService;

    @GetMapping("/list/{blogId}")
    public Result list(@PathVariable Long blogId, @RequestParam Integer current) {
        if (blogId == null || blogId <= 0 || current == null || current < 1 || current > 10000) return Result.fail("评论查询参数无效");
        Page<BlogComments> page = commentsService.query().eq("blog_id", blogId).eq("status", 0)
                .orderByDesc("create_time", "id").page(new Page<>(current, 10));
        List<BlogComments> records = page.getRecords();
        List<Long> ids = records.stream().map(BlogComments::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> users = ids.isEmpty() ? Collections.emptyMap() : userService.listByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        List<Map<String, Object>> result = records.stream().map(comment -> {
            Map<String, Object> item = new LinkedHashMap<>();
            User user = users.get(comment.getUserId());
            item.put("id", comment.getId());
            item.put("name", user == null ? "校园同学" : user.getNickName());
            item.put("content", comment.getContent());
            item.put("createTime", comment.getCreateTime());
            return item;
        }).collect(Collectors.toList());
        return Result.ok(result, page.getTotal());
    }

    @PostMapping
    public Result create(@RequestBody CommentRequest request) {
        if (request == null || request.blogId == null || request.blogId <= 0 || request.content == null
                || request.content.trim().isEmpty() || request.content.trim().length() > 255) return Result.fail("评论内容须为1至255个字符");
        Blog blog = blogService.getById(request.blogId);
        if (blog == null) return Result.fail("帖子不存在");
        BlogComments comment = new BlogComments().setBlogId(request.blogId).setUserId(UserHolder.getUser().getId())
                .setParentId(0L).setAnswerId(0L).setContent(request.content.trim()).setLiked(0).setStatus(false);
        if (!commentsService.save(comment)) return Result.fail("评论发布失败，请重试");
        return Result.ok(comment.getId());
    }

    public static class CommentRequest {
        public Long blogId;
        public String content;
    }
}
