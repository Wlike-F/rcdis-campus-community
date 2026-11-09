package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.utils.UserHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Service
public class RewardService {
    @Resource
    private JdbcTemplate jdbcTemplate;

    public Result catalog() {
        Long userId = UserHolder.getUser().getId();
        List<Map<String, Object>> rewards = jdbcTemplate.queryForList("SELECT p.id, p.code, p.name, p.description, p.price, p.stock, EXISTS(SELECT 1 FROM community_redemption r WHERE r.reward_id = p.id AND r.user_id = ?) AS owned FROM community_reward p WHERE p.active = 1 ORDER BY p.price, p.id", userId);
        return Result.ok(rewards);
    }

    public Result redemptions() {
        return Result.ok(jdbcTemplate.queryForList("SELECT r.id, r.cost, r.created_at AS createdAt, p.code, p.name, p.description FROM community_redemption r JOIN community_reward p ON p.id = r.reward_id WHERE r.user_id = ? ORDER BY r.id DESC", UserHolder.getUser().getId()));
    }

    @Transactional
    public Result redeem(Long rewardId, String requestId) {
        if (rewardId == null || rewardId <= 0 || requestId == null || !requestId.matches("[0-9a-fA-F-]{36}")) return Result.fail("兑换请求无效，请重试");
        Long userId = UserHolder.getUser().getId();
        List<Long> users = jdbcTemplate.queryForList("SELECT id FROM tb_user WHERE id = ? FOR UPDATE", Long.class, userId);
        if (users.isEmpty()) return Result.fail("用户不存在，请重新登录");
        List<Map<String, Object>> previous = jdbcTemplate.queryForList("SELECT id, reward_id AS rewardId FROM community_redemption WHERE user_id = ? AND request_id = ?", userId, requestId);
        if (!previous.isEmpty()) {
            if (((Number) previous.get(0).get("rewardId")).longValue() != rewardId) return Result.fail("兑换请求编号已使用");
            return Result.ok(previous.get(0));
        }
        List<Map<String, Object>> products = jdbcTemplate.queryForList("SELECT id, price, stock FROM community_reward WHERE id = ? AND active = 1 FOR UPDATE", rewardId);
        if (products.isEmpty()) return Result.fail("该装扮已下架");
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM community_redemption WHERE user_id = ? AND reward_id = ?", Integer.class, userId, rewardId) > 0) return Result.fail("你已经拥有这个装扮");
        Map<String, Object> product = products.get(0);
        int cost = ((Number) product.get("price")).intValue();
        if (((Number) product.get("stock")).intValue() < 1) return Result.fail("装扮已兑完");
        Long earned = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(points), 0) FROM community_points_log WHERE user_id = ?", Long.class, userId);
        Long spent = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(cost), 0) FROM community_redemption WHERE user_id = ?", Long.class, userId);
        if (earned - spent < cost) return Result.fail("积分不足，完成每日任务后再来吧");
        if (jdbcTemplate.update("UPDATE community_reward SET stock = stock - 1 WHERE id = ? AND active = 1 AND stock > 0", rewardId) != 1) throw new IllegalStateException("Reward stock changed during redemption: rewardId=" + rewardId);
        jdbcTemplate.update("INSERT INTO community_redemption (user_id, reward_id, request_id, cost) VALUES (?, ?, ?, ?)", userId, rewardId, requestId, cost);
        return Result.ok();
    }

    @Transactional
    public Result useTheme(String code) {
        Long userId = UserHolder.getUser().getId();
        if (code == null || !code.matches("forest|ginkgo|spring|night")) return Result.fail("主题不存在");
        if (!"forest".equals(code) && jdbcTemplate.queryForObject("SELECT COUNT(*) FROM community_redemption r JOIN community_reward p ON p.id = r.reward_id WHERE r.user_id = ? AND p.code = ?", Integer.class, userId, code) == 0) return Result.fail("请先兑换该主题");
        jdbcTemplate.update("INSERT INTO community_profile_theme (user_id, theme_code) VALUES (?, ?) ON DUPLICATE KEY UPDATE theme_code = VALUES(theme_code)", userId, code);
        return Result.ok();
    }
}
