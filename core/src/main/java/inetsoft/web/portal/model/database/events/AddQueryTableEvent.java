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
package inetsoft.web.portal.model.database.events;

import inetsoft.uql.asset.AssetEntry;

import java.awt.*;
import java.util.List;

public class AddQueryTableEvent {
   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public List<AssetEntry> getTables() {
      return tables;
   }

   public void setTables(List<AssetEntry> tables) {
      this.tables = tables;
   }

   public Point getPosition() {
      return position;
   }

   public void setPosition(Point position) {
      this.position = position;
   }

   private String id;
   private List<AssetEntry> tables;
   private Point position;
}
