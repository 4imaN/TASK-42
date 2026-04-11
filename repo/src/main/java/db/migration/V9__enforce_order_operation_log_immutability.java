package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.Statement;

/**
 * DB-level immutability triggers for order_operation_logs.
 *
 * <p>Creates BEFORE UPDATE and BEFORE DELETE triggers that reject any mutation,
 * complementing the JPA {@code @PreUpdate}/{@code @PreRemove} callbacks on the entity.
 *
 * <p>On MySQL the triggers use {@code SIGNAL SQLSTATE '45000'}:
 * <pre>{@code
 * CREATE TRIGGER prevent_oplog_update BEFORE UPDATE ON order_operation_logs
 * FOR EACH ROW SIGNAL SQLSTATE '45000'
 * SET MESSAGE_TEXT = 'Operation logs are immutable and cannot be updated';
 *
 * CREATE TRIGGER prevent_oplog_delete BEFORE DELETE ON order_operation_logs
 * FOR EACH ROW SIGNAL SQLSTATE '45000'
 * SET MESSAGE_TEXT = 'Operation logs are immutable and cannot be deleted';
 * }</pre>
 *
 * <p>On H2 (test profile) triggers are not created because H2 does not support
 * {@code SIGNAL}. Immutability is enforced by the JPA callbacks, which are
 * tested in {@code OrderServiceIntegrationTest}.
 */
public class V9__enforce_order_operation_log_immutability extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        String dbProduct = conn.getMetaData().getDatabaseProductName();

        if (!"MySQL".equalsIgnoreCase(dbProduct)) {
            // H2 or other non-MySQL databases: skip trigger creation.
            // Immutability is enforced at the JPA layer.
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TRIGGER prevent_oplog_update BEFORE UPDATE ON order_operation_logs " +
                "FOR EACH ROW SIGNAL SQLSTATE '45000' " +
                "SET MESSAGE_TEXT = 'Operation logs are immutable and cannot be updated'"
            );
            stmt.execute(
                "CREATE TRIGGER prevent_oplog_delete BEFORE DELETE ON order_operation_logs " +
                "FOR EACH ROW SIGNAL SQLSTATE '45000' " +
                "SET MESSAGE_TEXT = 'Operation logs are immutable and cannot be deleted'"
            );
        }
    }
}
