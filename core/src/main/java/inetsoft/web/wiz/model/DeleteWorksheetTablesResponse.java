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

import java.util.*;

/**
 * Response for DELETE /api/wiz/ws/table.
 */
public class DeleteWorksheetTablesResponse {

   /**
    * Updated worksheet identifier (same as the input wsId).
    */
   private String wsId;

   /**
    * Tables that were successfully removed.
    */
   private List<String> deleted = new ArrayList<>();

   /**
    * Requested table names that did not exist in the worksheet.
    */
   private List<String> notFound = new ArrayList<>();

   /**
    * Tables that were skipped because a retained assembly (not in the delete
    * request) still depends on them.  Key = table name; value = the name of
    * one dependent assembly that blocked deletion.
    */
   private Map<String, String> skipped = new LinkedHashMap<>();

   private boolean success;
   private String errorMessage;

   public String getWsId() {
      return wsId;
   }

   public void setWsId(String wsId) {
      this.wsId = wsId;
   }

   public List<String> getDeleted() {
      return deleted;
   }

   public void setDeleted(List<String> deleted) {
      this.deleted = deleted;
   }

   public List<String> getNotFound() {
      return notFound;
   }

   public void setNotFound(List<String> notFound) {
      this.notFound = notFound;
   }

   public Map<String, String> getSkipped() {
      return skipped;
   }

   public void setSkipped(Map<String, String> skipped) {
      this.skipped = skipped;
   }

   public boolean isSuccess() {
      return success;
   }

   public void setSuccess(boolean success) {
      this.success = success;
   }

   public String getErrorMessage() {
      return errorMessage;
   }

   public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
   }
}
