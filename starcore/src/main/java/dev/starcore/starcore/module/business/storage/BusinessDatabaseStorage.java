package dev.starcore.starcore.module.business.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.starcore.starcore.core.database.DatabaseService;
import dev.starcore.starcore.module.business.BusinessService.*;
import dev.starcore.starcore.module.nation.model.NationId;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * 商业数据数据库存储
 */
public class BusinessDatabaseStorage {

    private final DatabaseService databaseService;
    private final Logger logger;

    private static final String TABLE_TRANSACTIONS = "business_transactions";
    private static final String TABLE_PLAYER_INDEX = "business_player_index";
    private static final String TABLE_NATION_INDEX = "business_nation_index";

    public BusinessDatabaseStorage(DatabaseService databaseService, Logger logger) {
        this.databaseService = databaseService;
        this.logger = logger;
        initializeTables();
    }

    private void initializeTables() {
        databaseService.execute(
            "CREATE TABLE IF NOT EXISTS " + TABLE_TRANSACTIONS + " (" +
            "transaction_id TEXT PRIMARY KEY, player_id TEXT NOT NULL, nation_id TEXT NOT NULL, " +
            "type TEXT NOT NULL, category TEXT NOT NULL, amount TEXT NOT NULL, " +
            "timestamp INTEGER NOT NULL, created_at TEXT NOT NULL)"
        );

        databaseService.execute(
            "CREATE TABLE IF NOT EXISTS " + TABLE_PLAYER_INDEX + " (" +
            "player_id TEXT NOT NULL, nation_id TEXT NOT NULL, " +
            "first_transaction INTEGER, last_transaction INTEGER, " +
            "transaction_count INTEGER DEFAULT 0, total_volume TEXT DEFAULT 0, " +
            "PRIMARY KEY(player_id, nation_id))"
        );

        databaseService.execute(
            "CREATE TABLE IF NOT EXISTS " + TABLE_NATION_INDEX + " (" +
            "nation_id TEXT PRIMARY KEY, total_transactions INTEGER DEFAULT 0, " +
            "total_volume TEXT DEFAULT 0, last_updated INTEGER)"
        );

        databaseService.execute(
            "CREATE INDEX IF NOT EXISTS idx_tx_player ON " + TABLE_TRANSACTIONS + "(player_id, timestamp)"
        );
        databaseService.execute(
            "CREATE INDEX IF NOT EXISTS idx_tx_nation ON " + TABLE_TRANSACTIONS + "(nation_id, timestamp)"
        );
    }

    public void saveTransaction(BusinessTransaction tx) {
        String sql = "INSERT OR REPLACE INTO " + TABLE_TRANSACTIONS +
            " (transaction_id, player_id, nation_id, type, category, amount, timestamp, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            databaseService.update(sql,
                tx.transactionId().toString(),
                tx.playerId().toString(),
                tx.nationId().toString(),
                tx.type().name(),
                tx.category().name(),
                tx.amount().toPlainString(),
                tx.timestamp(),
                Instant.now().toString()
            );
        } catch (Exception e) {
            logger.warning("Failed to save transaction: " + e.getMessage());
        }
    }

    public LoadedAllData loadAllData() {
        List<BusinessTransaction> all = new ArrayList<>();
        String sql = "SELECT * FROM " + TABLE_TRANSACTIONS + " ORDER BY timestamp DESC LIMIT 100000";

        try {
            databaseService.query(sql, rs -> {
                try {
                    while (rs.next()) {
                        try {
                            all.add(loadTransaction(rs));
                        } catch (Exception e) {
                            logger.warning("Failed to load tx: " + e.getMessage());
                        }
                    }
                } catch (SQLException e) {
                    logger.warning("Failed to iterate results: " + e.getMessage());
                }
                return null;
            });
        } catch (Exception e) {
            logger.warning("Failed to load transactions: " + e.getMessage());
        }

        return new LoadedAllData(all, all);
    }

    private BusinessTransaction loadTransaction(ResultSet rs) throws SQLException {
        return new BusinessTransaction(
            UUID.fromString(rs.getString("transaction_id")),
            UUID.fromString(rs.getString("player_id")),
            NationId.of(UUID.fromString(rs.getString("nation_id"))),
            BusinessTransactionType.valueOf(rs.getString("type")),
            BusinessCategory.valueOf(rs.getString("category")),
            new BigDecimal(rs.getString("amount")),
            rs.getLong("timestamp")
        );
    }

    public void saveAll(
            Map<UUID, List<BusinessTransaction>> playerTx,
            Map<String, List<BusinessTransaction>> nationTx) {
        int count = 0;
        for (List<BusinessTransaction> txs : playerTx.values()) {
            for (BusinessTransaction tx : txs) {
                saveTransaction(tx);
                count++;
            }
        }
        for (List<BusinessTransaction> txs : nationTx.values()) {
            for (BusinessTransaction tx : txs) {
                saveTransaction(tx);
                count++;
            }
        }
        logger.info("Saved " + count + " business transactions");
    }

    public record LoadedAllData(List<BusinessTransaction> playerTransactions, List<BusinessTransaction> nationTransactions) {}
}
