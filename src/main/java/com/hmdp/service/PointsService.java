package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.USER_SIGN_KEY;

@Service
public class PointsService {
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public boolean grant(Long userId, String action, int points, LocalDate date) {
        int inserted = jdbcTemplate.update("INSERT IGNORE INTO community_points_log (user_id, action, business_date, points) VALUES (?, ?, ?, ?)",
                userId, action, Date.valueOf(date), points);
        return inserted == 1;
    }

    @Transactional
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        if (!grant(userId, "SIGN", 10, today)) return Result.fail("今天已经签到");
        String key = USER_SIGN_KEY + userId + ":" + today.getYear() + today.getMonthValue();
        stringRedisTemplate.opsForValue().setBit(key, today.getDayOfMonth() - 1, true);
        return Result.ok(summary(userId, today));
    }

    public Map<String, Object> summary(Long userId, LocalDate today) {
        Long total = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(points), 0) FROM community_points_log WHERE user_id = ?", Long.class, userId);
        Long spent = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(cost), 0) FROM community_redemption WHERE user_id = ?", Long.class, userId);
        List<Map<String, Object>> todayActions = jdbcTemplate.queryForList("SELECT action FROM community_points_log WHERE user_id = ? AND business_date = ?", userId, Date.valueOf(today));
        boolean signed = todayActions.stream().anyMatch(item -> "SIGN".equals(item.get("action")));
        boolean posted = todayActions.stream().anyMatch(item -> "POST".equals(item.get("action")));
        List<Map<String, Object>> records = jdbcTemplate.queryForList("SELECT action, points, businessDate, description FROM (SELECT action, points, business_date AS businessDate, '' AS description, created_at AS event_time, id FROM community_points_log WHERE user_id = ? UNION ALL SELECT 'REDEEM' AS action, -CAST(r.cost AS SIGNED) AS points, DATE(r.created_at) AS businessDate, p.name AS description, r.created_at AS event_time, r.id FROM community_redemption r JOIN community_reward p ON p.id = r.reward_id WHERE r.user_id = ?) entries ORDER BY event_time DESC, id DESC LIMIT 20", userId, userId);
        List<String> themes = jdbcTemplate.queryForList("SELECT theme_code FROM community_profile_theme WHERE user_id = ?", String.class, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("points", total - spent);
        result.put("growth", total);
        result.put("theme", themes.isEmpty() ? "forest" : themes.get(0));
        result.put("signedToday", signed);
        result.put("postedToday", posted);
        result.put("records", records);
        return result;
    }

    @Transactional(readOnly = true)
    public Result current() {
        return Result.ok(summary(UserHolder.getUser().getId(), LocalDate.now(ZoneId.of("Asia/Shanghai"))));
    }
}
