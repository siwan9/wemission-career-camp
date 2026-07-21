package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V2__Ensure_registration_constraints extends BaseJavaMigration {

	@Override
	public void migrate(Context context) throws Exception {
		Connection connection = context.getConnection();

		ensureInnoDbTables(connection);
		dropObsoleteDraftModeColumn(connection);
		removeExpiredAndDuplicateDrafts(connection);
		ensureSingleActiveRecruitmentConstraint(connection);

		ensureUniqueIndex(
			connection,
			"participant_lecture_drafts",
			"uk_participant_lecture_drafts_token_type",
			List.of("draft_token", "lecture_type")
		);
		ensureUniqueIndex(
			connection,
			"participant_lectures",
			"uk_participant_lectures_participant",
			List.of("participant_id")
		);
		ensureUniqueIndex(
			connection,
			"recruitment_participant_types",
			"uk_recruitment_participant_types_recruitment_type",
			List.of("recruitment_id", "participant_type_id")
		);

		ensureIndex(connection, "recruitments", "idx_recruitments_status_id", List.of("status", "id"));
		ensureIndex(
			connection,
			"recruitment_churches",
			"idx_recruitment_churches_recruitment_sort",
			List.of("recruitment_id", "sort_order", "id")
		);
		ensureIndex(
			connection,
			"lectures",
			"idx_lectures_recruitment_type_open_sort",
			List.of("recruitment_id", "type", "is_open", "sort_order", "id")
		);
		ensureIndex(
			connection,
			"participants",
			"idx_participants_lookup",
			List.of("recruitment_id", "recruitment_church_id", "name", "phone_number")
		);
		ensureIndex(
			connection,
			"participant_lecture_drafts",
			"idx_participant_lecture_drafts_lecture_expires",
			List.of("lecture_id", "expires_at")
		);
		ensureIndex(
			connection,
			"participant_lecture_drafts",
			"idx_participant_lecture_drafts_token_expires",
			List.of("draft_token", "expires_at")
		);
		ensureIndex(
			connection,
			"participant_lecture_drafts",
			"idx_participant_lecture_drafts_expires",
			List.of("expires_at")
		);
		ensureIndex(
			connection,
			"participant_lecture_drafts",
			"idx_participant_lecture_drafts_recruitment_expires_lecture",
			List.of("recruitment_id", "expires_at", "lecture_id")
		);
		ensureIndex(
			connection,
			"participant_lecture_drafts",
			"idx_participant_lecture_drafts_participant",
			List.of("participant_id")
		);

		ensureForeignKey(connection, "lectures", "recruitment_id", "recruitments", "fk_lectures_recruitment");
		ensureForeignKey(
			connection,
			"recruitment_churches",
			"recruitment_id",
			"recruitments",
			"fk_recruitment_churches_recruitment"
		);
		ensureForeignKey(
			connection,
			"recruitment_participant_types",
			"recruitment_id",
			"recruitments",
			"fk_recruitment_participant_types_recruitment"
		);
		ensureForeignKey(
			connection,
			"recruitment_participant_types",
			"participant_type_id",
			"participant_types",
			"fk_recruitment_participant_types_participant_type"
		);
		ensureForeignKey(connection, "participants", "recruitment_id", "recruitments", "fk_participants_recruitment");
		ensureForeignKey(
			connection,
			"participants",
			"recruitment_church_id",
			"recruitment_churches",
			"fk_participants_recruitment_church"
		);
		ensureForeignKey(
			connection,
			"participants",
			"participant_type_id",
			"participant_types",
			"fk_participants_participant_type"
		);
		ensureForeignKey(
			connection,
			"participant_lectures",
			"participant_id",
			"participants",
			"fk_participant_lectures_participant"
		);
		ensureForeignKey(
			connection,
			"participant_lectures",
			"morning_lecture_id",
			"lectures",
			"fk_participant_lectures_morning"
		);
		ensureForeignKey(
			connection,
			"participant_lectures",
			"afternoon_lecture_id",
			"lectures",
			"fk_participant_lectures_afternoon"
		);
		ensureForeignKey(
			connection,
			"participant_lecture_drafts",
			"participant_id",
			"participants",
			"fk_participant_lecture_drafts_participant"
		);
		ensureForeignKey(
			connection,
			"participant_lecture_drafts",
			"recruitment_id",
			"recruitments",
			"fk_participant_lecture_drafts_recruitment"
		);
		ensureForeignKey(
			connection,
			"participant_lecture_drafts",
			"lecture_id",
			"lectures",
			"fk_participant_lecture_drafts_lecture"
		);
	}

	private void ensureInnoDbTables(Connection connection) throws SQLException {
		for (String table : List.of(
			"recruitments",
			"participant_types",
			"recruitment_churches",
			"recruitment_participant_types",
			"lectures",
			"participants",
			"participant_lectures",
			"participant_lecture_drafts",
			"admins"
		)) {
			if (!isInnoDb(connection, table)) {
				execute(connection, "ALTER TABLE " + table + " ENGINE = InnoDB");
			}
		}
	}

	private boolean isInnoDb(Connection connection, String table) throws SQLException {
		String sql = """
			SELECT engine
			FROM information_schema.tables
			WHERE table_schema = DATABASE()
			  AND table_name = ?
			""";

		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, table);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() && "INNODB".equalsIgnoreCase(resultSet.getString("engine"));
			}
		}
	}

	private void ensureSingleActiveRecruitmentConstraint(Connection connection) throws SQLException {
		if (!hasColumn(connection, "recruitments", "active_registration_slot")) {
			execute(
				connection,
				"""
				ALTER TABLE recruitments
				ADD COLUMN active_registration_slot TINYINT
				GENERATED ALWAYS AS (
				    CASE WHEN status IN ('OPEN', 'WAITING') THEN 1 ELSE NULL END
				) STORED
				"""
			);
		}

		ensureUniqueIndex(
			connection,
			"recruitments",
			"uk_recruitments_single_active",
			List.of("active_registration_slot")
		);
	}

	private void dropObsoleteDraftModeColumn(Connection connection) throws SQLException {
		if (hasColumn(connection, "participant_lecture_drafts", "mode")) {
			execute(connection, "ALTER TABLE participant_lecture_drafts DROP COLUMN mode");
		}
	}

	private void removeExpiredAndDuplicateDrafts(Connection connection) throws SQLException {
		execute(
			connection,
			"DELETE FROM participant_lecture_drafts WHERE expires_at <= CURRENT_TIMESTAMP(6)"
		);
		execute(
			connection,
			"""
			DELETE older
			FROM participant_lecture_drafts older
			JOIN participant_lecture_drafts newer
			  ON older.draft_token = newer.draft_token
			 AND older.lecture_type = newer.lecture_type
			 AND (
			      older.updated_at < newer.updated_at
			      OR (older.updated_at = newer.updated_at AND older.id < newer.id)
			 )
			"""
		);
	}

	private void ensureUniqueIndex(
		Connection connection,
		String table,
		String indexName,
		List<String> columns
	) throws SQLException {
		if (!hasIndex(connection, table, columns, true)) {
			execute(connection, buildCreateIndexSql(table, indexName, columns, true));
		}
	}

	private void ensureIndex(
		Connection connection,
		String table,
		String indexName,
		List<String> columns
	) throws SQLException {
		if (!hasIndex(connection, table, columns, false)) {
			execute(connection, buildCreateIndexSql(table, indexName, columns, false));
		}
	}

	private boolean hasIndex(
		Connection connection,
		String table,
		List<String> expectedColumns,
		boolean uniqueRequired
	) throws SQLException {
		Map<String, IndexDefinition> indexes = new HashMap<>();
		String sql = """
			SELECT index_name, non_unique, seq_in_index, column_name
			FROM information_schema.statistics
			WHERE table_schema = DATABASE()
			  AND table_name = ?
			ORDER BY index_name, seq_in_index
			""";

		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, table);
			try (ResultSet resultSet = statement.executeQuery()) {
				while (resultSet.next()) {
					String indexName = resultSet.getString("index_name");
					IndexDefinition definition = indexes.computeIfAbsent(
						indexName,
						key -> new IndexDefinition(resultSetBoolean(resultSet, "non_unique"))
					);
					definition.addColumn(
						resultSet.getInt("seq_in_index"),
						resultSet.getString("column_name")
					);
				}
			}
		}

		List<String> normalizedExpectedColumns = normalize(expectedColumns);
		return indexes.values().stream().anyMatch(index -> {
			if (uniqueRequired) {
				return !index.nonUnique() && index.columns().equals(normalizedExpectedColumns);
			}

			return startsWith(index.columns(), normalizedExpectedColumns);
		});
	}

	private boolean resultSetBoolean(ResultSet resultSet, String column) {
		try {
			return resultSet.getBoolean(column);
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	private boolean startsWith(List<String> actual, List<String> expected) {
		return actual.size() >= expected.size()
			&& actual.subList(0, expected.size()).equals(expected);
	}

	private List<String> normalize(List<String> columns) {
		return columns.stream()
			.map(column -> column.toLowerCase(Locale.ROOT))
			.toList();
	}

	private String buildCreateIndexSql(
		String table,
		String indexName,
		List<String> columns,
		boolean unique
	) {
		return "CREATE " + (unique ? "UNIQUE " : "") + "INDEX " + indexName
			+ " ON " + table + " (" + String.join(", ", columns) + ")";
	}

	private void ensureForeignKey(
		Connection connection,
		String table,
		String column,
		String referencedTable,
		String constraintName
	) throws SQLException {
		if (hasForeignKey(connection, table, column, referencedTable)) {
			return;
		}

		execute(
			connection,
			"ALTER TABLE " + table
				+ " ADD CONSTRAINT " + constraintName
				+ " FOREIGN KEY (" + column + ") REFERENCES " + referencedTable + " (id)"
		);
	}

	private boolean hasForeignKey(
		Connection connection,
		String table,
		String column,
		String referencedTable
	) throws SQLException {
		String sql = """
			SELECT COUNT(*)
			FROM information_schema.key_column_usage
			WHERE table_schema = DATABASE()
			  AND table_name = ?
			  AND column_name = ?
			  AND referenced_table_name = ?
			""";

		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, table);
			statement.setString(2, column);
			statement.setString(3, referencedTable);
			try (ResultSet resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getInt(1) > 0;
			}
		}
	}

	private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
		String sql = """
			SELECT COUNT(*)
			FROM information_schema.columns
			WHERE table_schema = DATABASE()
			  AND table_name = ?
			  AND column_name = ?
			""";

		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, table);
			statement.setString(2, column);
			try (ResultSet resultSet = statement.executeQuery()) {
				resultSet.next();
				return resultSet.getInt(1) > 0;
			}
		}
	}

	private void execute(Connection connection, String sql) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private static final class IndexDefinition {
		private final boolean nonUnique;
		private final List<IndexedColumn> indexedColumns = new ArrayList<>();

		private IndexDefinition(boolean nonUnique) {
			this.nonUnique = nonUnique;
		}

		private void addColumn(int position, String column) {
			indexedColumns.add(new IndexedColumn(position, column.toLowerCase(Locale.ROOT)));
		}

		private boolean nonUnique() {
			return nonUnique;
		}

		private List<String> columns() {
			return indexedColumns.stream()
				.sorted(Comparator.comparingInt(IndexedColumn::position))
				.map(IndexedColumn::column)
				.toList();
		}
	}

	private record IndexedColumn(int position, String column) {
	}
}
