package dev.starcore.starcore.webmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * WebMap服务器
 * 基于Undertow的轻量级HTTP服务器
 */
public class WebMapServer implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final Logger logger;
    private final Gson gson;

    private Undertow server;
    private final String host;
    private final int port;

    // E-085: 允许的 CORS 域名（配置化）- 使用 ConcurrentHashMap.newKeySet() 保证线程安全
    private final Set<String> allowedOrigins = ConcurrentHashMap.newKeySet();
    private boolean corsEnabled = true;

    // 数据提供者
    private final MapDataProvider dataProvider;

    // 标记数据提供者
    private WebMapMarkerProvider markerProvider;

    // E-086: API 密钥（可配置），防止未授权访问敏感数据
    private String apiKey;

    // 引用 SSL 服务（可选）
    private SSLManager sslManager;

    public WebMapServer(Plugin plugin, String host, int port, MapDataProvider dataProvider) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.host = host;
        this.port = port;
        this.dataProvider = dataProvider;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

        // 初始化允许的 CORS 域名
        initAllowedOrigins();
    }

    /**
     * 设置 SSL 管理器
     */
    public void setSslManager(SSLManager sslManager) {
        this.sslManager = sslManager;
    }

    /**
     * 设置标记数据提供者
     */
    public void setMarkerProvider(WebMapMarkerProvider markerProvider) {
        this.markerProvider = markerProvider;
    }

    /**
     * 初始化允许的 CORS 域名
     */
    private void initAllowedOrigins() {
        // 默认允许的域名
        allowedOrigins.add("localhost");
        allowedOrigins.add("127.0.0.1");

        // 从配置文件读取（如果存在）
        // 这里可以扩展为从 config.yml 读取
    }

    /**
     * 添加允许的 CORS 域名
     */
    public void addAllowedOrigin(String origin) {
        if (origin != null && !origin.isBlank()) {
            allowedOrigins.add(origin.toLowerCase().trim());
            logger.info("已添加 CORS 允许域名: " + origin);
        }
    }

    /**
     * 移除允许的 CORS 域名
     */
    public void removeAllowedOrigin(String origin) {
        if (origin != null) {
            allowedOrigins.remove(origin.toLowerCase().trim());
            logger.info("已移除 CORS 允许域名: " + origin);
        }
    }

    /**
     * 获取当前允许的 CORS 域名列表
     */
    public Set<String> getAllowedOrigins() {
        return new HashSet<>(allowedOrigins);
    }

    /**
     * 启用/禁用 CORS
     */
    public void setCorsEnabled(boolean enabled) {
        this.corsEnabled = enabled;
    }

    /**
     * 设置 API 密钥
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 启动服务器
     * E-089: 启动失败时记录严重日志，防止外部误以为服务器已启动
     */
    public void start() {
        if (server != null) {
            logger.warning("WebMap服务器已经在运行");
            return;
        }

        try {
            server = Undertow.builder()
                .addHttpListener(port, host)
                .setHandler(this::handleRequest)
                .build();

            server.start();
            logger.info("WebMap服务器已启动: http://" + host + ":" + port);
        } catch (Exception e) {
            // E-089: 启动失败后明确标记 server=null，防止外部误判
            server = null;
            logger.severe("WebMap服务器启动失败: " + e.getMessage());
        }
    }

    /**
     * 启动 HTTPS 服务器
     */
    public void startHttps(String domain) {
        if (server != null) {
            logger.warning("WebMap服务器已经在运行");
            return;
        }

        if (sslManager == null) {
            logger.severe("SSL 管理器未设置，无法启动 HTTPS");
            return;
        }

        try {
            var sslContext = sslManager.createSSLContext(domain);
            if (sslContext == null) {
                logger.severe("无法创建 SSL 上下文，证书可能不存在");
                return;
            }

            server = Undertow.builder()
                .addHttpsListener(port, host, sslContext)
                .setHandler(this::handleRequest)
                .build();

            server.start();
            logger.info("WebMap HTTPS 服务器已启动: https://" + host + ":" + port);
        } catch (Exception e) {
            logger.severe("WebMap HTTPS 服务器启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止服务器
     */
    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
            logger.info("WebMap服务器已停止");
        }
    }

    /**
     * 重载服务器配置
     */
    public void reload(String newHost, int newPort) {
        boolean wasRunning = server != null;
        if (wasRunning) {
            stop();
        }

        // 更新配置（这里需要重新创建实例）
        // 暂时只记录日志
        logger.info("配置已更新，请重启服务器以应用新配置");
    }

    /**
     * 处理HTTP请求
     * E-086: 添加 API 密钥鉴权，防止未授权访问敏感数据
     */
    private void handleRequest(HttpServerExchange exchange) {
        // E-086: 敏感路径需要鉴权
        String path = exchange.getRequestPath();
        if (requiresAuth(path) && !validateApiKey(exchange)) {
            exchange.setStatusCode(401);
            exchange.getResponseSender().send("{\"error\":\"Unauthorized\"}");
            return;
        }

        // 处理 CORS 预检请求
        if (exchange.getRequestMethod().toString().equals("OPTIONS")) {
            handleCorsPreflight(exchange);
            exchange.setStatusCode(204);
            exchange.endExchange();
            return;
        }

        // 设置 CORS 头（安全版本）
        setCorsHeaders(exchange);

        exchange.getResponseHeaders()
            .put(Headers.CONTENT_TYPE, "application/json");

        try {
            String response = switch (path) {
                case "/api/territories" -> handleTerritories();
                case "/api/nations" -> handleNations();
                case "/api/cities" -> handleCities();
                case "/api/players" -> handlePlayers();
                case "/api/stats" -> handleStats();
                case "/api/markers" -> handleMarkers();
                case "/api/markers/public" -> handlePublicMarkers();
                case "/api/markers/search" -> handleSearchMarkers(exchange);
                case "/api/health" -> "{\"status\":\"ok\",\"timestamp\":" + System.currentTimeMillis() + "}";
                default -> handleNotFound();
            };

            exchange.setStatusCode(200);
            exchange.getResponseSender().send(response);
        } catch (Exception e) {
            logger.severe("处理请求失败: " + e.getMessage());
            exchange.setStatusCode(500);
            exchange.getResponseSender().send("{\"error\":\"Internal Server Error\"}");
        }
    }

    /**
     * 设置 CORS 响应头（安全配置）
     */
    private void setCorsHeaders(HttpServerExchange exchange) {
        if (!corsEnabled) {
            return;
        }

        // 获取请求的 Origin
        String requestOrigin = exchange.getRequestHeaders().getFirst(HttpString.tryFromString("Origin"));

        // 验证 Origin 是否在允许列表中
        if (requestOrigin != null && isOriginAllowed(requestOrigin)) {
            exchange.getResponseHeaders()
                .put(HttpString.tryFromString("Access-Control-Allow-Origin"), requestOrigin);
            exchange.getResponseHeaders()
                .put(HttpString.tryFromString("Access-Control-Allow-Credentials"), "true");
        }

        // 限制允许的 HTTP 方法
        exchange.getResponseHeaders()
            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, POST, OPTIONS");

        // 限制允许的头部
        exchange.getResponseHeaders()
            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Content-Type, Authorization, X-Requested-With");

        // 安全响应头
        exchange.getResponseHeaders()
            .put(HttpString.tryFromString("X-Content-Type-Options"), "nosniff");
        exchange.getResponseHeaders()
            .put(HttpString.tryFromString("X-Frame-Options"), "DENY");
        exchange.getResponseHeaders()
            .put(HttpString.tryFromString("X-XSS-Protection"), "1; mode=block");
        // Content-Security-Policy 防止 XSS 和数据注入
        exchange.getResponseHeaders()
            .put(HttpString.tryFromString("Content-Security-Policy"), "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'");
        // E-088: Strict-Transport-Security 仅在 HTTPS 端口发送，HTTP 端口不应发送
        // if (exchange.isSecure()) { exchange.getResponseHeaders().put(HttpString.tryFromString("Strict-Transport-Security"), "max-age=31536000; includeSubDomains"); }
    }

    /**
     * 处理 CORS 预检请求
     */
    private void handleCorsPreflight(HttpServerExchange exchange) {
        if (!corsEnabled) {
            return;
        }

        String requestOrigin = exchange.getRequestHeaders().getFirst(HttpString.tryFromString("Origin"));

        if (requestOrigin != null && isOriginAllowed(requestOrigin)) {
            exchange.getResponseHeaders()
                .put(HttpString.tryFromString("Access-Control-Allow-Origin"), requestOrigin);
            exchange.getResponseHeaders()
                .put(HttpString.tryFromString("Access-Control-Allow-Credentials"), "true");
        }

        exchange.getResponseHeaders()
            .put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, POST, OPTIONS");
        exchange.getResponseHeaders()
            .put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Content-Type, Authorization, X-Requested-With");
        exchange.getResponseHeaders()
            .put(HttpString.tryFromString("Access-Control-Max-Age"), "86400"); // 24小时缓存预检结果
    }

    /**
     * 验证 API 密钥
     * E-086: 敏感数据接口需要有效的 API 密钥
     */
    private boolean validateApiKey(HttpServerExchange exchange) {
        if (apiKey == null || apiKey.isEmpty()) {
            // 如果未配置密钥，仅允许本地访问
            String remoteAddr = exchange.getSourceAddress() != null
                ? exchange.getSourceAddress().getAddress().getHostAddress()
                : "";
            return remoteAddr.startsWith("127.") || remoteAddr.startsWith("0:0:0:0:0:0:0:1");
        }
        String providedKey = exchange.getRequestHeaders().getFirst(HttpString.tryFromString("X-API-Key"));
        if (providedKey == null) {
            providedKey = exchange.getRequestHeaders().getFirst(HttpString.tryFromString("Authorization"));
            if (providedKey != null && providedKey.startsWith("Bearer ")) {
                providedKey = providedKey.substring(7);
            }
        }
        return apiKey.equals(providedKey);
    }

    /**
     * 检查路径是否需要鉴权
     * E-086: /api/territories, /api/nations, /api/players, /api/stats 返回敏感数据需要鉴权
     */
    private boolean requiresAuth(String path) {
        return path.equals("/api/territories")
            || path.equals("/api/nations")
            || path.equals("/api/cities")
            || path.equals("/api/players")
            || path.equals("/api/stats")
            || path.equals("/api/markers");
    }

    /**
     * 检查 Origin 是否被允许
     */
    private boolean isOriginAllowed(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }

        // 移除协议前缀
        String cleanOrigin = origin.toLowerCase().trim();
        if (cleanOrigin.startsWith("http://")) {
            cleanOrigin = cleanOrigin.substring(7);
        } else if (cleanOrigin.startsWith("https://")) {
            cleanOrigin = cleanOrigin.substring(8);
        }

        // 移除路径
        int slashIndex = cleanOrigin.indexOf('/');
        if (slashIndex > 0) {
            cleanOrigin = cleanOrigin.substring(0, slashIndex);
        }

        // 移除端口
        int colonIndex = cleanOrigin.indexOf(':');
        if (colonIndex > 0) {
            cleanOrigin = cleanOrigin.substring(0, colonIndex);
        }

        // 检查是否在允许列表中
        return allowedOrigins.contains(cleanOrigin) ||
               allowedOrigins.contains("*");
    }

    /**
     * 处理Territory请求
     */
    private String handleTerritories() {
        var territories = dataProvider.getTerritories();
        return gson.toJson(territories);
    }

    /**
     * 处理Nation请求
     */
    private String handleNations() {
        var nations = dataProvider.getNations();
        return gson.toJson(nations);
    }

    /**
     * 处理City请求
     */
    private String handleCities() {
        var cities = dataProvider.getCities();
        return gson.toJson(cities);
    }

    /**
     * 处理玩家请求
     */
    private String handlePlayers() {
        var players = dataProvider.getOnlinePlayers();
        return gson.toJson(players);
    }

    /**
     * 处理统计请求
     */
    private String handleStats() {
        var stats = dataProvider.getStats();
        return gson.toJson(stats);
    }

    /**
     * 处理标记请求
     */
    private String handleMarkers() {
        if (markerProvider == null) {
            return "{\"error\":\"Marker provider not available\"}";
        }
        var markers = dataProvider.getCustomMarkers();
        return gson.toJson(markers);
    }

    /**
     * 处理公开标记请求
     */
    private String handlePublicMarkers() {
        if (markerProvider == null) {
            return "{\"error\":\"Marker provider not available\"}";
        }
        var markers = dataProvider.getCustomMarkers();
        return gson.toJson(markers);
    }

    /**
     * 处理标记搜索请求
     * E-087: 使用 Gson 构造 JSON 防止注入攻击
     */
    private String handleSearchMarkers(HttpServerExchange exchange) {
        if (markerProvider == null) {
            return gson.toJson(Map.of("error", "Marker provider not available"));
        }
        // 从查询参数获取搜索条件
        String query = null;
        String world = null;
        try {
            // 使用 getQueryString 获取原始查询字符串
            String queryString = exchange.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                for (String param : queryString.split("&")) {
                    String[] parts = param.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();
                        if ("q".equals(key)) {
                            query = java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
                        } else if ("world".equals(key)) {
                            world = java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("获取搜索参数失败: " + e.getMessage());
        }
        var markers = dataProvider.searchMarkers(query, world);
        // E-087: 使用 Gson 序列化防止 JSON 注入
        return gson.toJson(markers);
    }

    /**
     * 处理404
     */
    private String handleNotFound() {
        return "{\"error\":\"Not Found\"}";
    }

    /**
     * 检查服务器是否运行
     */
    public boolean isRunning() {
        return server != null;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    // ==================== 命令处理 ====================

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("starcore.webmap.admin")) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("你没有权限执行此命令", net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "restart" -> handleRestart(sender);
            case "status" -> handleStatus(sender);
            case "cors" -> handleCors(sender, args);
            case "reload" -> handleReload(sender);
            case "help" -> sendUsage(sender);
            default -> sendUsage(sender);
        }

        return true;
    }

    private void handleStart(CommandSender sender) {
        if (isRunning()) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("WebMap服务器已经在运行", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            return;
        }

        start();
        if (isRunning()) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("WebMap服务器已启动: http://" + host + ":" + port, net.kyori.adventure.text.format.NamedTextColor.GREEN));
        } else {
            sender.sendMessage(net.kyori.adventure.text.Component.text("WebMap服务器启动失败", net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    private void handleStop(CommandSender sender) {
        if (!isRunning()) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("WebMap服务器未运行", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            return;
        }

        stop();
        sender.sendMessage(net.kyori.adventure.text.Component.text("WebMap服务器已停止", net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    private void handleRestart(CommandSender sender) {
        stop();
        start();
        if (isRunning()) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("WebMap服务器已重启: http://" + host + ":" + port, net.kyori.adventure.text.format.NamedTextColor.GREEN));
        } else {
            sender.sendMessage(net.kyori.adventure.text.Component.text("WebMap服务器重启失败", net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(net.kyori.adventure.text.Component.text("=== WebMap 服务器状态 ===", net.kyori.adventure.text.format.NamedTextColor.GOLD));

        String status = isRunning() ? "运行中" : "已停止";
        net.kyori.adventure.text.format.NamedTextColor statusColor = isRunning()
            ? net.kyori.adventure.text.format.NamedTextColor.GREEN
            : net.kyori.adventure.text.format.NamedTextColor.RED;

        sender.sendMessage(net.kyori.adventure.text.Component.text("状态: " + status, statusColor));
        sender.sendMessage(net.kyori.adventure.text.Component.text("地址: http://" + host + ":" + port, net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(net.kyori.adventure.text.Component.text("CORS: " + (corsEnabled ? "启用" : "禁用"), net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(net.kyori.adventure.text.Component.text("允许的域名: " + String.join(", ", allowedOrigins), net.kyori.adventure.text.format.NamedTextColor.GRAY));
    }

    private void handleCors(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("用法: /webmap cors <add|remove|list|enable|disable> [origin]", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(net.kyori.adventure.text.Component.text("用法: /webmap cors add <origin>", net.kyori.adventure.text.format.NamedTextColor.RED));
                    return;
                }
                addAllowedOrigin(args[2]);
                sender.sendMessage(net.kyori.adventure.text.Component.text("已添加允许的 CORS 域名: " + args[2], net.kyori.adventure.text.format.NamedTextColor.GREEN));
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(net.kyori.adventure.text.Component.text("用法: /webmap cors remove <origin>", net.kyori.adventure.text.format.NamedTextColor.RED));
                    return;
                }
                removeAllowedOrigin(args[2]);
                sender.sendMessage(net.kyori.adventure.text.Component.text("已移除 CORS 域名: " + args[2], net.kyori.adventure.text.format.NamedTextColor.GREEN));
            }
            case "list" -> {
                sender.sendMessage(net.kyori.adventure.text.Component.text("=== 允许的 CORS 域名 ===", net.kyori.adventure.text.format.NamedTextColor.GOLD));
                if (allowedOrigins.isEmpty()) {
                    sender.sendMessage(net.kyori.adventure.text.Component.text("(无)", net.kyori.adventure.text.format.NamedTextColor.GRAY));
                } else {
                    for (String origin : allowedOrigins) {
                        sender.sendMessage(net.kyori.adventure.text.Component.text("- " + origin, net.kyori.adventure.text.format.NamedTextColor.WHITE));
                    }
                }
            }
            case "enable" -> {
                setCorsEnabled(true);
                sender.sendMessage(net.kyori.adventure.text.Component.text("CORS 已启用", net.kyori.adventure.text.format.NamedTextColor.GREEN));
            }
            case "disable" -> {
                setCorsEnabled(false);
                sender.sendMessage(net.kyori.adventure.text.Component.text("CORS 已禁用", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            }
            default -> sender.sendMessage(net.kyori.adventure.text.Component.text("未知子命令: " + args[1], net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    private void handleReload(CommandSender sender) {
        reload(host, port);
        sender.sendMessage(net.kyori.adventure.text.Component.text("配置已重载", net.kyori.adventure.text.format.NamedTextColor.GREEN));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(net.kyori.adventure.text.Component.text("=== WebMap 管理命令 ===", net.kyori.adventure.text.format.NamedTextColor.GOLD));
        sender.sendMessage(net.kyori.adventure.text.Component.text("/webmap start - 启动服务器", net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(net.kyori.adventure.text.Component.text("/webmap stop - 停止服务器", net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(net.kyori.adventure.text.Component.text("/webmap restart - 重启服务器", net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(net.kyori.adventure.text.Component.text("/webmap status - 查看服务器状态", net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(net.kyori.adventure.text.Component.text("/webmap cors add <origin> - 添加允许的 CORS 域名", net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(net.kyori.adventure.text.Component.text("/webmap cors remove <origin> - 移除 CORS 域名", net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(net.kyori.adventure.text.Component.text("/webmap cors list - 列出允许的 CORS 域名", net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(net.kyori.adventure.text.Component.text("/webmap cors enable/disable - 启用/禁用 CORS", net.kyori.adventure.text.format.NamedTextColor.WHITE));
        sender.sendMessage(net.kyori.adventure.text.Component.text("/webmap reload - 重载配置", net.kyori.adventure.text.format.NamedTextColor.WHITE));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("starcore.webmap.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return filterMatching(args[0], Arrays.asList(
                "start", "stop", "restart", "status", "cors", "reload", "help"
            ));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("cors")) {
            return filterMatching(args[1], Arrays.asList("add", "remove", "list", "enable", "disable"));
        }

        return List.of();
    }

    private List<String> filterMatching(String input, List<String> options) {
        String lower = input.toLowerCase();
        return options.stream()
            .filter(s -> s.toLowerCase().startsWith(lower))
            .toList();
    }

    // ==================== SSL 管理器接口 ====================

    /**
     * SSL 管理器接口
     */
    public interface SSLManager {
        javax.net.ssl.SSLContext createSSLContext(String domain);
    }
}
