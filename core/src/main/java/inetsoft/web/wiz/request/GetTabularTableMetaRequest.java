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

package inetsoft.web.wiz.request;

public class GetTabularTableMetaRequest {
   public String getDsName() {
      return dsName;
   }

   public void setDsName(String dsName) {
      this.dsName = dsName;
   }

   /** The value to set on the connector's {@code AnnotatableQuery} target property, e.g. an OData entity name or a SAP table name. */
   public String getTarget() {
      return target;
   }

   public void setTarget(String target) {
      this.target = target;
   }

   private String dsName;
   private String target;
}
