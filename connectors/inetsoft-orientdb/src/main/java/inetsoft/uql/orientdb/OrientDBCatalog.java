/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.uql.orientdb;

import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;

/**
 * Assembles OrientDBRuntime's TabularCatalogProvider answers from standard java.sql.
 * DatabaseMetaData calls. Mirrors AerospikeCatalog's role: package-private, static methods, no
 * state of its own.
 *
 * Confirmed against the driver (com.orientechnologies:orientdb-jdbc:3.2.29): {@code
 * OrientJdbcConnection.getCatalog()} returns the connected database's own name and {@code
 * getSchema()} always returns {@code null} -- there is exactly one database per connection, no
 * separate schema concept. {@code getTables}/{@code getColumns}/{@code getPrimaryKeys} never read
 * their {@code catalog}/{@code schemaPattern} arguments to filter matches, but the three methods
 * don't treat their own {@code TABLE_CAT}/{@code TABLE_SCHEM} output columns the same way.
 * {@code OrientJdbcDatabaseMetaData.getTables} and {@code getColumns} (the latter via {@code
 * getPropertyAsDocument}) always report both as {@code database.getName()}, independent of what was
 * passed in. {@code getPrimaryKeys} instead echoes the passed-in {@code catalog} argument verbatim
 * into both columns rather than substituting {@code database.getName()} -- a difference this class
 * never depends on, since
 * {@code primaryKeyColumnNames} below only reads {@code PK_NAME}/{@code COLUMN_NAME}/{@code
 * KEY_SEQ} from that result set. Either way, every call below passes {@code conn.getCatalog()} as
 * catalog and {@code null} as schema purely as a self-documenting, forward-compatible convention --
 * not because today's driver needs it to filter correctly.
 *
 * A class name (the "table" in JDBC terms) is matched two different ways by this driver, and both
 * matter here: {@code getTables} does a literal, case-insensitive {@code equals}/{@code
 * equalsIgnoreCase} comparison against {@code tableNamePattern}, with no pattern semantics at all,
 * while {@code getColumns} runs {@code tableNamePattern} through {@code OrientJdbcUtils.like},
 * which is a real (if partial) SQL LIKE emulation: it escapes regex metacharacters first, then
 * rewrites {@code _} to {@code .} (any single character) and {@code %} to {@code .*?}, and matches
 * case-insensitively. Passing a literal class name to {@code getColumns} is therefore
 * over-matching, not exact -- a class named {@code my_class} will also match {@code myXclass}, and
 * {@code Orders} will also match {@code orders}. Every row this class reads back from either
 * method is additionally filtered by an exact, case-sensitive {@code TABLE_NAME.equals(datasetId)}
 * check before being trusted, rather than relying on the driver's own matching to have been exact.
 */
final class OrientDBCatalog {
   private OrientDBCatalog() {
   }

   static TabularCatalog listDatasets(Connection conn) throws Exception {
      DatabaseMetaData meta = conn.getMetaData();
      String database = conn.getCatalog();
      List<TabularDatasetRef> datasets = new ArrayList<>();

      // types is passed explicitly as {"TABLE"}, never null: OrientJdbcDatabaseMetaData's own
      // TABLE_TYPES default (used whenever types == null) is {"TABLE", "SYSTEM TABLE"} -- passing
      // null would therefore ask for MORE, not fewer, rows than passing nothing. "SYSTEM TABLE" is
      // how this driver classifies its own built-in security/bookkeeping classes (OUser, ORole,
      // OIdentity, ORestricted, OFunction) plus one bare literal, "internal" (OMetadataInternal.
      // SYSTEM_CLUSTER), none of which belong in a data-annotation catalog next to actual user
      // classes. Because "internal" is matched as a literal string rather than tied to an actual
      // reserved class, a user-created class literally named "internal" (any case) would also be
      // classified "SYSTEM TABLE" and silently excluded here -- a driver quirk, not something this
      // connector introduces or works around.
      try(ResultSet rs = meta.getTables(database, null, "%", new String[]{"TABLE"})) {
         while(rs.next()) {
            // Defensive re-check, not required by the types filter above: if a caller (or a test
            // double) ever hands back a row this driver itself would have classified as "SYSTEM
            // TABLE", this loop must not depend solely on the types argument having been honored
            // upstream to keep it out of the catalog.
            if(!"TABLE".equals(rs.getString("TABLE_TYPE"))) {
               continue;
            }

            datasets.add(new TabularDatasetRef(rs.getString("TABLE_NAME")));
         }
      }

      // OrientDB exposes no driver-readable class-to-class reference/foreign-key construct --
      // same "nothing to honestly report" position AerospikeCatalog/HiveCatalog take.
      return new TabularCatalog(datasets, List.of());
   }

   static TabularDatasetSchema describeDataset(Connection conn, String datasetId) throws Exception {
      DatabaseMetaData meta = conn.getMetaData();
      String database = conn.getCatalog();

      if(!classExists(meta, database, datasetId)) {
         throw new Exception("Class '" + datasetId + "' not found in database '" + database + "'.");
      }

      List<TabularColumn> columns = describeColumns(meta, database, datasetId);
      String queryTarget = backtickQuote(datasetId);

      // A class with zero reported columns is schema-less: getColumns walks clazz.properties(),
      // OrientDB's declared-schema API, so zero rows means zero declared properties (inherited
      // ones included -- see below), not "the driver failed to answer". A schema-less class's
      // instances can still carry any runtime field, none of which any driver call can enumerate,
      // so the only column this connector can honestly promise is the real pseudo-property @rid
      // (verified against OrientJdbcResultSet's own read paths for it -- see describeColumns'
      // javadoc note), with columnsMayBeIncomplete signaling that the true field set is unknown.
      if(columns.isEmpty()) {
         return new TabularDatasetSchema(datasetId, List.of(new TabularColumn("@rid", XSchema.STRING)),
            List.of(), Map.of("queryString", "SELECT @rid FROM " + queryTarget), true);
      }

      // keyColumns comes from getPrimaryKeys, which only makes sense once a class is known to have
      // a declared schema; see primaryKeyColumnNames' javadoc for why a schema-less class must
      // skip this call entirely rather than just discard its result.
      List<String> keyColumns = primaryKeyColumnNames(meta, database, datasetId);
      return new TabularDatasetSchema(datasetId, columns, keyColumns,
         Map.of("queryString", "SELECT * FROM " + queryTarget), false);
   }

   private static boolean classExists(DatabaseMetaData meta, String database, String datasetId)
      throws Exception
   {
      try(ResultSet rs = meta.getTables(database, null, datasetId, new String[]{"TABLE"})) {
         while(rs.next()) {
            if(datasetId.equals(rs.getString("TABLE_NAME"))) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * {@code clazz.properties()} (what {@code getColumns} walks internally) recurses into
    * superclasses -- {@code OClassImpl.properties(Collection)} explicitly adds the superclass's
    * properties on top of its own, unlike {@code declaredProperties()}, which does not recurse.
    * That is not a problem this method needs to work around: an inherited property really is a
    * field every instance of this class carries, so reporting it here is correct, not a
    * schema-less false negative. This connector therefore never calls an {@code OClass} API
    * directly -- {@code getColumns}'s own result already reflects the right answer.
    */
   private static List<TabularColumn> describeColumns(DatabaseMetaData meta, String database,
                                                        String datasetId) throws Exception
   {
      OrientDBSQLTypes sqlTypes = new OrientDBSQLTypes();
      // ORDINAL_POSITION here is prop.getId(), which traces back to
      // OSchemaShared.findOrCreateGlobalProperty's database-wide, monotonically increasing
      // counter: the position at which a (property name, OType) pair was first registered
      // ANYWHERE in the database's schema, not the position at which this particular class
      // declared it. Two classes that happen to declare a same-named, same-typed property share
      // one id; a property declared later on this class can therefore sort before one declared
      // earlier on this same class, if some other class registered that (name, type) pair first.
      // Still the right sort key: it is the only ordering signal this driver exposes, and it is
      // deterministic and reproducible across calls against the same schema.
      TreeMap<Integer, TabularColumn> byPosition = new TreeMap<>();

      // columnNamePattern is null, not "%": getColumns has a dedicated null fast path that adds
      // every column without running it through the LIKE-emulating OrientJdbcUtils.like at all --
      // there is no reason to pay for (or risk a metacharacter surprise from) a pattern match
      // against column names when every column of this class is wanted.
      try(ResultSet rs = meta.getColumns(database, null, datasetId, null)) {
         while(rs.next()) {
            // tableNamePattern above matched via OrientJdbcUtils.like's "_"->"." and case-
            // insensitive rewrite, which over-matches (see class javadoc) -- this exact,
            // case-sensitive check is what actually restricts the result to this one class.
            if(!datasetId.equals(rs.getString("TABLE_NAME"))) {
               continue;
            }

            String type = sqlTypes.convertToXType(rs.getInt("DATA_TYPE"));
            byPosition.put(rs.getInt("ORDINAL_POSITION"),
               new TabularColumn(rs.getString("COLUMN_NAME"), type != null ? type : XSchema.STRING));
         }
      }

      return new ArrayList<>(byPosition.values());
   }

   /**
    * {@code getPrimaryKeys} reports every unique index on a class, not a single "primary key" --
    * OrientDB has no such concept; records are identified by the system {@code @rid}. {@code
    * KEY_SEQ} restarts at 1 within EACH index, so collecting rows into one sequence-keyed map
    * (the way a real single-primary-key source would) would silently merge two unrelated unique
    * indexes' fields into one bogus key when a class happens to declare more than one. Rows are
    * therefore grouped by {@code PK_NAME} first; only when exactly one group exists does its
    * KEY_SEQ-ordered column list become {@code keyColumns} -- zero groups (no unique index) and
    * two-or-more groups (no single index this connector could call "the" key without guessing)
    * both report an empty list, which is honest rather than a silent pick of the first, largest,
    * or otherwise most plausible-looking index.
    *
    * Only called for a class already known to have a non-empty {@code getColumns} result: a
    * schema-less class can still hold a unique index on an undeclared runtime field, and {@link
    * inetsoft.web.wiz.service.TabularCatalogService}'s keyColumns validation requires every
    * reported key column to also appear in {@code columns} -- for a schema-less class {@code
    * columns} is only ever {@code @rid}, so any index-derived key name would fail that check and
    * abort the whole describeDataset call. Skipping the call entirely (not just discarding a
    * result that might contain such a name) avoids depending on this index happening not to exist.
    */
   private static List<String> primaryKeyColumnNames(DatabaseMetaData meta, String database,
                                                       String datasetId)
   {
      Map<String, TreeMap<Short, String>> byIndexName = new LinkedHashMap<>();

      try(ResultSet rs = meta.getPrimaryKeys(database, null, datasetId)) {
         while(rs.next()) {
            byIndexName.computeIfAbsent(rs.getString("PK_NAME"), k -> new TreeMap<>())
               .put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
         }
      }
      catch(Exception ex) {
         // Whether an unsupported-method response surfaces as SQLFeatureNotSupportedException
         // specifically or a generic SQLException is driver-dependent and unconfirmed here, so a
         // broad catch is deliberate -- mirrors AerospikeCatalog.primaryKeyColumnNames. Treated as
         // "this class declares no primary key" rather than propagated -- unlike getTables/
         // getColumns, whose failures are almost always a real connection/permission problem and
         // must propagate.
         return List.of();
      }

      if(byIndexName.size() != 1) {
         return List.of();
      }

      return new ArrayList<>(byIndexName.values().iterator().next().values());
   }

   /**
    * Wraps {@code identifier} in backticks, the quoting OrientDB's SQL grammar actually supports
    * for an identifier -- confirmed from {@code orientdb-core}'s parser grammar ({@code
    * OrientSqlConstants}'s {@code QUOTED_IDENTIFIER} token) and {@code OIdentifier}'s own
    * quoted-value handling, NOT from {@code DatabaseMetaData.getIdentifierQuoteString()} (which
    * returns a single space here -- the JDBC-spec convention for "this driver does not support
    * identifier quoting" -- describing this hand-written driver's own metadata self-report, not
    * what the SQL engine it fronts actually parses) and NOT by analogy to Hive (doubles
    * backticks) or Aerospike (doubles double quotes): OrientDB's escape for an embedded backtick
    * is a single backslash, per {@code OIdentifier}'s value round-trip.
    */
   private static String backtickQuote(String identifier) {
      return "`" + identifier.replace("`", "\\`") + "`";
   }
}
