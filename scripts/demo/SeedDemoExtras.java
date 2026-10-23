import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Inserts fictional fixtures without overwriting existing rows.
 * java --class-path postgresql.jar SeedDemoExtras.java repoRoot seedDate newlyCreatedUserNamesCsv
 * Final argument lists ONLY users newly created by the current seed run; use an empty argument on reruns.
 * Credentials are read from repoRoot/.env and never printed or accepted as CLI arguments.
 */
public class SeedDemoExtras {
    private static final String DATABASE = "enterprise_risk_db", SOURCE = "DEMO_FIXTURE";
    private record Event(String key, String type, String root, String actor1, String actor2,
                         String countryCode, String country, String location, String goldstein, String tone) {}
    private record SavedEvent(String key, String id, String date) {}

    public static void main(String[] args) {
        try { run(args); }
        catch (SQLException failure) {
            // Do not print JDBC messages, URLs or credentials.
            System.err.println("SeedDemoExtras failed; transaction rolled back. SQLSTATE="
                    + failure.getSQLState() + "; errorCode=" + failure.getErrorCode());
            System.exit(1);
        } catch (IllegalArgumentException failure) {
            System.err.println("SeedDemoExtras rejected input: " + failure.getMessage()); System.exit(1);
        } catch (Exception failure) {
            System.err.println("SeedDemoExtras failed without printing credentials; errorType="
                    + failure.getClass().getSimpleName()); System.exit(1);
        }
    }
    private static void run(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException(
                "Expected repoRoot, seedDate (yyyy-MM-dd), newlyCreatedUserNamesCsv (empty or omitted on rerun)");
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("backend/pom.xml")) || !Files.isRegularFile(root.resolve(".env")))
            throw new IllegalArgumentException("repoRoot must contain backend/pom.xml and .env");
        LocalDate seedDate = LocalDate.parse(args[1]);
        Set<String> users = new LinkedHashSet<>();
        String newUserNamesCsv = args.length == 3 ? args[2] : "";
        if (!newUserNamesCsv.isBlank()) for (String raw : newUserNamesCsv.split(",", -1)) {
            String name = raw.trim();
            if (!name.matches("^demo_member_(0[1-9]|1[0-9]|20)$"))
                throw new IllegalArgumentException("Only newly created demo_member_01 through demo_member_20 are allowed");
            if (!users.add(name)) throw new IllegalArgumentException("Duplicate demo member names are not allowed");
        }
        Map<String, String> env = readEnv(root.resolve(".env"));
        if (!DATABASE.equals(env.get("DB_NAME")))
            throw new IllegalArgumentException("DB_NAME must be exactly enterprise_risk_db");
        String rawPort = env.getOrDefault("DB_PORT", "5433");
        if (!rawPort.matches("[0-9]{1,5}")) throw new IllegalArgumentException("DB_PORT must be an integer TCP port");
        int port = Integer.parseInt(rawPort);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("DB_PORT is outside 1..65535");
        String url = "jdbc:postgresql://127.0.0.1:" + port + "/" + DATABASE
                + "?connectTimeout=10&socketTimeout=30&ApplicationName=SeedDemoExtras";
        try (Connection connection = DriverManager.getConnection(url,
                env.getOrDefault("DB_USER", "postgres"), env.getOrDefault("DB_PASSWORD", "postgres"))) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(72590432612345)")) {
                    lock.execute();
                }
                try (PreparedStatement check = connection.prepareStatement("SELECT current_database()")) {
                    try (ResultSet row = check.executeQuery()) {
                        if (!row.next() || !DATABASE.equals(row.getString(1)))
                            throw new IllegalArgumentException("Connected database is not the allowed local demo database");
                    }
                }
                List<SavedEvent> events = insertEvents(connection, seedDate);
                int count = updateNewUsers(connection, users);
                connection.commit();
                StringBuilder json = new StringBuilder("{\"externalSource\":\"").append(SOURCE)
                        .append("\",\"passwordsUpdated\":").append(count).append(",\"events\":[");
                for (int index = 0; index < events.size(); index++) {
                    if (index > 0) json.append(',');
                    SavedEvent event = events.get(index);
                    json.append("{\"externalEventId\":\"").append(event.key())
                            .append("\",\"id\":\"").append(event.id()).append("\",\"eventDate\":\"")
                            .append(event.date()).append("\",\"externalSource\":\"").append(SOURCE).append("\"}");
                }
                System.out.println(json.append("]}"));
            } catch (Exception failure) { connection.rollback(); throw failure; }
        }
    }
    private static Map<String, String> readEnv(Path file) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.replace("\uFEFF", "").trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("export ")) line = line.substring(7).trim();
            int separator = line.indexOf('=');
            if (separator < 1) continue;
            String key = line.substring(0, separator).trim();
            if (!Set.of("DB_NAME", "DB_PORT", "DB_USER", "DB_PASSWORD").contains(key)) continue;
            String value = line.substring(separator + 1).trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length() - 1);
            values.put(key, value);
        }
        return values;
    }
    private static List<SavedEvent> insertEvents(Connection connection, LocalDate date) throws SQLException {
        List<Event> fixtures = List.of(
            new Event("DEMO-EVT-01", "[DEMO] Production shutdown - EN", "20", "Atlas Components", "Pacific Supply", "US", "USA", "Texas", "-10", "-8"),
            new Event("DEMO-EVT-02", "[DEMO] Expansion and new contract - EN", "01", "Atlas Components", "Pacific Supply", "US", "USA", "Texas", "5", "4"),
            new Event("DEMO-EVT-03", "[DEMO] Gian doan san xuat - VI", "19", "Minh Phat", null, "VN", "Vietnam", "Binh Duong", "-8", "-6"),
            new Event("DEMO-EVT-04", "[DEMO] Unrelated harbor disruption", "14", "Northhaven Port Authority", null, "IS", "Iceland", "Reykjavik", "-4", "-3"),
            new Event("DEMO-EVT-05", "[DEMO] Chua phan tich - thao tac truc tiep", "17", "Atlas Components", "Pacific Supply", "US", "USA", "Texas", "-6", "-3")
        );
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO external_events (external_source, external_event_id, event_date, event_type,
                    event_root_code, actor1_name, actor2_name, country_code, country, location,
                    goldstein_score, avg_tone, source_url, sentiment, signal, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NOT_ANALYZED', 'NEUTRAL', 0)
                ON CONFLICT (external_source, external_event_id) DO NOTHING
                """)) {
            for (Event event : fixtures) {
                insert.setString(1, SOURCE); insert.setString(2, event.key()); insert.setObject(3, date);
                insert.setString(4, event.type()); insert.setString(5, event.root());
                insert.setString(6, event.actor1()); insert.setString(7, event.actor2());
                insert.setString(8, event.countryCode()); insert.setString(9, event.country());
                insert.setString(10, event.location()); insert.setBigDecimal(11, new BigDecimal(event.goldstein()));
                insert.setBigDecimal(12, new BigDecimal(event.tone()));
                insert.setString(13, "https://example.invalid/demo/" + event.key()); insert.executeUpdate();
            }
        }
        List<SavedEvent> saved = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, external_event_id, event_date FROM external_events
                WHERE external_source = ? AND external_event_id IN (?, ?, ?, ?, ?) ORDER BY external_event_id
                """)) {
            select.setString(1, SOURCE);
            for (int index = 0; index < fixtures.size(); index++) select.setString(index + 2, fixtures.get(index).key());
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) saved.add(new SavedEvent(rows.getString("external_event_id"),
                        rows.getString("id"), rows.getObject("event_date", LocalDate.class).toString()));
            }
        }
        if (saved.size() != 5) throw new SQLException("Fixture row count mismatch", "P0001");
        return saved;
    }
    private static int updateNewUsers(Connection connection, Set<String> names) throws SQLException {
        if (names.isEmpty()) return 0;
        int updated = 0;
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE users SET password_hash = crypt(?, gen_salt('bf', 10)),
                    updated_at = now(), version = version + 1 WHERE username = ?
                """)) {
            for (String name : names) {
                update.setString(1, "123456"); update.setString(2, name);
                int count = update.executeUpdate();
                if (count != 1) throw new SQLException("Expected one newly created demo member", "P0001");
                updated += count;
            }
        }
        return updated;
    }
}

