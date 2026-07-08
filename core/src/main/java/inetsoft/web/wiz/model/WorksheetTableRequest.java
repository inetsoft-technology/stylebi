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

import com.fasterxml.jackson.annotation.*;

import java.util.List;

/**
 * Request body for POST /api/wiz/ws/table.
 * <p>
 * Each call creates a batch of table assemblies and adds them to the worksheet
 * identified by {@code worksheetId}.  Omit {@code worksheetId} on the first
 * call; the response carries the newly assigned {@code wsId} which must be
 * supplied in every subsequent call for the same multi-step query.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorksheetTableRequest {
   /** Null → create a new worksheet. Non-null → add tables to an existing worksheet. */
   private String worksheetId;

   /** Tables to create in this request, in dependency order (physical first, mirror/join last). */
   private List<WorksheetTable> tables;

   public String getWorksheetId() { return worksheetId; }
   public void setWorksheetId(String worksheetId) { this.worksheetId = worksheetId; }

   public List<WorksheetTable> getTables() { return tables; }
   public void setTables(List<WorksheetTable> tables) { this.tables = tables; }
}
