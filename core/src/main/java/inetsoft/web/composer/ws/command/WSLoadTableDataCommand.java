/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.composer.ws.command;

import inetsoft.web.composer.model.ws.WSTableData;
import inetsoft.web.viewsheet.command.ViewsheetCommand;

public class WSLoadTableDataCommand implements ViewsheetCommand {
   public WSTableData getTableData() {
      return tableData;
   }

   public void setTableData(WSTableData tableData) {
      this.tableData = tableData;
   }

   public boolean isContinuation() {
      return continuation;
   }

   public void setContinuation(boolean continuation) {
      this.continuation = continuation;
   }

   public int getRequestId() {
      return requestId;
   }

   public void setRequestId(int requestId) {
      this.requestId = requestId;
   }

   WSTableData tableData;
   boolean continuation;
   int requestId;
}
