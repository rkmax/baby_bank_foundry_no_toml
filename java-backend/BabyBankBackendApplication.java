import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BabyBankBackendApplication {
    private BabyBankBackendApplication() {}

    public static void main(String[] args) throws IOException {
        String host = System.getenv().getOrDefault("BABY_BANK_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("BABY_BANK_PORT", "8080"));
        Path repoRoot = resolveRepoRoot();

        ContractCatalog catalog = new ContractCatalog(repoRoot);
        BabyBankSimulator simulator = new BabyBankSimulator();

        HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 0);
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors())
        );
        server.setExecutor(executor);

        registerRoute(server, "/", "GET", exchange -> catalog.servicePayload());
        registerRoute(
            server,
            "/api/health",
            "GET",
            exchange -> Map.of("status", "ok", "service", "baby-bank-java-backend")
        );
        registerRoute(server, "/api/contract", "GET", exchange -> catalog.contractPayload());
        registerRoute(server, "/api/findings", "GET", exchange -> catalog.findingsPayload());
        registerRoute(server, "/api/state", "GET", exchange -> simulator.snapshot());
        registerRoute(server, "/api/reset", "POST", exchange -> simulator.reset());
        registerRoute(
            server,
            "/api/signup",
            "POST",
            exchange -> {
                Map<String, Object> body = readJsonObject(exchange);
                return simulator.signup(requireString(body, "alias"));
            }
        );
        registerRoute(
            server,
            "/api/deposit",
            "POST",
            exchange -> {
                Map<String, Object> body = readJsonObject(exchange);
                return simulator.deposit(
                    requireString(body, "confirmAlias"),
                    requireLong(body, "lockBlocks"),
                    requireDecimal(body, "amountEth")
                );
            }
        );
        registerRoute(server, "/api/withdraw", "POST", exchange -> simulator.withdraw());
        registerRoute(
            server,
            "/api/advance-blocks",
            "POST",
            exchange -> {
                Map<String, Object> body = readJsonObject(exchange);
                return simulator.advanceBlocks(requireLong(body, "amount"));
            }
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            executor.shutdownNow();
        }));

        server.start();
        System.out.println("baby-bank-java-backend listening on http://" + host + ':' + port);
    }

    private static Path resolveRepoRoot() {
        String override = System.getenv("BABY_BANK_REPO_ROOT");
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    private static void registerRoute(
        HttpServer server,
        String path,
        String method,
        RouteHandler handler
    ) {
        server.createContext(path, exchange -> handle(exchange, method, handler));
    }

    private static void handle(HttpExchange exchange, String expectedMethod, RouteHandler handler)
        throws IOException {
        addCommonHeaders(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        if (!expectedMethod.equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(
                exchange,
                405,
                errorPayload("Method not allowed.", List.of(expectedMethod))
            );
            return;
        }

        try {
            writeJson(exchange, 200, handler.handle(exchange));
        } catch (ApiException exception) {
            writeJson(exchange, exception.statusCode(), Map.of("error", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            writeJson(exchange, 400, Map.of("error", exception.getMessage()));
        } catch (Exception exception) {
            writeJson(
                exchange,
                500,
                Map.of("error", "Unexpected backend error.", "detail", exception.getMessage())
            );
        }
    }

    private static Map<String, Object> errorPayload(String message, List<String> allowedMethods) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", message);
        payload.put("allowedMethods", allowedMethods);
        return payload;
    }

    private static void addCommonHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    }

    private static void writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] responseBytes = JsonCodec.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }

    private static Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (body.isEmpty()) {
            return Map.of();
        }

        Object parsed = JsonCodec.parse(body);
        if (parsed instanceof Map<?, ?> objectValue) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) objectValue;
            return casted;
        }

        throw new ApiException(400, "Expected a JSON object.");
    }

    private static String requireString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof String stringValue) {
            return stringValue;
        }
        throw new ApiException(400, "Expected a string field: " + key);
    }

    private static long requireLong(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Number numberValue) {
            return numberValue.longValue();
        }
        throw new ApiException(400, "Expected a numeric field: " + key);
    }

    private static BigDecimal requireDecimal(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof BigDecimal decimalValue) {
            return decimalValue;
        }
        if (value instanceof Number numberValue) {
            return new BigDecimal(numberValue.toString());
        }
        throw new ApiException(400, "Expected a numeric field: " + key);
    }

    @FunctionalInterface
    private interface RouteHandler {
        Object handle(HttpExchange exchange) throws Exception;
    }

    private static final class ApiException extends RuntimeException {
        private final int statusCode;

        private ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }
}

final class BabyBankSimulator {
    private static final String CONTRACT_ADDRESS = "0xb4b900000000000000000000000000000000b4b9";
    private static final String WALLET_ADDRESS = "0xa11c00000000000000000000000000000000beef";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_ACTIVITY_ITEMS = 6;

    private long activitySequence = System.currentTimeMillis();
    private long currentBlock;
    private String registeredAlias;
    private BigDecimal lockedBalanceEth;
    private Long unlockBlock;
    private BigDecimal lastPayoutEth;
    private String statusMessage;
    private String statusTone;
    private final Deque<LinkedHashMap<String, Object>> activity;

    BabyBankSimulator() {
        this.activity = new ArrayDeque<>();
        resetInternal();
    }

    synchronized LinkedHashMap<String, Object> snapshot() {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("currentBlock", currentBlock);
        payload.put("contractAddress", CONTRACT_ADDRESS);
        payload.put("walletAddress", WALLET_ADDRESS);
        payload.put("registeredAlias", registeredAlias);
        payload.put("lockedBalanceEth", lockedBalanceEth);
        payload.put("unlockBlock", unlockBlock);
        payload.put("lastPayoutEth", lastPayoutEth);
        payload.put("statusMessage", statusMessage);
        payload.put("statusTone", statusTone);
        payload.put("activity", new ArrayList<>(activity));
        return payload;
    }

    synchronized LinkedHashMap<String, Object> reset() {
        resetInternal();
        return response("Simulation reset", "success");
    }

    synchronized LinkedHashMap<String, Object> signup(String alias) {
        String normalizedAlias = alias.trim();
        if (normalizedAlias.isEmpty()) {
            throw new IllegalArgumentException("Alias must not be blank.");
        }

        if (registeredAlias != null) {
            setStatus(
                "The contract would silently ignore a second signup. Active alias remains " + registeredAlias + '.',
                "warning"
            );
            pushActivity(
                "Duplicate signup ignored",
                "Tried to replace " + registeredAlias + " with " + normalizedAlias + ", but the contract keeps the first registration.",
                "warning"
            );
            return response("Duplicate signup ignored.", "warning");
        }

        registeredAlias = normalizedAlias;
        setStatus(
            "Recipient alias " + normalizedAlias + " registered. You can now simulate a locked deposit.",
            "success"
        );
        pushActivity(
            "Recipient registered",
            "The active wallet is now registered as " + normalizedAlias + ". Deposits can use the alias confirmation field.",
            "success"
        );
        return response("Recipient registered.", "success");
    }

    synchronized LinkedHashMap<String, Object> deposit(String confirmAlias, long lockBlocks, BigDecimal amountEth) {
        if (registeredAlias == null) {
            throw new IllegalArgumentException("The recipient must be registered before a deposit can be scheduled.");
        }

        if (!registeredAlias.equals(confirmAlias.trim())) {
            throw new IllegalArgumentException("The alias confirmation does not match the registered recipient alias.");
        }

        if (lockBlocks < 0) {
            throw new IllegalArgumentException("Lock blocks must be zero or a positive integer.");
        }

        if (amountEth.signum() <= 0) {
            throw new IllegalArgumentException("Deposit amount must be greater than zero.");
        }

        boolean hadExistingBalance = lockedBalanceEth.signum() > 0;
        lockedBalanceEth = amountEth.stripTrailingZeros();
        unlockBlock = currentBlock + lockBlocks;
        lastPayoutEth = BigDecimal.ZERO;

        pushActivity(
            "Deposit scheduled",
            "Queued " + formatEth(lockedBalanceEth) + " for " + registeredAlias + " until block " + unlockBlock + '.',
            hadExistingBalance ? "warning" : "success"
        );

        if (hadExistingBalance) {
            setStatus(
                "A new deposit replaced the previous balance, matching the overwrite behavior in the contract.",
                "warning"
            );
            return response("Deposit replaced the previous balance.", "warning");
        }

        setStatus("Deposit locked successfully until block " + unlockBlock + '.', "success");
        return response("Deposit scheduled.", "success");
    }

    synchronized LinkedHashMap<String, Object> withdraw() {
        if (lockedBalanceEth.signum() <= 0) {
            setStatus(
                "The active wallet has no locked balance. The contract would return without reverting.",
                "warning"
            );
            pushActivity(
                "Withdraw skipped",
                "No balance was available for withdrawal in the local simulation.",
                "warning"
            );
            return response("Withdraw skipped because no balance was available.", "warning");
        }

        BigDecimal payout = lockedBalanceEth;
        BigDecimal bonus = BigDecimal.ZERO;
        if (unlockBlock != null && currentBlock > unlockBlock && currentBlock % 10 == 0) {
            bonus = BigDecimal.valueOf(unlockBlock)
                .multiply(new BigDecimal("0.001"))
                .setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros();
            payout = payout.add(bonus).stripTrailingZeros();
        }

        lockedBalanceEth = BigDecimal.ZERO;
        unlockBlock = null;
        lastPayoutEth = payout;

        if (bonus.signum() > 0) {
            pushActivity(
                "Withdrawal executed",
                "Payout completed with a predictable bonus path: " + formatEth(payout) + " total, including " + formatEth(bonus) + " bonus.",
                "warning"
            );
            setStatus(
                "The predictable bonus branch was triggered. This is the insecure randomness path highlighted in the audit panel.",
                "warning"
            );
            return response("Withdrawal executed with bonus.", "warning");
        }

        pushActivity(
            "Withdrawal executed",
            "Payout completed for " + formatEth(payout) + " with no bonus triggered.",
            "success"
        );
        setStatus(
            "Withdrawal completed. Advance blocks to explore the unlock threshold and bonus path again.",
            "success"
        );
        return response("Withdrawal executed.", "success");
    }

    synchronized LinkedHashMap<String, Object> advanceBlocks(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Block advance amount must be greater than zero.");
        }

        currentBlock += amount;
        pushActivity(
            "Chain advanced",
            "Moved the local simulation forward by " + amount + " block" + (amount == 1 ? "" : "s") + '.',
            "info"
        );
        setStatus(
            "Current block is now " + currentBlock + ". Withdrawals only unlock after block " + (unlockBlock == null ? "N/A" : unlockBlock) + '.',
            "info"
        );
        return response("Block height updated.", "info");
    }

    private void resetInternal() {
        currentBlock = 182340;
        registeredAlias = null;
        lockedBalanceEth = BigDecimal.ZERO;
        unlockBlock = null;
        lastPayoutEth = BigDecimal.ZERO;
        statusMessage = "Loaded the Baby Bank backend simulation.";
        statusTone = "info";
        activity.clear();
        pushActivity(
            "Backend initialized",
            "Loaded the Baby Bank simulation API in local dummy mode.",
            "info"
        );
    }

    private void setStatus(String message, String tone) {
        statusMessage = message;
        statusTone = tone;
    }

    private void pushActivity(String title, String detail, String tone) {
        LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", ++activitySequence);
        entry.put("title", title);
        entry.put("detail", detail);
        entry.put("tone", tone);
        entry.put("time", TIME_FORMATTER.format(LocalTime.now()));
        activity.addFirst(entry);

        while (activity.size() > MAX_ACTIVITY_ITEMS) {
            activity.removeLast();
        }
    }

    private LinkedHashMap<String, Object> response(String message, String tone) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("tone", tone);
        payload.put("state", snapshot());
        return payload;
    }

    private static String formatEth(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString() + " ETH";
    }
}

final class ContractCatalog {
    private static final List<Map<String, Object>> FINDINGS = List.of(
        Map.of(
            "id", "predictable-randomness",
            "title", "Predictable bonus path",
            "severity", "critical",
            "method", "withdraw()",
            "summary", "The gift amount depends on block-derived randomness, making the payout path observable and manipulable."
        ),
        Map.of(
            "id", "balance-overwrite",
            "title", "Deposits overwrite previous balance",
            "severity", "high",
            "method", "deposit(uint256,address,string)",
            "summary", "A new deposit replaces the recipient balance instead of accumulating it, which can wipe previously locked funds."
        ),
        Map.of(
            "id", "silent-signup",
            "title", "Duplicate signup is ignored silently",
            "severity", "medium",
            "method", "signup(string)",
            "summary", "Calling signup twice does not revert or emit a signal, which can hide unexpected user flows during testing."
        )
    );

    private static final List<Map<String, Object>> METHOD_CARDS = List.of(
        Map.of(
            "signature", "signup(string)",
            "description", "Registers the recipient alias and sets a sentinel unlock block."
        ),
        Map.of(
            "signature", "deposit(uint256,address,string)",
            "description", "Schedules a payment for the recipient and rewrites balance state."
        ),
        Map.of(
            "signature", "withdraw()",
            "description", "Withdraws the current balance and may trigger the bonus path after unlock."
        ),
        Map.of(
            "signature", "balance(address)",
            "description", "Read-only view for the locked amount assigned to a wallet."
        ),
        Map.of(
            "signature", "withdraw_time(address)",
            "description", "Read-only view for the block height at which funds unlock."
        ),
        Map.of(
            "signature", "user(address)",
            "description", "Read-only view for the registered alias hash."
        )
    );

    private final Path repoRoot;
    private final Map<String, Object> contractPayload;

    ContractCatalog(Path repoRoot) throws IOException {
        this.repoRoot = repoRoot;
        this.contractPayload = loadContractPayload();
    }

    Map<String, Object> findingsPayload() {
        return Map.of("findings", FINDINGS);
    }

    Map<String, Object> contractPayload() {
        return contractPayload;
    }

    Map<String, Object> servicePayload() {
        return Map.of(
            "service", "baby-bank-java-backend",
            "description", "In-memory Java API for the Baby Bank demo contract.",
            "endpoints", List.of(
                endpoint("GET", "/api/health", "Liveness probe."),
                endpoint("GET", "/api/contract", "Contract metadata plus ABI from the Foundry artifact."),
                endpoint("GET", "/api/findings", "Audit findings mirrored from the frontend."),
                endpoint("GET", "/api/state", "Current in-memory simulation state."),
                endpoint("POST", "/api/signup", "Register the recipient alias."),
                endpoint("POST", "/api/deposit", "Lock a deposit for the active wallet."),
                endpoint("POST", "/api/withdraw", "Withdraw the simulated balance."),
                endpoint("POST", "/api/advance-blocks", "Move the local block counter forward."),
                endpoint("POST", "/api/reset", "Restore the default demo state.")
            )
        );
    }

    private static Map<String, Object> endpoint(String method, String path, String description) {
        return Map.of("method", method, "path", path, "description", description);
    }

    private Map<String, Object> loadContractPayload() throws IOException {
        Path sourcePath = repoRoot.resolve("contracts/baby_bank.sol");
        Path artifactPath = repoRoot.resolve("out/baby_bank.sol/baby_bank.json");

        String sourceContent = Files.readString(sourcePath);
        String artifactContent = Files.readString(artifactPath);
        Object parsedArtifact = JsonCodec.parse(artifactContent);
        if (!(parsedArtifact instanceof Map<?, ?> artifactObject)) {
            throw new IOException("Unexpected artifact structure in " + artifactPath);
        }

        String pragma = extractPragma(sourceContent);
        Map<String, Object> metadata = getObject(artifactObject, "metadata");
        Map<String, Object> compiler = getObject(metadata, "compiler");

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "baby_bank");
        payload.put("sourcePath", repoRoot.relativize(sourcePath).toString());
        payload.put("artifactPath", repoRoot.relativize(artifactPath).toString());
        payload.put("pragma", pragma);
        payload.put("compilerVersion", compiler.get("version"));
        payload.put("abi", getList(artifactObject, "abi"));
        payload.put("methodIdentifiers", getObject(artifactObject, "methodIdentifiers"));
        payload.put("methods", METHOD_CARDS);
        payload.put("findings", FINDINGS);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getObject(Map<?, ?> source, String key) {
        Object value = source.get(key);
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        throw new IllegalArgumentException("Expected object at key: " + key);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getList(Map<?, ?> source, String key) {
        Object value = source.get(key);
        if (value instanceof List<?> listValue) {
            return (List<Object>) listValue;
        }
        throw new IllegalArgumentException("Expected array at key: " + key);
    }

    private static String extractPragma(String sourceContent) {
        for (String line : sourceContent.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("pragma solidity")) {
                return trimmed
                    .replace("pragma solidity", "")
                    .replace(";", "")
                    .trim();
            }
        }
        return "unknown";
    }
}

final class JsonCodec {
    private JsonCodec() {}

    static String toJson(Object value) {
        StringBuilder builder = new StringBuilder();
        writeJson(builder, value);
        return builder.toString();
    }

    static Object parse(String json) {
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.ensureFullyConsumed();
        return value;
    }

    private static void writeJson(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
            return;
        }

        if (value instanceof String stringValue) {
            writeString(builder, stringValue);
            return;
        }

        if (value instanceof BigDecimal decimalValue) {
            builder.append(decimalValue.toPlainString());
            return;
        }

        if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
            return;
        }

        if (value instanceof Map<?, ?> mapValue) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                writeString(builder, String.valueOf(entry.getKey()));
                builder.append(':');
                writeJson(builder, entry.getValue());
            }
            builder.append('}');
            return;
        }

        if (value instanceof Iterable<?> iterableValue) {
            builder.append('[');
            boolean first = true;
            for (Object item : iterableValue) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                writeJson(builder, item);
            }
            builder.append(']');
            return;
        }

        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
    }

    private static void writeString(StringBuilder builder, String value) {
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (current < 0x20) {
                        builder.append(String.format("\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
                }
            }
        }
        builder.append('"');
    }

    private static final class Parser {
        private final String input;
        private int index;

        private Parser(String input) {
            this.input = input;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= input.length()) {
                throw error("Expected a JSON value.");
            }

            char current = input.charAt(index);
            return switch (current) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseKeyword("true", Boolean.TRUE);
                case 'f' -> parseKeyword("false", Boolean.FALSE);
                case 'n' -> parseKeyword("null", null);
                default -> {
                    if (current == '-' || Character.isDigit(current)) {
                        yield parseNumber();
                    }
                    throw error("Unexpected character: " + current);
                }
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            skipWhitespace();

            Map<String, Object> object = new LinkedHashMap<>();
            if (peek('}')) {
                index++;
                return object;
            }

            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                object.put(key, value);
                skipWhitespace();

                if (peek('}')) {
                    index++;
                    return object;
                }

                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            skipWhitespace();

            List<Object> array = new ArrayList<>();
            if (peek(']')) {
                index++;
                return array;
            }

            while (true) {
                array.add(parseValue());
                skipWhitespace();

                if (peek(']')) {
                    index++;
                    return array;
                }

                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();

            while (index < input.length()) {
                char current = input.charAt(index++);
                if (current == '"') {
                    return builder.toString();
                }

                if (current != '\\') {
                    builder.append(current);
                    continue;
                }

                if (index >= input.length()) {
                    throw error("Invalid escape sequence.");
                }

                char escaped = input.charAt(index++);
                switch (escaped) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> builder.append(parseUnicodeEscape());
                    default -> throw error("Unsupported escape sequence: \\" + escaped);
                }
            }

            throw error("Unterminated string literal.");
        }

        private char parseUnicodeEscape() {
            if (index + 4 > input.length()) {
                throw error("Incomplete unicode escape.");
            }

            String hex = input.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException exception) {
                throw error("Invalid unicode escape: " + hex);
            }
        }

        private Object parseKeyword(String keyword, Object value) {
            if (!input.startsWith(keyword, index)) {
                throw error("Expected " + keyword + ".");
            }

            index += keyword.length();
            return value;
        }

        private Number parseNumber() {
            int start = index;

            if (peek('-')) {
                index++;
            }

            parseDigits();

            boolean decimal = false;
            if (peek('.')) {
                decimal = true;
                index++;
                parseDigits();
            }

            if (peek('e') || peek('E')) {
                decimal = true;
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                parseDigits();
            }

            String token = input.substring(start, index);
            try {
                if (decimal) {
                    return new BigDecimal(token);
                }

                BigInteger integerValue = new BigInteger(token);
                if (integerValue.bitLength() < Long.SIZE) {
                    return integerValue.longValue();
                }
                return integerValue;
            } catch (NumberFormatException exception) {
                throw error("Invalid number: " + token);
            }
        }

        private void parseDigits() {
            int start = index;
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }

            if (start == index) {
                throw error("Expected at least one digit.");
            }
        }

        private void ensureFullyConsumed() {
            skipWhitespace();
            if (index != input.length()) {
                throw error("Unexpected trailing content.");
            }
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private boolean peek(char expected) {
            return index < input.length() && input.charAt(index) == expected;
        }

        private void expect(char expected) {
            if (!peek(expected)) {
                throw error("Expected '" + expected + "'.");
            }
            index++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " Position: " + index);
        }
    }
}
