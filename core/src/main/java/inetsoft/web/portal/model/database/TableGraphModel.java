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
package inetsoft.web.portal.model.database;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.awt.*;
import java.util.List;

@JsonIgnoreProperties
public class TableGraphModel {

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public Rectangle getBounds() {
      return bounds;
   }

   public void setBounds(Rectangle bounds) {
      this.bounds = bounds;
   }

   public List<GraphColumnInfo> getColumns() {
      return columns;
   }

   public void setColumns(List<GraphColumnInfo> columns) {
      this.columns = columns;
   }

   public List<JoinModel> getJoins() {
      return joins;
   }

   public void setJoins(List<JoinModel> joins) {
      this.joins = joins;
   }

   private String name;
   private Rectangle bounds;
   private List<GraphColumnInfo> columns;
   private List<JoinModel> joins;
}
