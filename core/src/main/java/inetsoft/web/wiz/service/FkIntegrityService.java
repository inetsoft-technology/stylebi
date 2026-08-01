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

package inetsoft.web.wiz.service;

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.JDBCHandler;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.request.FkIntegrityRequest;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

/**
 * Counts the rows an INNER join from a fact table to a foreign-key target would silently drop.
 *
 * <p>wiz-services wants to replace a range slider over a surrogate key (e.g. {@code partner_id})
 * with a filter listing the target's labels, which requires injecting an INNER join to the FK
 * target. That join drops every row whose FK is NULL or orphaned, which would change every
 * unfiltered aggregate on the dashboard. So the join is only injected when this count is zero.</p>
 *
 * <p>Two consequences shape this class:</p>
 * <ul>
 *   <li><b>A wrong zero is the worst failure.</b> Nothing here degrades into a count: no
 *       exception is swallowed, no missing result defaults to zero. Failures propagate to the
 *       controller's {@code @ExceptionHandler}, and wiz-services treats any non-200 as a
 *       rejection — the fail-closed outcome.</li>
 *   <li><b>Identifier validation is the security boundary.</b> This is deliberately not a
 *       {@code {sql}}-accepting endpoint; accepting SQL would create an authenticated
 *       arbitrary-SQL surface over every registered datasource to serve one caller with one
 *       fixed query shape. Identifiers come in, the statement is composed here, and anything
 *       outside the allowed character set is rejected rather than quoted or escaped.</li>
 * </ul>
 */
@Service
public class FkIntegrityService {
   public FkIntegrityService(MetadataApiService metadataService,
                             DataSourceService dataSourceService)
   {
      this.metadataService = metadataService;
      this.dataSourceService = dataSourceService;
   }

   /**
    * Counts the rows an INNER join from {@code sourceTable} to {@code targetTable} would drop.
    *
    * @param request   the source/target tables and key columns, as identifiers.
    * @param principal the requesting user; must hold READ on the datasource.
    * @return rows whose FK is NULL plus rows whose FK has no matching target row.
    *
    * @throws IllegalArgumentException if any identifier fails validation — nothing is executed.
    * @throws SecurityException        if the user lacks READ on the datasource.
    * @throws SQLException             if the query fails or returns no usable count. Never
    *                                  translated into a count.
    */
   public long countDroppedRows(FkIntegrityRequest request, Principal principal) throws Exception {
      if(request == null) {
         throw new IllegalArgumentException("A request body is required.");
      }

      String dsName = request.getDatasourcePath();

      if(dsName == null || dsName.isEmpty()) {
         throw new IllegalArgumentException("datasourcePath is required.");
      }

      // Validate before anything else: a rejected identifier must never reach a connection.
      String sourceTable = validateTableIdentifier(request.getSourceTable(), "sourceTable");
      String fkColumn = validateColumnIdentifier(request.getFkColumn(), "fkColumn");
      String targetTable = validateTableIdentifier(request.getTargetTable(), "targetTable");
      String targetKeyColumn =
         validateColumnIdentifier(request.getTargetKeyColumn(), "targetKeyColumn");

      if(!dataSourceService.checkPermission(dsName, ResourceAction.READ, principal)) {
         throw new SecurityException("Access denied to data source: " + dsName);
      }

      JDBCDataSource ds = metadataService.getJDBCDatasource(dsName);
      String sql = buildDroppedRowCountSql(sourceTable, fkColumn, targetTable, targetKeyColumn);

      try(Connection conn = openConnection(ds, principal)) {
         return executeCount(conn, sql);
      }
   }

   /**
    * The single connection-acquisition seam, mirroring MetadataApiService. Package-private and
    * overridable so the surrounding logic is testable without a live database.
    */
   Connection openConnection(JDBCDataSource ds, Principal principal) throws Exception {
      return new JDBCHandler().getConnection(ds, principal);
   }

   /**
    * Runs the count. An absent row or a NULL count is an error, not a zero: {@code getLong}
    * returns 0 for SQL NULL, and 0 is precisely the answer that opens the gate.
    */
   private static long executeCount(Connection conn, String sql) throws SQLException {
      try(PreparedStatement stmt = conn.prepareStatement(sql);
          ResultSet rs = stmt.executeQuery())
      {
         if(!rs.next()) {
            throw new SQLException(
               "Foreign key integrity count returned no row; refusing to report zero dropped rows.");
         }

         long count = rs.getLong(1);

         if(rs.wasNull()) {
            throw new SQLException(
               "Foreign key integrity count was NULL; refusing to report zero dropped rows.");
         }

         return count;
      }
   }

   /**
    * Builds the counting statement from already-validated identifiers.
    *
    * <p>Both halves of "rows an INNER join would drop" are counted: NULL foreign keys, and
    * foreign keys with no matching target row. The orphan half is a correlated subquery, so the
    * FK value is compared column-to-column and never rendered into the statement text.</p>
    */
   static String buildDroppedRowCountSql(String sourceTable, String fkColumn,
                                         String targetTable, String targetKeyColumn)
   {
      return "SELECT COUNT(*) FROM " + sourceTable + " src" +
         " WHERE src." + fkColumn + " IS NULL" +
         " OR NOT EXISTS (SELECT 1 FROM " + targetTable + " tgt" +
         " WHERE tgt." + targetKeyColumn + " = src." + fkColumn + ")";
   }

   /**
    * Validates a table name: at most two dot-separated segments (schema.table).
    */
   static String validateTableIdentifier(String identifier, String field) {
      return validateIdentifier(identifier, field, 2);
   }

   /**
    * Validates a column name: exactly one segment. A dot in a column name is a rejection, not a
    * qualification — the statement qualifies columns itself via its {@code src}/{@code tgt}
    * aliases.
    */
   static String validateColumnIdentifier(String identifier, String field) {
      return validateIdentifier(identifier, field, 1);
   }

   private static String validateIdentifier(String identifier, String field, int maxSegments) {
      if(identifier == null || identifier.isEmpty()) {
         throw new IllegalArgumentException(field + " is required.");
      }

      // A -1 limit keeps trailing empty segments, so "sale_order." splits to ["sale_order", ""]
      // and is rejected instead of slipping through with a dangling dot.
      String[] segments = identifier.split("\\.", -1);

      if(segments.length > maxSegments) {
         throw new IllegalArgumentException(
            field + " must have at most " + maxSegments + " name segment" +
            (maxSegments == 1 ? "" : "s") + ": '" + identifier + "'.");
      }

      for(String segment : segments) {
         if(!IDENTIFIER.matcher(segment).matches()) {
            throw new IllegalArgumentException(
               field + " is not a valid identifier: '" + identifier + "'. Each name segment must " +
               "match " + IDENTIFIER.pattern() + ".");
         }
      }

      return identifier;
   }

   /**
    * The whole security boundary of this feature. Anything outside this set is refused; no
    * attempt is made to quote or escape a rejected name into safety.
    */
   private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

   private final MetadataApiService metadataService;
   private final DataSourceService dataSourceService;
}
