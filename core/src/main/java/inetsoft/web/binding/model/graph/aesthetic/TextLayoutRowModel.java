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

import inetsoft.uql.viewsheet.graph.TextLayoutRow;
import java.util.List;
import java.util.stream.Collectors;

public class TextLayoutRowModel {
   private List<TextLayoutItemModel> items;
   private String horizontalAlign;  // "left" | "center" | "right"; null = default left

   public List<TextLayoutItemModel> getItems() { return items; }
   public void setItems(List<TextLayoutItemModel> items) { this.items = items; }

   public String getHorizontalAlign() { return horizontalAlign; }
   public void setHorizontalAlign(String horizontalAlign) { this.horizontalAlign = horizontalAlign; }

   public static TextLayoutRowModel fromDomain(TextLayoutRow row) {
      TextLayoutRowModel model = new TextLayoutRowModel();
      model.items = row.getItems().stream()
         .map(TextLayoutItemModel::fromDomain)
         .collect(Collectors.toList());
      String align = row.getAlignment();
      if(!"left".equals(align)) {
         model.horizontalAlign = align;
      }
      return model;
   }

   public TextLayoutRow toDomain() {
      TextLayoutRow row = new TextLayoutRow();
      if(items != null) {
         items.stream()
            .map(TextLayoutItemModel::toDomain)
            .forEach(row::addItem);
      }
      if(horizontalAlign != null) {
         row.setAlignment(horizontalAlign);
      }
      return row;
   }
}
