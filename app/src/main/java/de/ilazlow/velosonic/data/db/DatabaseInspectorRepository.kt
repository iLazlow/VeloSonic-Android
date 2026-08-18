package de.ilazlow.velosonic.data.db

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class TableSummary(val name: String, val rowCount: Int)
data class TableRow(val rowId: Long, val values: Map<String, String?>)

/**
 * Generic raw-SQLite passthrough over Room's underlying database — mirrors iOS's
 * `DatabaseViewerView`/`DatabaseTableView`/`DatabaseRowDetailView` trio: list every table + row
 * count, browse a table's rows paginated, and hand-edit a single row's column values. This
 * intentionally bypasses every DAO/entity mapping (raw column names/values as strings) since the
 * whole point is an unopinionated debug browser, not a typed data-access layer.
 */
@Singleton
class DatabaseInspectorRepository @Inject constructor(
    private val database: VeloSonicDatabase
) {
    suspend fun listTables(): List<TableSummary> = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        val names = mutableListOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_master_table' AND name NOT LIKE 'android_metadata' ORDER BY name"
        ).use { cursor ->
            while (cursor.moveToNext()) names.add(cursor.getString(0))
        }
        names.map { name ->
            var count = 0
            db.query("SELECT COUNT(*) FROM \"$name\"").use { cursor ->
                if (cursor.moveToFirst()) count = cursor.getInt(0)
            }
            TableSummary(name, count)
        }
    }

    suspend fun getRows(table: String, limit: Int, offset: Int): List<TableRow> = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        val rows = mutableListOf<TableRow>()
        db.query(SimpleSQLiteQuery("SELECT rowid, * FROM \"$table\" LIMIT ? OFFSET ?", arrayOf(limit, offset))).use { cursor ->
            val columnNames = cursor.columnNames
            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(0)
                val values = LinkedHashMap<String, String?>()
                for (i in 1 until columnNames.size) {
                    values[columnNames[i]] = if (cursor.isNull(i)) null else cursor.getString(i)
                }
                rows.add(TableRow(rowId, values))
            }
        }
        rows
    }

    suspend fun getRow(table: String, rowId: Long): TableRow? = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        var result: TableRow? = null
        db.query(SimpleSQLiteQuery("SELECT rowid, * FROM \"$table\" WHERE rowid = ?", arrayOf(rowId))).use { cursor ->
            val columnNames = cursor.columnNames
            if (cursor.moveToFirst()) {
                val values = LinkedHashMap<String, String?>()
                for (i in 1 until columnNames.size) {
                    values[columnNames[i]] = if (cursor.isNull(i)) null else cursor.getString(i)
                }
                result = TableRow(rowId, values)
            }
        }
        result
    }

    suspend fun updateRowColumn(table: String, rowId: Long, column: String, value: String?) = withContext(Dispatchers.IO) {
        val db = database.openHelper.writableDatabase
        db.execSQL("UPDATE \"$table\" SET \"$column\" = ? WHERE rowid = ?", arrayOf<Any?>(value, rowId))
    }

    /** Room-equivalent of SwiftData's persistent-history trim (`DatabaseBackupManager.
     *  cleanupPersistentHistory`) — Room/SQLite has no separate history log to trim, but a WAL
     *  checkpoint achieves the same practical goal (folds the write-ahead log back into the main
     *  db file, shrinking on-disk footprint) and is the closest real, safe action to offer here. */
    suspend fun cleanUpHistory() = withContext(Dispatchers.IO) {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { }
    }
}
