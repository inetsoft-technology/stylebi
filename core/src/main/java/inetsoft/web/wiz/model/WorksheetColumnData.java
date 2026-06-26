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

package inetsoft.web.wiz.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One column of a worksheet table, mirroring the TypeScript {@code TableColumn} shape.
 * <ul>
 *   <li>{@code name}        – the column identifier StyleBI exposes (alias or DB column name).</li>
 *   <li>{@code alias}       – the explicitly-set alias when it differs from {@code name}; null otherwise.</li>
 *   <li>{@code description} – optional column description.</li>
 *   <li>{@code type}        – column data type (XSchema type, e.g. "string", "integer", "date").</li>
 * </ul>
 * {@code alias} and {@code description} are omitted from the JSON when null (NON_NULL).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorksheetColumnData {
   public WorksheetColumnData() {
   }

   public WorksheetColumnData(String name, String type) {
      this.name = name;
      this.type = type;
   }

   public WorksheetColumnData(String name, String alias, String description, String type) {
      this.name = name;
      this.alias = alias;
      this.description = description;
      this.type = type;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getAlias() {
      return alias;
   }

   public void setAlias(String alias) {
      this.alias = alias;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public String getType() {
      return type;
   }

   public void setType(String type) {
      this.type = type;
   }

   private String name;
   private String alias;
   private String description;
   private String type;
}
