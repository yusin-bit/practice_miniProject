package main.java.com.rental.web;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import main.java.com.rental.common.exception.NotFoundException;
import main.java.com.rental.common.util.DBManager;
import main.java.com.rental.category.entity.Category;
import main.java.com.rental.category.service.CategoryServiceImpl;
import main.java.com.rental.post.dto.AvailablePost;
import main.java.com.rental.post.service.PostService;
import main.java.com.rental.post.service.PostServiceImpl;
import main.java.com.rental.item.dto.ItemCreateRequest;
import main.java.com.rental.item.entity.Item;
import main.java.com.rental.item.service.ItemService;
import main.java.com.rental.item.service.ItemServiceImpl;
import main.java.com.rental.rental.dto.RentalCreateRequest;
import main.java.com.rental.rental.dto.RentalDetail;
import main.java.com.rental.rental.entity.Rental;
import main.java.com.rental.rental.service.RentalServiceImpl;
import main.java.com.rental.session.Session;
import main.java.com.rental.user.dto.PasswordChangeRequest;
import main.java.com.rental.user.dto.UserLoginRequest;
import main.java.com.rental.user.dto.UserResponse;
import main.java.com.rental.user.dto.UserSignUpRequest;
import main.java.com.rental.user.service.UserServiceImpl;

/**
 * 별도 WAS 없이 실행되는 로컬 웹 진입점.
 * 브라우저 요청을 기존 Service -> Repository -> JDBC 흐름에 연결한다.
 */
public class LocalWebApplication {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8787;
    private static final Path WEB_ROOT = Path.of("web").toAbsolutePath().normalize();

    private final UserServiceImpl userService = new UserServiceImpl();
    private final CategoryServiceImpl categoryService = new CategoryServiceImpl();
    private final PostService postService = PostServiceImpl.getInstance();
    private final ItemService itemService = ItemServiceImpl.getInstance();
    private final RentalServiceImpl rentalService = RentalServiceImpl.getInstance();

    public static void main(String[] args) throws Exception {
        new LocalWebApplication().start();
    }

    private void start() throws Exception {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(HOST), PORT), 0);
        } catch (IOException alreadyRunning) {
            openBrowser();
            return;
        }

        server.createContext("/api/health", this::health);
        server.createContext("/api/stats", this::globalStats);
        server.createContext("/api/login", this::login);
        server.createContext("/api/signup", this::signUp);
        server.createContext("/api/check-id", this::checkId);
        server.createContext("/api/find-id", this::findId);
        server.createContext("/api/password", this::changePassword);
        server.createContext("/api/password/reset", this::resetPassword);
        server.createContext("/api/logout", this::logout);
        server.createContext("/api/posts", this::availablePosts);
        server.createContext("/api/items/mine", this::myItems);
        server.createContext("/api/items/create", this::createItem);
        server.createContext("/api/items/update", this::updateItem);
        server.createContext("/api/items/delete", this::deleteItem);
        server.createContext("/api/categories", this::categories);
        server.createContext("/api/rentals/current", this::currentRentals);
        server.createContext("/api/rentals/action", this::rentalAction);
        server.createContext("/api/rentals", this::createRental);
        server.createContext("/", new StaticHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("빌림 로컬 웹 프로그램 실행: http://" + HOST + ":" + PORT);
        openBrowser();
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        try (Connection connection = DBManager.getConnection()) {
            sendJson(exchange, 200, "{\"ok\":true,\"database\":\"" +
                    json(connection.getCatalog()) + "\"}");
        } catch (Exception e) {
            sendError(exchange, 500, "DB 연결 실패: " + message(e));
        }
    }

    private void globalStats(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        try {
            int[] stats = rentalService.getGlobalStats();
            sendJson(exchange, 200, "{\"ok\":true,\"available\":" + stats[0]
                    + ",\"active\":" + stats[1] + ",\"completed\":" + stats[2] + "}");
        } catch (Exception e) {
            sendError(exchange, 500, message(e));
        }
    }

    private void login(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        try {
            Map<String, String> form = readForm(exchange);
            UserResponse user = userService.login(new UserLoginRequest(form.get("id"), form.get("password")));
            sendJson(exchange, 200, "{\"ok\":true,\"user\":{\"id\":\"" + json(user.getId()) +
                    "\",\"nickname\":\"" + json(user.getNickName()) + "\",\"name\":\"" +
                    json(user.getName()) + "\",\"phone\":\"" + json(user.getPhoneNo()) + "\"}}");
        } catch (Exception e) {
            sendError(exchange, 401, message(e));
        }
    }

    private void logout(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        userService.logout();
        sendJson(exchange, 200, "{\"ok\":true}");
    }

    private void signUp(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        try {
            Map<String, String> form = readForm(exchange);
            userService.signUp(new UserSignUpRequest(form.get("id"), form.get("password"),
                    form.get("nickname"), form.get("name"), form.get("phone")));
            sendJson(exchange, 201, "{\"ok\":true,\"message\":\"회원 정보가 MySQL에 저장되었습니다.\"}");
        } catch (Exception e) {
            sendError(exchange, 400, message(e));
        }
    }

    private void checkId(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        try {
            String id = readForm(exchange).get("id");
            boolean available = userService.isIdAvailable(id);
            String message = available ? "사용 가능한 아이디입니다." : "이미 사용 중인 아이디입니다.";
            sendJson(exchange, 200, "{\"ok\":true,\"available\":" + available
                    + ",\"message\":\"" + json(message) + "\"}");
        } catch (Exception e) {
            sendError(exchange, 400, message(e));
        }
    }

    private void findId(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        try {
            String id = userService.findId(readForm(exchange).get("phone"));
            sendJson(exchange, 200, "{\"ok\":true,\"id\":\"" + json(id) + "\"}");
        } catch (Exception e) {
            sendError(exchange, 404, message(e));
        }
    }

    private void changePassword(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        if (!loggedIn(exchange)) return;
        try {
            Map<String, String> form = readForm(exchange);
            String currentPassword = form.get("currentPassword");
            String newPassword = form.get("newPassword");
            String confirmation = form.get("confirmation");
            if (newPassword == null || newPassword.isBlank() || !newPassword.equals(confirmation)) {
                sendError(exchange, 400, "새 비밀번호와 확인 값이 일치하지 않습니다.");
                return;
            }

            UserResponse currentUser = Session.getInstance().getLoginUser();
            userService.login(new UserLoginRequest(currentUser.getId(), currentPassword));
            userService.updatePassword(new PasswordChangeRequest(
                    currentUser.getId(), currentUser.getPhoneNo(), newPassword));
            sendJson(exchange, 200, "{\"ok\":true,\"message\":\"비밀번호가 변경되었습니다.\"}");
        } catch (Exception e) {
            sendError(exchange, 400, message(e));
        }
    }

    private void resetPassword(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        try {
            Map<String, String> form = readForm(exchange);
            String id = form.getOrDefault("id", "").trim();
            String phone = form.getOrDefault("phone", "").trim();
            String newPassword = form.get("newPassword");
            String confirmation = form.get("confirmation");
            if (newPassword == null || newPassword.isBlank() || !newPassword.equals(confirmation)) {
                sendError(exchange, 400, "새 비밀번호와 확인 값이 일치하지 않습니다.");
                return;
            }
            userService.updatePassword(new PasswordChangeRequest(id, phone, newPassword));
            sendJson(exchange, 200, "{\"ok\":true,\"message\":\"비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요.\"}");
        } catch (Exception e) {
            sendError(exchange, 400, message(e));
        }
    }

    private void availablePosts(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        if (!loggedIn(exchange)) return;
        try {
            List<AvailablePost> posts = postService.selectAvailablePost();
            StringBuilder json = new StringBuilder("{\"ok\":true,\"posts\":[");
            for (int i = 0; i < posts.size(); i++) {
                AvailablePost post = posts.get(i);
                if (i > 0) json.append(',');
                json.append("{\"postNum\":").append(post.getPostNum())
                    .append(",\"title\":\"").append(json(post.getTitle()))
                    .append("\",\"content\":\"").append(json(post.getContent()))
                    .append("\",\"rentDate\":\"").append(json(post.getRentDate()))
                    .append("\",\"returnDate\":\"").append(json(post.getReturnDate()))
                    .append("\",\"addr\":\"").append(json(post.getAddr()))
                    .append("\",\"itemName\":\"").append(json(post.getItemName()))
                    .append("\",\"category\":\"").append(json(post.getCategory())).append("\"}");
            }
            sendJson(exchange, 200, json.append("]}").toString());
        } catch (NotFoundException empty) {
            sendJson(exchange, 200, "{\"ok\":true,\"posts\":[]}");
        } catch (Exception e) {
            sendError(exchange, 500, message(e));
        }
    }

    private void myItems(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        if (!loggedIn(exchange)) return;
        try {
            String userId = Session.getInstance().getLoginUser().getId();
            List<Item> items = itemService.selectByUserId(userId);
            StringBuilder json = new StringBuilder("{\"ok\":true,\"items\":[");
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                if (i > 0) json.append(',');
                json.append("{\"itemNum\":").append(item.getItemNum())
                    .append(",\"itemName\":\"").append(json(item.getItemName()))
                    .append("\",\"available\":").append(item.isStatus())
                    .append(",\"smallCategoryCode\":\"").append(json(item.getSmallCategoryCode()))
                    .append("\"}");
            }
            sendJson(exchange, 200, json.append("]}").toString());
        } catch (Exception e) {
            sendError(exchange, 500, message(e));
        }
    }

    private void createItem(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        if (!loggedIn(exchange)) return;
        try {
            Map<String, String> form = readForm(exchange);
            String itemName = form.getOrDefault("itemName", "").trim();
            String smallCategoryCode = form.getOrDefault("smallCategoryCode", "").trim();
            if (itemName.isBlank() || smallCategoryCode.isBlank())
                throw new IllegalArgumentException("물품명과 소분류를 선택해주세요.");
            String userId = Session.getInstance().getLoginUser().getId();
            int itemNum = itemService.itemInsert(new ItemCreateRequest(itemName, true, smallCategoryCode, userId));
            sendJson(exchange, 201, "{\"ok\":true,\"itemNum\":" + itemNum
                    + ",\"message\":\"물품이 DB에 등록되었습니다.\"}");
        } catch (Exception e) {
            sendError(exchange, 400, message(e));
        }
    }

    private void updateItem(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        if (!loggedIn(exchange)) return;
        try {
            Map<String, String> form = readForm(exchange);
            int itemNum = Integer.parseInt(form.getOrDefault("itemNum", "0"));
            String itemName = form.getOrDefault("itemName", "").trim();
            String smallCategoryCode = form.getOrDefault("smallCategoryCode", "").trim();
            if (itemNum <= 0 || itemName.isBlank() || smallCategoryCode.isBlank())
                throw new IllegalArgumentException("수정할 물품 정보를 확인해주세요.");
            String userId = Session.getInstance().getLoginUser().getId();
            itemService.itemUpdate(new Item(itemNum, itemName, smallCategoryCode, userId));
            sendJson(exchange, 200, "{\"ok\":true,\"message\":\"물품 정보가 수정되었습니다.\"}");
        } catch (Exception e) {
            sendError(exchange, 400, message(e));
        }
    }

    private void deleteItem(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        if (!loggedIn(exchange)) return;
        try {
            int itemNum = Integer.parseInt(readForm(exchange).getOrDefault("itemNum", "0"));
            String userId = Session.getInstance().getLoginUser().getId();
            itemService.itemDelete(itemNum, userId);
            sendJson(exchange, 200, "{\"ok\":true,\"message\":\"물품이 DB에서 삭제되었습니다.\"}");
        } catch (Exception e) {
            sendError(exchange, 400, message(e));
        }
    }

    private void categories(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        if (!loggedIn(exchange)) return;
        try {
            List<Category> bigCategories = categoryService.getBigCategories();
            StringBuilder json = new StringBuilder("{\"ok\":true,\"categories\":[");
            for (int i = 0; i < bigCategories.size(); i++) {
                Category big = bigCategories.get(i);
                if (i > 0) json.append(',');
                json.append("{\"code\":\"").append(json(big.getCode()))
                    .append("\",\"name\":\"").append(json(big.getName())).append("\",\"children\":[");
                List<Category> smallCategories = categoryService.getSmallCategories(big.getCode());
                for (int j = 0; j < smallCategories.size(); j++) {
                    Category small = smallCategories.get(j);
                    if (j > 0) json.append(',');
                    json.append("{\"code\":\"").append(json(small.getCode()))
                        .append("\",\"name\":\"").append(json(small.getName())).append("\"}");
                }
                json.append("]}");
            }
            sendJson(exchange, 200, json.append("]}").toString());
        } catch (Exception e) {
            sendError(exchange, 500, message(e));
        }
    }

    private void createRental(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        if (!loggedIn(exchange)) return;
        try {
            Map<String, String> form = readForm(exchange);
            int postNum = Integer.parseInt(form.getOrDefault("postNum", "0"));
            String borrowerId = Session.getInstance().getLoginUser().getId();
            rentalService.rentalCreate(new RentalCreateRequest(postNum, borrowerId));
            sendJson(exchange, 201, "{\"ok\":true,\"status\":100,\"message\":\"대여 신청이 DB에 저장되었습니다.\"}");
        } catch (Exception e) {
            sendError(exchange, 400, message(e));
        }
    }

    private void currentRentals(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        if (!loggedIn(exchange)) return;
        try {
            List<RentalDetail> rentals = rentalService.selectRelatedRentalHistory();
            String loginId = Session.getInstance().getLoginUser().getId();
            StringBuilder json = new StringBuilder("{\"ok\":true,\"rentals\":[");
            for (int i = 0; i < rentals.size(); i++) {
                RentalDetail rental = rentals.get(i);
                if (i > 0) json.append(',');
                json.append("{\"rentalNum\":").append(rental.getRentalNum())
                    .append(",\"postNum\":").append(rental.getPostNum())
                    .append(",\"borrowerId\":\"").append(json(rental.getBorrowerId()))
                    .append("\",\"lenderId\":\"").append(json(rental.getLenderId()))
                    .append("\",\"role\":\"").append(loginId.equals(rental.getLenderId()) ? "lender" : "borrower")
                    .append("\",\"itemName\":\"").append(json(rental.getItemName()))
                    .append("\",\"title\":\"").append(json(rental.getTitle()))
                    .append("\",\"rentDate\":\"").append(json(rental.getRentDate()))
                    .append("\",\"returnDate\":\"").append(json(rental.getReturnDate()))
                    .append("\",\"addr\":\"").append(json(rental.getAddr()))
                    .append("\",\"status\":").append(rental.getStatus().getCode())
                    .append(",\"statusName\":\"").append(json(rental.getStatus().getName())).append("\"}");
            }
            sendJson(exchange, 200, json.append("]}").toString());
        } catch (Exception e) {
            sendError(exchange, 500, message(e));
        }
    }

    private void rentalAction(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        if (!loggedIn(exchange)) return;
        try {
            Map<String, String> form = readForm(exchange);
            int rentalNum = Integer.parseInt(form.getOrDefault("rentalNum", "0"));
            String action = form.getOrDefault("action", "");
            String resultMessage;
            if ("approve".equals(action)) {
                Rental rental = rentalService.selectByRentalNum(rentalNum);
                if (!rentalService.approveRental(rentalNum, rental.getPostNum()))
                    throw new IllegalStateException("대여 신청 승인에 실패했습니다.");
                resultMessage = "대여 신청을 승인했습니다.";
            } else {
                int fromStatus;
                int toStatus;
                switch (action) {
                    case "reject" -> { fromStatus = 100; toStatus = 102; resultMessage = "대여 신청을 거절했습니다."; }
                    case "start" -> { fromStatus = 101; toStatus = 110; resultMessage = "물품 인도를 확인하고 대여를 시작했습니다."; }
                    case "return-request" -> { fromStatus = 110; toStatus = 200; resultMessage = "반납을 신청했습니다."; }
                    case "return-approve" -> { fromStatus = 200; toStatus = 201; resultMessage = "반납 신청을 승인했습니다."; }
                    case "return-confirm" -> { fromStatus = 201; toStatus = 210; resultMessage = "반납 거래를 확인했습니다."; }
                    case "complete" -> { fromStatus = 210; toStatus = 211; resultMessage = "반납을 최종 완료했습니다."; }
                    default -> throw new IllegalArgumentException("지원하지 않는 처리입니다.");
                }
                rentalService.updateStatusRentalNum(toStatus, rentalNum, fromStatus);
            }
            sendJson(exchange, 200, "{\"ok\":true,\"message\":\"" + json(resultMessage) + "\"}");
        } catch (Exception e) {
            sendError(exchange, 400, message(e));
        }
    }

    private boolean loggedIn(HttpExchange exchange) throws IOException {
        if (Session.getInstance().getLoginUser() != null) return true;
        sendError(exchange, 401, "먼저 로그인해주세요.");
        return false;
    }

    private boolean method(HttpExchange exchange, String expected) throws IOException {
        if (expected.equalsIgnoreCase(exchange.getRequestMethod())) return true;
        sendError(exchange, 405, "지원하지 않는 요청 방식입니다.");
        return false;
    }

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> values = new HashMap<>();
        if (body.isBlank()) return values;
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }

    private static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, "{\"ok\":false,\"message\":\"" + json(message) + "\"}");
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private static String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String message(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static void openBrowser() {
        try {
            URI uri = URI.create("http://" + HOST + ":" + PORT);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(uri);
        } catch (Exception ignored) { }
    }

    private static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();
            if ("/".equals(requestPath)) requestPath = "/index.html";
            Path file = WEB_ROOT.resolve(requestPath.substring(1)).normalize();
            if (!file.startsWith(WEB_ROOT) || !Files.isRegularFile(file)) {
                byte[] missing = "Not found".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, missing.length);
                exchange.getResponseBody().write(missing);
                exchange.close();
                return;
            }
            String name = file.getFileName().toString();
            String type = name.endsWith(".css") ? "text/css; charset=utf-8"
                    : name.endsWith(".js") ? "application/javascript; charset=utf-8"
                    : "text/html; charset=utf-8";
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            long length = Files.size(file);
            exchange.sendResponseHeaders(200, length);
            try (InputStream input = Files.newInputStream(file); OutputStream output = exchange.getResponseBody()) {
                input.transferTo(output);
            }
        }
    }
}
