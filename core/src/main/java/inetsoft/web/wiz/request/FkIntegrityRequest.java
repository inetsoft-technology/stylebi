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

package inetsoft.web.wiz.request;

/**
 * Identifies the foreign key whose join integrity is being probed.
 *
 * <p>Deliberately carries identifiers, never SQL: the statement is composed server-side from
 * these fields after each has been validated. See
 * {@link inetsoft.web.wiz.service.FkIntegrityService}.</p>
 */
public class FkIntegrityRequest {
   public String getDatasourcePath() {
      return datasourcePath;
   }

   public void setDatasourcePath(String datasourcePath) {
      this.datasourcePath = datasourcePath;
   }

   public String getSourceTable() {
      return sourceTable;
   }

   public void setSourceTable(String sourceTable) {
      this.sourceTable = sourceTable;
   }

   public String getFkColumn() {
      return fkColumn;
   }

   public void setFkColumn(String fkColumn) {
      this.fkColumn = fkColumn;
   }

   public String getTargetTable() {
      return targetTable;
   }

   public void setTargetTable(String targetTable) {
      this.targetTable = targetTable;
   }

   public String getTargetKeyColumn() {
      return targetKeyColumn;
   }

   public void setTargetKeyColumn(String targetKeyColumn) {
      this.targetKeyColumn = targetKeyColumn;
   }

   private String datasourcePath;
   private String sourceTable;
   private String fkColumn;
   private String targetTable;
   private String targetKeyColumn;
}
