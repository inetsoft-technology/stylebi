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

package inetsoft.report;

import inetsoft.sree.security.OrganizationManager;

public class PropertyChangeEvent extends java.beans.PropertyChangeEvent {
   public PropertyChangeEvent(Object source, String propertyName, Object oldValue, Object newValue) {
      super(source, propertyName, oldValue, newValue);
      this.orgID = OrganizationManager.getInstance().getCurrentOrgID();
   }

   public PropertyChangeEvent(Object source, String propertyName, Object oldValue, Object newValue, String orgID) {
      super(source, propertyName, oldValue, newValue);
      this.orgID = orgID;
   }

   public String getOrgID() {
      return orgID;
   }

   public void setOrgID(String orgID) {
      this.orgID = orgID;
   }

   private String orgID;
}
