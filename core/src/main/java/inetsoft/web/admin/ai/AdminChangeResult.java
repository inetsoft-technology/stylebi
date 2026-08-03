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
package inetsoft.web.admin.ai;

public class AdminChangeResult {
   public AdminChangeResult() {}
   public String getProperty() { return property; }
   public void setProperty(String v) { this.property = v; }
   public String getBeforeValue() { return beforeValue; }
   public void setBeforeValue(String v) { this.beforeValue = v; }
   public String getAfterValue() { return afterValue; }
   public void setAfterValue(String v) { this.afterValue = v; }
   public String getStatus() { return status; }
   public void setStatus(String v) { this.status = v; }
   public String getError() { return error; }
   public void setError(String v) { this.error = v; }

   /**
    * True once the pre-change snapshot read inside {@code AdminChangeService.applyChange}
    * completed successfully.
    *
    * <p>{@code beforeValue == null} cannot, by itself, distinguish "the property was genuinely
    * unset" from "the snapshot read failed" - both leave {@code beforeValue} null. Only the
    * former is safe to undo by writing {@code null} (reset-to-default); undoing the latter would
    * remove a property that was never touched. {@code AdminChangesetApplyService} must check this
    * flag, not just compare before/after, before treating a change as "moved" and eligible for
    * rollback.
    */
   public boolean isBeforeRead() { return beforeRead; }
   public void setBeforeRead(boolean v) { this.beforeRead = v; }

   private String property, beforeValue, afterValue, status, error;
   private boolean beforeRead;
}
