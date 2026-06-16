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
package inetsoft.web.binding.model.graph.aesthetic;

import inetsoft.uql.viewsheet.graph.TextLayout;

import java.util.List;

public class TextLayoutModel {
   private List<TextLayoutRowModel> rows;

   public List<TextLayoutRowModel> getRows() { return rows; }
   public void setRows(List<TextLayoutRowModel> rows) { this.rows = rows; }

   public static TextLayoutModel fromDomain(TextLayout layout) {
      if(layout == null) return null;
      TextLayoutModel model = new TextLayoutModel();
      if(layout.getRows() != null) {
         model.rows = layout.getRows().stream()
            .map(TextLayoutRowModel::fromDomain)
            .collect(java.util.stream.Collectors.toList());
      }
      return model;
   }

   public TextLayout toDomain() {
      TextLayout layout = new TextLayout();
      if(rows != null) {
         rows.stream().map(TextLayoutRowModel::toDomain).forEach(layout::addRow);
      }
      return layout;
   }
}
