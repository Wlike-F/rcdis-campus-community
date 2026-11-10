package com.hmdp.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.RegexUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@Service
public class EmailAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_PREFIX = "auth:email:code:";
    private static final String RATE_PREFIX = "auth:email:rate:";
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    @Resource private StringRedisTemplate redis;
    @Resource private JavaMailSender mailSender;
    @Resource private IUserService users;
    @Value("${spring.mail.username}") private String sender;
    @Value("${spring.mail.password}") private String mailPassword;

    public Result sendCode(String address) {
        String email = normalize(address);
        if (email == null) return Result.fail("请输入有效邮箱地址");
        if (mailPassword == null || mailPassword.trim().isEmpty()) return Result.fail("邮件服务尚未配置，请联系管理员");
        String rate = RATE_PREFIX + email;
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(rate, "1", 60, TimeUnit.SECONDS))) return Result.fail("发送过于频繁，请稍后再试");
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1000000));
        redis.opsForValue().set(CODE_PREFIX + email, code, 5, TimeUnit.MINUTES);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(email);
        message.setSubject("RCDIS 校园社区验证码");
        message.setText("您的验证码是：" + code + "。有效期 5 分钟，请勿泄露给他人。");
        try { mailSender.send(message); }
        catch (MailException exception) {
            redis.delete(CODE_PREFIX + email);
            redis.delete(rate);
            throw new IllegalStateException("Failed to send verification email", exception);
        }
        return Result.ok();
    }

    public Result emailLogin(String address, String code) {
        String email = normalize(address);
        if (email == null || !allowAttempt(email) || !consume(email, code)) return Result.fail("邮箱或验证码错误，或尝试过于频繁");
        User user = users.query().eq("email", email).one();
        if (user == null) {
            user = new User().setEmail(email).setNickName("user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            try { users.save(user); }
            catch (DuplicateKeyException exception) { user = users.query().eq("email", email).one(); }
        }
        if (user == null) throw new IllegalStateException("Failed to create email account");
        return issueToken(user);
    }

    public Result passwordLogin(String address, String password) {
        String email = normalize(address);
        if (email == null || password == null || password.getBytes(StandardCharsets.UTF_8).length > 72 || !allowAttempt(email)) return Result.fail("邮箱或密码错误，或尝试过于频繁");
        User user = users.query().eq("email", email).one();
        if (user == null || user.getPassword() == null || !user.getPassword().startsWith("$2") || !encoder.matches(password, user.getPassword())) return Result.fail("邮箱或密码错误");
        return issueToken(user);
    }

    @Transactional
    public Result resetPassword(String address, String code, String password) {
        String email = normalize(address);
        if (email == null || password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) return Result.fail("请输入有效邮箱和8至72位密码");
        if (!allowAttempt(email) || !consume(email, code)) return Result.fail("验证码错误或尝试过于频繁");
        User user = users.query().eq("email", email).one();
        if (user == null) {
            user = new User().setEmail(email).setNickName("user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)).setPassword(encoder.encode(password));
            users.save(user);
        } else {
            user.setPassword(encoder.encode(password));
            users.updateById(user);
        }
        return issueToken(user);
    }

    private boolean allowAttempt(String email) {
        String key = "auth:email:attempt:" + email;
        Long attempts = redis.opsForValue().increment(key);
        if (Long.valueOf(1).equals(attempts)) redis.expire(key, 10, TimeUnit.MINUTES);
        return attempts != null && attempts <= 10;
    }

    private boolean consume(String email, String code) {
        if (code == null || !code.matches("[0-9]{6}")) return false;
        return Long.valueOf(1).equals(redis.execute(CONSUME, Collections.singletonList(CODE_PREFIX + email), code));
    }

    private String normalize(String value) {
        if (value == null) return null;
        String email = value.trim().toLowerCase(Locale.ROOT);
        return email.length() <= 190 && !RegexUtils.isEmailInvalid(email) ? email : null;
    }

    private Result issueToken(User user) {
        String token = UUID.randomUUID().toString();
        UserDTO dto = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> fields = BeanUtil.beanToMap(dto, new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true)
                .setFieldValueEditor((name, value) -> value.toString()));
        redis.opsForHash().putAll(LOGIN_USER_KEY + token, fields);
        redis.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.SECONDS);
        return Result.ok(token);
    }
}
