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

import java.util.List;

/**
 * Response from POST /api/wiz/ws/table (batch).
 * {@code wsId} identifies the worksheet holding all created tables; {@code tables} carries one
 * per-table result (success and failure alike, in request order).
 */
public class WorksheetTablesResponse {
   /** Asset identifier of the worksheet holding the created tables; null when nothing was persisted. */
   private String wsId;

   /** One result per requested table, in request order. */
   private List<WorksheetTableResponse> tables;

   public String getWsId() { return wsId; }
   public void setWsId(String wsId) { this.wsId = wsId; }

   public List<WorksheetTableResponse> getTables() { return tables; }
   public void setTables(List<WorksheetTableResponse> tables) { this.tables = tables; }
}
