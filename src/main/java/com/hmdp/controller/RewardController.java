package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.RewardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/rewards")
public class RewardController {
    @Resource
    private RewardService rewardService;

    @GetMapping
    public Result catalog() { return rewardService.catalog(); }

    @GetMapping("/mine")
    public Result mine() { return rewardService.redemptions(); }

    @PostMapping("/redeem")
    public Result redeem(@RequestBody Map<String, Object> body) {
        Object value = body.get("rewardId");
        if (!(value instanceof Number)) return Result.fail("兑换商品无效");
        Object requestId = body.get("requestId");
        return rewardService.redeem(((Number) value).longValue(), requestId instanceof String ? (String) requestId : null);
    }

    @PostMapping("/theme")
    public Result theme(@RequestBody Map<String, String> body) { return rewardService.useTheme(body.get("code")); }
}
