package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.service.PointsService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.ZoneId;

import static com.hmdp.utils.RedisConstants.BLOG_HOT_KEY;
import static com.hmdp.utils.RedisConstants.BLOG_HOT_LIMIT;
import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_BLOG_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_BLOG_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_BLOG_USER_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_BLOG_USER_TTL;
import static com.hmdp.utils.RedisConstants.FEED_KEY;
import static com.hmdp.utils.RedisConstants.LOCK_BLOG_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private PointsService pointsService;

    /**
     * 查询最热的笔记。
     * 排序交给 blog:hot 这个 ZSet（score = 点赞数），MySQL 不再做 order by liked 与分页 COUNT。
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        int pageSize = SystemConstants.MAX_PAGE_SIZE;
        long start = (long) (current - 1) * pageSize;
        long end = start + pageSize - 1;

        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(BLOG_HOT_KEY, start, end);
        if (tuples == null || tuples.isEmpty()) {
            // ZSet 为空可能是首次上线或 Redis 被清空，按 DB 重建一次再取，避免直接返回空列表
            if (rebuildHotRanking() == 0) {
                return Result.ok(Collections.emptyList());
            }
            tuples = stringRedisTemplate.opsForZSet().reverseRangeWithScores(BLOG_HOT_KEY, start, end);
        }
        List<Long> ids = new ArrayList<>();
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple.getValue() != null) {
                    ids.add(Long.valueOf(tuple.getValue()));
                }
            }
        }
        // 主体与作者都走批量缓存，isLike 按当前用户现算
        return Result.ok(loadBlogsForList(ids));
    }

    @Override
    public Result queryCommunityBlogs(Integer current, String topic) {
        if (current == null || current < 1) return Result.fail("页码无效");
        if (topic != null && (topic.trim().isEmpty() || topic.length() > 32)) return Result.fail("话题无效");
        Page<Blog> page = query().eq("community_visible", 1).eq(topic != null, "topic", topic)
                .orderByDesc("create_time", "id").page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> blogs = page.getRecords();
        fillBlogUsers(blogs);
        return Result.ok(blogs, page.getTotal());
    }

    @Override
    public void likeBlog(Long id) {
        // 1、判断当前用户是否已经点赞过了
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        boolean willLike = score == null;

        // 2、数据库更新点赞数量
        boolean isSuccess = willLike
                ? update().setSql("liked = liked + 1").eq("id", id).update()
                : update().setSql("liked = liked - 1").eq("id", id).update();
        if (!isSuccess) {
            return;
        }
        // 3、维护点赞关系
        if (willLike) {
            stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
        } else {
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        }
        // 4、同步热榜分数，并失效详情缓存：缓存里的 liked 已经过时
        stringRedisTemplate.opsForZSet().incrementScore(BLOG_HOT_KEY, id.toString(), willLike ? 1 : -1);
        cacheClient.evict(CACHE_BLOG_KEY + id);
    }

    /**
     * 查询笔记详情
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        // 详情缓存：互斥锁重建防击穿、空值缓存防穿透、TTL 叠加随机抖动防雪崩
        Blog blog = cacheClient.queryWithMutex(CACHE_BLOG_KEY, LOCK_BLOG_KEY, id, Blog.class,
                this::getById, CACHE_BLOG_TTL, TimeUnit.MINUTES);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        // 作者与 isLike 在缓存之外现填：isLike 是用户态字段，进共享缓存会串用户
        fillBlogUsers(Collections.singletonList(blog));
        this.isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) { // 查询笔记是否被点赞
        // 1、获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        // 2、获取用户
        Long userId = user.getId();
        // 3、查询当前用户是否点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    /**
     * 列表装配：笔记主体走批量缓存，作者走批量缓存，isLike 现算。
     * 输出顺序沿用传入的 ids（热榜是按点赞数排的）。
     */
    private List<Blog> loadBlogsForList(List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Blog> blogs = cacheClient.listWithBatchCache(CACHE_BLOG_KEY, ids, Blog.class,
                this::loadBlogsFromDb, CACHE_BLOG_TTL, TimeUnit.MINUTES);
        List<Blog> list = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Blog blog = blogs.get(id);
            if (blog != null) {
                list.add(blog);
            }
        }
        if (list.isEmpty()) {
            return list;
        }
        fillBlogUsers(list);
        list.forEach(this::isBlogLiked);
        return list;
    }

    /**
     * 笔记批量回源：未命中的 id 合并成一次 IN 查询
     */
    private Map<Long, Blog> loadBlogsFromDb(Collection<Long> ids) {
        List<Blog> found = query().in("id", ids).list();
        Map<Long, Blog> map = new HashMap<>(found.size());
        for (Blog blog : found) {
            map.put(blog.getId(), blog);
        }
        return map;
    }

    /**
     * 批量填充作者昵称与头像，替换原先每条笔记一次 getById 造成的 N+1
     */
    private void fillBlogUsers(List<Blog> blogs) {
        if (blogs == null || blogs.isEmpty()) {
            return;
        }
        List<Long> userIds = blogs.stream().map(Blog::getUserId).distinct().collect(Collectors.toList());
        Map<Long, UserDTO> users = cacheClient.listWithBatchCache(CACHE_BLOG_USER_KEY, userIds, UserDTO.class,
                this::loadUsersFromDb, CACHE_BLOG_USER_TTL, TimeUnit.MINUTES);
        for (Blog blog : blogs) {
            UserDTO user = users.get(blog.getUserId());
            if (user != null) {
                blog.setName(user.getNickName());
                blog.setIcon(user.getIcon());
            }
        }
    }

    /**
     * 作者批量回源，只取展示需要的三列，不把手机号等敏感字段放进缓存
     */
    private Map<Long, UserDTO> loadUsersFromDb(Collection<Long> userIds) {
        List<User> found = userService.query().select("id", "nick_name", "icon").in("id", userIds).list();
        Map<Long, UserDTO> map = new HashMap<>(found.size());
        for (User user : found) {
            map.put(user.getId(), BeanUtil.copyProperties(user, UserDTO.class));
        }
        return map;
    }


    @Override
    public Result queryBlogLikes(Long id) {
        // 查询前5个点赞用户
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 解析出用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        // 根据用户id查询用户
        String idStr = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    /**
     * 保存笔记信息
     * @param blog
     * @return
     */
    @Override
    @Transactional
    public Result saveBlog(Blog blog) {
        if (blog == null || blog.getTitle() == null || blog.getTitle().trim().isEmpty()
                || blog.getTitle().trim().length() > 255 || blog.getContent() == null
                || blog.getContent().trim().isEmpty() || blog.getContent().length() > 2048
                || !Arrays.asList("校园问答", "周边探店", "闲置互助", "活动搭子", "随便聊聊").contains(blog.getTopic())) {
            return Result.fail("请填写有效的标题、正文和话题");
        }
        if (blog.getImages() != null && (blog.getImages().length() > 2048 || blog.getImages().split(",").length > 9)) {
            return Result.fail("图片数量或路径超出限制");
        }
        if (blog.getImages() != null && !blog.getImages().isEmpty()) {
            for (String image : blog.getImages().split(",")) {
                if (!image.matches("/blogs/[0-9]{1,2}/[0-9]{1,2}/[0-9a-f-]{36}\\.(jpg|png|gif)")) return Result.fail("图片路径无效，请重新上传");
            }
        }
        Blog post = new Blog();
        post.setTitle(blog.getTitle().trim());
        post.setContent(blog.getContent().trim());
        post.setTopic(blog.getTopic());
        post.setImages(blog.getImages() == null ? "" : blog.getImages());
        post.setShopId(0L);
        post.setLiked(0);
        post.setComments(0);
        blog = post;
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean save = save(blog);
        if (!save) {
            return Result.fail("笔记保存失败");
        }
        pointsService.grant(user.getId(), "POST", 20, LocalDate.now(ZoneId.of("Asia/Shanghai")));
        // 新笔记进热榜，点赞数从 0 开始
        stringRedisTemplate.opsForZSet().add(BLOG_HOT_KEY, blog.getId().toString(), 0D);
        trimHotRanking();
        // 查询笔记作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", blog.getUserId()).list();
        // 推送笔记id给所有粉丝
        for (Follow follow : follows){
            // 获取粉丝id
            String key = FEED_KEY + follow.getUserId();
            // 推送笔记id给粉丝
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    /**
     * 查询笔记列表关注（滚动分页）
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1、获取当前用户
        Long userId = UserHolder.getUser().getId();
        // 2、查询收件箱
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0; // 获取最小时间戳
        int os =1; // 记录偏移量
        // 3、解析数据：笔记id、分数（时间）
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples){
            String id = typedTuple.getValue();
            if (id != null) {
                ids.add(Long.valueOf(id));
            }
            long time = typedTuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        // 4、根据id装配笔记列表（主体与作者都走批量缓存）
        List<Blog> blogs = loadBlogsForList(ids);

        // 5、封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setMinTime(minTime);
        r.setOffset(os);

        return Result.ok(r);
    }

    /**
     * 按 DB 的 liked 重建热榜 ZSet，用于首次上线或 Redis 数据丢失后恢复。
     * @return 写入 ZSet 的条目数
     */
    @Override
    public int rebuildHotRanking() {
        List<Blog> top = query().select("id", "liked")
                .orderByDesc("liked")
                .last("LIMIT " + BLOG_HOT_LIMIT)
                .list();
        if (top.isEmpty()) {
            return 0;
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>(top.size());
        for (Blog blog : top) {
            Integer liked = blog.getLiked();
            tuples.add(new DefaultTypedTuple<>(String.valueOf(blog.getId()), (double) (liked == null ? 0 : liked)));
        }
        stringRedisTemplate.delete(BLOG_HOT_KEY);
        Long added = stringRedisTemplate.opsForZSet().add(BLOG_HOT_KEY, tuples);
        return added == null ? 0 : added.intValue();
    }

    /**
     * 热榜只保留分数最高的 BLOG_HOT_LIMIT 条，避免无界增长
     */
    private void trimHotRanking() {
        Long size = stringRedisTemplate.opsForZSet().size(BLOG_HOT_KEY);
        if (size != null && size > BLOG_HOT_LIMIT) {
            stringRedisTemplate.opsForZSet().removeRange(BLOG_HOT_KEY, 0, size - BLOG_HOT_LIMIT - 1);
        }
    }
}
