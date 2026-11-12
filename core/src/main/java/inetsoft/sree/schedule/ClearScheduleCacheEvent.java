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

package inetsoft.sree.schedule;

import java.io.Serializable;

/**
 * Cluster message sent when an organization is deleted or its id is renamed away, so that every
 * node drops the in-memory schedule and data cycle task caches it holds for the organization.
 */
public class ClearScheduleCacheEvent implements Serializable {
   public ClearScheduleCacheEvent(String orgID) {
      this.orgID = orgID;
   }

   public String getOrgID() {
      return orgID;
   }

   private final String orgID;
}
