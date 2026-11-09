package com.hmdp.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.Result;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherService;
import com.hmdp.service.IShopTypeService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Component
public class AiToolRegistry {
    private final ObjectMapper objectMapper;
    public AiToolRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    @Resource
    private IShopService shopService;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private IBlogService blogService;
    @Resource
    private IShopTypeService shopTypeService;

    public List<Map<String, Object>> definitions() {
        return Arrays.asList(
                function("query_shop", "Query a Shenyang shop by its id", schema("id")),
                function("query_shop_types", "List available shop categories", emptySchema()),
                function("query_vouchers", "Query vouchers of a shop", schema("shopId")),
                function("query_nearby_shops", "Query Shenyang shops near the user by category and distance", nearbySchema()),
                function("query_hot_blogs", "Query hot community posts by page", schema("current")),
                function("query_follow_feed", "Query the authenticated user's follow feed", feedSchema())
        );
    }

    public String execute(String name, String argumentsJson) {
        return execute(name, argumentsJson, null);
    }

    public String execute(String name, String argumentsJson, com.hmdp.ai.dto.AiLocation location) {
        try {
            JsonNode arguments = objectMapper.readTree(argumentsJson);
            Result result;
            if ("query_shop".equals(name)) {
                result = shopService.queryById(requiredLong(arguments, "id"));
            } else if ("query_shop_types".equals(name)) {
                result = shopTypeService.queryTypeList();
            } else if ("query_vouchers".equals(name)) {
                result = voucherService.queryVoucherOfShop(requiredLong(arguments, "shopId"));
            } else if ("query_nearby_shops".equals(name)) {
                int typeId = positiveInt(arguments, "typeId");
                int current = positiveInt(arguments, "current");
                double x = location == null ? numberOrDefault(arguments, "x", 123.70256389816592D) : validCoordinate(location.getX(), "x", -180D, 180D);
                double y = location == null ? numberOrDefault(arguments, "y", 41.456947405181005D) : validCoordinate(location.getY(), "y", -85.05112878D, 85.05112878D);
                double radius = numberOrDefault(arguments, "radius", 5000D);
                if (x < -180D || x > 180D || y < -85.05112878D || y > 85.05112878D || radius <= 0 || radius > 50000D) {
                    throw new IllegalArgumentException("AI nearby query coordinates or radius are outside the supported range");
                }
                result = shopService.queryShopByType(typeId, current, x, y, radius);
            } else if ("query_hot_blogs".equals(name)) {
                result = blogService.queryHotBlog(positiveInt(arguments, "current"));
            } else if ("query_follow_feed".equals(name)) {
                result = blogService.queryBlogOfFollow(requiredLong(arguments, "max"), requiredInt(arguments, "offset"));
            } else {
                throw new IllegalArgumentException("Unsupported AI tool: " + name);
            }
            if ("query_shop".equals(name) || "query_nearby_shops".equals(name)) {
                return sanitizedShopResult(result);
            }
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid arguments for AI tool: name=" + name
                    + ", arguments=" + argumentsJson, exception);
        }
    }

    private String sanitizedShopResult(Result result) throws com.fasterxml.jackson.core.JsonProcessingException {
        JsonNode data = objectMapper.valueToTree(result.getData());
        List<Map<String, Object>> items = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode shop : data) addShop(items, shop);
        } else if (data != null && data.isObject()) {
            addShop(items, data);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.getSuccess());
        response.put("errorMsg", result.getErrorMsg());
        response.put("data", items);
        response.put("notice", "仅展示公开POI资料，位置为转换后的近似值，营业状态请出行前核实；优惠券仍为演示数据。");
        return objectMapper.writeValueAsString(response);
    }

    private void addShop(List<Map<String, Object>> items, JsonNode shop) {
        if (!shop.path("id").canConvertToLong() || !shop.path("name").isTextual()
                || !"公开POI·沈阳药科大学周边".equals(shop.path("area").asText())) return;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", shop.path("id").asLong());
        item.put("name", shop.path("name").asText());
        item.put("address", shop.path("address").asText());
        if (shop.path("distance").isNumber()) item.put("distance", shop.path("distance").asDouble());
        items.add(item);
    }

    private Map<String, Object> function(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private Map<String, Object> schema(String property) {
        return map("type", "object", "properties", map(property, map("type", "integer")),
                "required", Arrays.asList(property));
    }

    private Map<String, Object> emptySchema() {
        return map("type", "object", "properties", map());
    }

    private Map<String, Object> nearbySchema() {
        return map("type", "object", "properties", map(
                "typeId", map("type", "integer"), "current", map("type", "integer"),
                "x", map("type", "number"), "y", map("type", "number"),
                "radius", map("type", "number")),
                "required", Arrays.asList("typeId", "current"));
    }

    private Map<String, Object> feedSchema() {
        return map("type", "object", "properties", map(
                "max", map("type", "integer"), "offset", map("type", "integer")),
                "required", Arrays.asList("max", "offset"));
    }

    private Long requiredLong(JsonNode arguments, String name) {
        if (!arguments.has(name) || !arguments.path(name).canConvertToLong()) {
            throw new IllegalArgumentException("AI tool argument must be a valid integer: " + name);
        }
        return arguments.path(name).asLong();
    }

    private Integer requiredInt(JsonNode arguments, String name) {
        Long value = requiredLong(arguments, name);
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw new IllegalArgumentException("AI tool argument is outside integer range: " + name);
        }
        return value.intValue();
    }

    private int positiveInt(JsonNode arguments, String name) {
        int value = requiredInt(arguments, name);
        if (value <= 0 || value > 1000) throw new IllegalArgumentException("AI tool argument must be between 1 and 1000: " + name);
        return value;
    }

    private double validCoordinate(Double value, String name, double min, double max) {
        if (value == null || !Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Invalid AI location coordinate: " + name);
        }
        return value;
    }

    private Double numberOrDefault(JsonNode arguments, String name, Double defaultValue) {
        if (!arguments.has(name) || !arguments.path(name).isNumber()) {
            return defaultValue;
        }
        double value = arguments.path(name).asDouble();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("AI tool argument must be finite: " + name);
        }
        return value;
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }
}
