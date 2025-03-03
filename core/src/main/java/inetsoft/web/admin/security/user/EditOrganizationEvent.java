/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.admin.security.user;

public class EditOrganizationEvent {
   public static int NONE = -1;
   public static int STARTED = 1;
   public static int FINSHED = 2;

   public EditOrganizationEvent(int status, String fromOrgID, String toOrgID) {
      super();
      this.status = status;
      this.fromOrgID = fromOrgID;
      this.toOrgID = toOrgID;
   }

   public static int getNONE() {
      return NONE;
   }

   public static void setNONE(int NONE) {
      EditOrganizationEvent.NONE = NONE;
   }

   public int getStatus() {
      return status;
   }

   public void setStatus(int status) {
      this.status = status;
   }

   public String getToOrgID() {
      return toOrgID;
   }

   public void setToOrgID(String toOrgID) {
      this.toOrgID = toOrgID;
   }

   public String getFromOrgID() {
      return fromOrgID;
   }

   public void setFromOrgID(String fromOrgID) {
      this.fromOrgID = fromOrgID;
   }

   private int status = NONE;
   private String toOrgID;
   private String fromOrgID;
}
