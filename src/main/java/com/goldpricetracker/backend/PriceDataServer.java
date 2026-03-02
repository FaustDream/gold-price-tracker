package com.goldpricetracker.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * 本地数据服务 (仅本机访问)
 * 提供 /price 与 /settings 接口，供原生 AppBar 或其他前端拉取展示。
 */
public class PriceDataServer {
    private static HttpServer server;
    private static final String CONFIG_FILE = "gold_tracker_config.properties";
    private static final int PORT = 9876;

    public static void startAsync() {
        try {
            if (server != null) return;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/price", new PriceHandler());
            server.createContext("/settings", new SettingsHandler());
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            System.out.println("PriceDataServer started on http://127.0.0.1:" + PORT);
        } catch (IOException e) {
            System.err.println("Failed to start PriceDataServer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static class PriceHandler implements HttpHandler {
        private final PriceService priceService = new PriceService();
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"method_not_allowed\"}");
                return;
            }
            Map<String, Double> prices = priceService.fetchPrices();
            double domestic = prices.getOrDefault("domestic", 0.0);
            double international = prices.getOrDefault("international", 0.0);
            double marketClosed = prices.getOrDefault("market_closed", 0.0);

            String json = String.format("{\"domestic\":%.4f,\"international\":%.4f,\"market_closed\":%d}",
                    domestic, international, marketClosed > 0.5 ? 1 : 0);
            respond(exchange, 200, json);
        }
    }

    static class SettingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
                props.load(in);
            } catch (IOException ignored) {}

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String fontSize = props.getProperty("font.size", "14");
                String lang = props.getProperty("lang", "zh-CN");
                String json = String.format("{\"font_size\":%s,\"lang\":\"%s\"}", fontSize, lang);
                respond(exchange, 200, json);
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String text = new String(body, StandardCharsets.UTF_8);
                // 简单解析：key=value&key=value
                for (String pair : text.split("&")) {
                    String[] kv = pair.split("=");
                    if (kv.length == 2) {
                        if ("font_size".equals(kv[0])) props.setProperty("font.size", kv[1]);
                        if ("lang".equals(kv[0])) props.setProperty("lang", kv[1]);
                    }
                }
                try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
                    props.store(out, null);
                }
                respond(exchange, 200, "{\"status\":\"ok\"}");
                return;
            }
            respond(exchange, 405, "{\"error\":\"method_not_allowed\"}");
        }
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
