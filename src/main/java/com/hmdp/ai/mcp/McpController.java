package com.hmdp.ai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.tool.AiToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mcp")
public class McpController {
    private static final Logger log = LoggerFactory.getLogger(McpController.class);
    private static final String VERSION = "2025-03-26";
    private static final List<String> ALLOWED_TOOLS = Arrays.asList(
            "query_shop", "query_shop_types", "query_vouchers", "query_nearby_shops");

    @Resource
    private AiToolRegistry registry;
    @Resource
    private ObjectMapper mapper;

    @GetMapping
    public ResponseEntity<Void> get() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    @PostMapping(produces = "application/json")
    public ResponseEntity<?> handle(@RequestBody JsonNode request,
                                    @RequestHeader(value = "Origin", required = false) String origin) {
        if (origin != null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (!request.isObject() || !"2.0".equals(request.path("jsonrpc").asText())
                || !request.path("method").isTextual()) {
            return ResponseEntity.badRequest().body(error(null, -32600, "Invalid JSON-RPC request"));
        }
        JsonNode id = request.get("id");
        String method = request.path("method").asText();
        if (id == null) {
            if (method.startsWith("notifications/")) return ResponseEntity.accepted().build();
            return ResponseEntity.badRequest().body(error(null, -32600, "Request id is required"));
        }
        if (!id.isTextual() && !id.isIntegralNumber()) {
            return ResponseEntity.badRequest().body(error(null, -32600, "Invalid request id"));
        }
        if ("initialize".equals(method)) {
            return ResponseEntity.ok(response(id, map("protocolVersion", VERSION,
                    "capabilities", map("tools", map("listChanged", false)),
                    "serverInfo", map("name", "rcdis-campus", "version", "1.0.0"))));
        }
        if ("ping".equals(method)) return ResponseEntity.ok(response(id, map()));
        if ("tools/list".equals(method)) return ResponseEntity.ok(response(id, map("tools", tools())));
        if ("tools/call".equals(method)) {
            JsonNode params = request.path("params");
            String name = params.path("name").asText("");
            JsonNode arguments = params.path("arguments");
            if (!ALLOWED_TOOLS.contains(name) || !arguments.isObject()) {
                return ResponseEntity.ok(error(id, -32602, "Unknown tool or invalid arguments"));
            }
            try {
                validate(name, arguments);
                String result = registry.execute(name, mapper.writeValueAsString(arguments));
                return ResponseEntity.ok(response(id, map("content", Arrays.asList(map("type", "text", "text", result)))));
            } catch (IllegalArgumentException exception) {
                return ResponseEntity.ok(response(id, map("isError", true, "content", Arrays.asList(
                        map("type", "text", "text", exception.getMessage())))));
            } catch (Exception exception) {
                log.error("MCP tool execution failed: tool={}", name, exception);
                return ResponseEntity.ok(response(id, map("isError", true, "content", Arrays.asList(
                        map("type", "text", "text", "Tool execution failed; retry later")))));
            }
        }
        return ResponseEntity.ok(error(id, -32601, "Method not found"));
    }

    private List<Map<String, Object>> tools() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> definition : registry.definitions()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) definition.get("function");
            if (ALLOWED_TOOLS.contains(function.get("name"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> schema = new LinkedHashMap<>((Map<String, Object>) function.get("parameters"));
                if ("query_nearby_shops".equals(function.get("name"))) {
                    schema.put("required", Arrays.asList("typeId", "current", "x", "y"));
                }
                result.add(map("name", function.get("name"), "description", function.get("description"),
                        "inputSchema", schema));
            }
        }
        return result;
    }

    private void validate(String name, JsonNode arguments) {
        if ("query_shop".equals(name)) positive(arguments, "id");
        if ("query_vouchers".equals(name)) positive(arguments, "shopId");
        if ("query_nearby_shops".equals(name)) {
            positive(arguments, "typeId");
            positive(arguments, "current");
            if (!arguments.path("x").isNumber() || !arguments.path("y").isNumber()) {
                throw new IllegalArgumentException("Nearby search requires explicit x and y coordinates");
            }
        }
    }

    private void positive(JsonNode arguments, String key) {
        JsonNode value = arguments.path(key);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() <= 0) {
            throw new IllegalArgumentException("Argument must be a positive integer: " + key);
        }
    }

    private Map<String, Object> response(JsonNode id, Object result) {
        return map("jsonrpc", "2.0", "id", id, "result", result);
    }

    private Map<String, Object> error(JsonNode id, int code, String message) {
        return map("jsonrpc", "2.0", "id", id, "error", map("code", code, "message", message));
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) result.put(String.valueOf(values[i]), values[i + 1]);
        return result;
    }
}
