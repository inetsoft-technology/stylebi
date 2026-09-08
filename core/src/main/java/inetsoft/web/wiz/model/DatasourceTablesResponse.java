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

package inetsoft.web.wiz.model;

import inetsoft.web.wiz.model.osi.OsiRelationship;

import java.util.List;

public class DatasourceTablesResponse {
   public List<DatabaseTableInfo> getTables() {
      return tables;
   }

   public void setTables(List<DatabaseTableInfo> tables) {
      this.tables = tables;
   }

   public List<OsiRelationship> getRelationships() {
      return relationships;
   }

   public void setRelationships(List<OsiRelationship> relationships) {
      this.relationships = relationships;
   }

   /**
    * The cursor to pass back to fetch the next page of a paged {@code GET /datasource/tables}
    * request. {@code null} means either this response is not paged (no {@code limit} was
    * requested) or it is the last page of one that was.
    */
   public String getNextCursor() {
      return nextCursor;
   }

   public void setNextCursor(String nextCursor) {
      this.nextCursor = nextCursor;
   }

   private List<DatabaseTableInfo> tables;
   private List<OsiRelationship> relationships;
   private String nextCursor;
}
