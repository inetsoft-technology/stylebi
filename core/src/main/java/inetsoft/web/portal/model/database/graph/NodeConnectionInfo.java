/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.model.database.graph;

import inetsoft.web.portal.model.database.JoinModel;

import java.util.Objects;

public class NodeConnectionInfo {

   public NodeConnectionInfo() {
   }

   public NodeConnectionInfo(String id) {
      this.id = id;
   }

   public NodeConnectionInfo(String id, JoinModel joinModel) {
      this.id = id;
      this.joinModel = joinModel;
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public JoinModel getJoinModel() {
      return joinModel;
   }

   public void setJoinModel(JoinModel joinModel) {
      this.joinModel = joinModel;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      }

      if (o == null || getClass() != o.getClass()) {
         return false;
      }

      NodeConnectionInfo that = (NodeConnectionInfo) o;

      return id.equals(that.id) &&
         joinModel.equals(that.joinModel);
   }

   @Override
   public int hashCode() {
      return Objects.hash(id, joinModel);
   }

   private String id;
   private JoinModel joinModel;
}
