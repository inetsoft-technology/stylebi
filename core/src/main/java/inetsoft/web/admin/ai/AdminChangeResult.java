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

   private String property, beforeValue, afterValue, status, error;
}
