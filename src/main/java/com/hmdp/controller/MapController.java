package com.hmdp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.hmdp.dto.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import javax.annotation.Resource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/map")
public class MapController {
    private final RestTemplate client;
    @Resource private JdbcTemplate jdbc;
    @Value("${amap.web-key:}") private String key;

    public MapController() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(6000);
        this.client = new RestTemplate(factory);
    }

    @GetMapping("/shop/{id}")
    public Result shop(@PathVariable Long id) {
        if (id == null || id <= 0) return Result.fail("商户编号无效");
        List<Map<String, Object>> links = jdbc.queryForList(
                "SELECT amap_poi_id AS poiId, amap_name AS name, amap_address AS address, "
                        + "amap_longitude AS longitude, amap_latitude AS latitude, coordinate_system AS coordinateSystem, "
                        + "match_basis AS matchBasis, checked_at AS checkedAt FROM shop_amap_link WHERE shop_id = ?", id);
        return Result.ok(links.isEmpty() ? null : links.get(0));
    }

    @GetMapping("/shop/{id}/posts")
    public Result posts(@PathVariable Long id) {
        if (id == null || id <= 0) return Result.fail("商户编号无效");
        return Result.ok(jdbc.queryForList("SELECT id, title, create_time AS createTime FROM tb_blog WHERE shop_id = ? AND community_visible = 1 ORDER BY create_time DESC, id DESC LIMIT 10", id));
    }

    @GetMapping("/place/{id}")
    public Result place(@PathVariable String id) {
        if (!id.matches("[A-Za-z0-9]{1,32}")) return Result.fail("地点编号无效");
        if (key == null || key.trim().isEmpty()) return Result.fail("高德地图服务尚未配置");
        URI uri = UriComponentsBuilder.fromHttpUrl("https://restapi.amap.com/v3/place/detail")
                .queryParam("key", key).queryParam("id", id).build().encode().toUri();
        JsonNode response;
        try { response = client.getForObject(uri, JsonNode.class); }
        catch (RestClientException exception) { return Result.fail("地点详情暂时不可用，请稍后重试"); }
        if (response == null || !"1".equals(response.path("status").asText())) return Result.fail("地点详情查询失败，请稍后重试");
        JsonNode pois = response.path("pois");
        if (!pois.isArray() || pois.isEmpty()) return Result.fail("地点不存在或已下线");
        return Result.ok(placeData(pois.get(0)));
    }

    private Map<String, Object> placeData(JsonNode item) {
        Map<String, Object> place = new LinkedHashMap<>();
        String poiId = item.path("id").asText();
        place.put("id", poiId);
        place.put("name", item.path("name").asText());
        place.put("address", item.path("address").isTextual() ? item.path("address").asText() : "地址暂无");
        place.put("location", item.path("location").asText());
        place.put("distance", item.path("distance").asText());
        List<Long> matches = jdbc.queryForList("SELECT shop_id FROM shop_amap_link WHERE amap_poi_id = ?", Long.class, poiId);
        if (!matches.isEmpty()) place.put("shopId", matches.get(0));
        return place;
    }

    @GetMapping("/nearby")
    public Result nearby(@RequestParam double x, @RequestParam double y, @RequestParam String keyword) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 73 || x > 135 || y < 18 || y > 54)
            return Result.fail("位置坐标无效");
        if (keyword == null || keyword.trim().isEmpty() || keyword.length() > 40) return Result.fail("请输入有效的地点关键词");
        if (key == null || key.trim().isEmpty()) return Result.fail("高德地图服务尚未配置");
        URI conversion = UriComponentsBuilder.fromHttpUrl("https://restapi.amap.com/v3/assistant/coordinate/convert")
                .queryParam("key", key).queryParam("coordsys", "gps")
                .queryParam("locations", String.format(Locale.ROOT, "%.6f,%.6f", x, y)).build().encode().toUri();
        JsonNode converted;
        try { converted = client.getForObject(conversion, JsonNode.class); }
        catch (RestClientException exception) { return Result.fail("位置坐标转换失败，请稍后重试"); }
        if (converted == null || !"1".equals(converted.path("status").asText())
                || !converted.path("locations").asText("").matches("[0-9.]+,[0-9.]+"))
            return Result.fail("位置坐标转换失败，请检查地图服务权限");
        String[] convertedParts = converted.path("locations").asText().split(",");
        String location = String.format(Locale.ROOT, "%.6f,%.6f", Double.parseDouble(convertedParts[0]), Double.parseDouble(convertedParts[1]));
        URI uri = UriComponentsBuilder.fromHttpUrl("https://restapi.amap.com/v3/place/around")
                .queryParam("key", key).queryParam("location", location)
                .queryParam("keywords", keyword.trim()).queryParam("radius", 3000).queryParam("offset", 10)
                .queryParam("page", 1).queryParam("sortrule", "distance").build().encode().toUri();
        JsonNode response;
        try { response = client.getForObject(uri, JsonNode.class); }
        catch (RestClientException exception) { return Result.fail("高德地点查询暂时不可用，请稍后重试"); }
        if (response == null || !"1".equals(response.path("status").asText())) return Result.fail("高德地点查询失败，请检查服务权限或稍后重试");
        List<Map<String, Object>> places = new ArrayList<>();
        for (JsonNode item : response.path("pois")) {
            places.add(placeData(item));
        }
        return Result.ok(places);
    }
}
