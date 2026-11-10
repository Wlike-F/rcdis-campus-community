package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.service.PointsService;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private PointsService pointsService;

    @Resource
    private StringRedisTemplate redis;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.login(loginForm, session);
    }


    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpServletRequest request){
        return userService.logout(request);
    }

    @GetMapping("/me")
    public Result me(HttpServletRequest request){
        User user = userService.getById(UserHolder.getUser().getId());
        if (user == null) return Result.fail("用户不存在");
        UserDTO dto = BeanUtil.copyProperties(user, UserDTO.class);
        String token = request.getHeader("authorization");
        if (token != null && !token.isEmpty()) {
            redis.opsForHash().put(RedisConstants.LOGIN_USER_KEY + token, "nickName", dto.getNickName() == null ? "" : dto.getNickName());
            redis.opsForHash().put(RedisConstants.LOGIN_USER_KEY + token, "icon", dto.getIcon() == null ? "" : dto.getIcon());
        }
        return Result.ok(dto);
    }

    @PutMapping("/profile")
    public Result updateProfile(@RequestBody ProfileRequest request, HttpServletRequest httpRequest) {
        if (request == null || request.nickName == null || request.nickName.trim().isEmpty()
                || request.nickName.trim().length() > 32 || request.nickName.chars().anyMatch(Character::isISOControl)) {
            return Result.fail("昵称须为1至32个字符，且不能包含控制字符");
        }
        String icon = request.icon == null ? "" : request.icon;
        Long id = UserHolder.getUser().getId();
        User current = userService.getById(id);
        if (current == null) return Result.fail("用户不存在");
        if (!icon.equals(current.getIcon()) && !icon.isEmpty() && (!icon.matches("/blogs/[0-9]{1,2}/[0-9]{1,2}/[0-9a-f-]{36}\\.(jpg|png|gif)")
                || !new File(SystemConstants.IMAGE_UPLOAD_DIR, icon).isFile())) {
            return Result.fail("头像无效，请重新上传");
        }
        boolean updated = userService.update().eq("id", id).set("nick_name", request.nickName.trim()).set("icon", icon).update();
        if (!updated) return Result.fail("保存失败，请重试");
        redis.delete(RedisConstants.CACHE_BLOG_USER_KEY + id);
        return me(httpRequest);
    }

    public static class ProfileRequest {
        public String nickName;
        public String icon;
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 查询用户信息
     * @param userId
     * @return
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }

    /**
     * 签到功能
     * @return
     */
    @PostMapping("/sign")
    public Result sign() {
        return pointsService.sign();
    }

    @GetMapping("/points")
    public Result points() {
        return pointsService.current();
    }

    /**
     * 统计签到统计功能
     * @return
     */
    @GetMapping("/sign/count")
    public Result signCount() {
        return userService.signCount();
    }
}
