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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes one column in the primary worksheet table assembly.
 * <p>
 * name    – the underlying DB column name (used to match against FieldInfo.name)
 * alias   – the column identifier as StyleBI knows it in the worksheet;
 * null when the alias equals the DB column name
 * type    – the column data type (XSchema type, e.g. "string", "integer", "date");
 * used by the visualization layer to classify dimension vs measure
 * table   – DB table name (used to match against FieldInfo.table)
 * schema  – DB schema (may be empty)
 * catalog – DB catalog (may be empty)
 * path    – datasource path (used to match against FieldInfo.source.path)
 * <p>
 * Immutable: this type ends up inside the {@code @Serial.Structural} {@link WorksheetModel}, which is
 * eligible for cluster-level caching, so it carries no setters. The {@code @JsonCreator} constructor
 * keeps it deserializable on the inbound side.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorksheetColumnInfo {
   @JsonCreator
   public WorksheetColumnInfo(@JsonProperty("name") String name,
                              @JsonProperty("alias") String alias,
                              @JsonProperty("type") String type,
                              @JsonProperty("table") String table,
                              @JsonProperty("schema") String schema,
                              @JsonProperty("catalog") String catalog,
                              @JsonProperty("path") String path,
                              @JsonProperty("description") String description)
   {
      this.name = name;
      this.alias = alias;
      this.type = type;
      this.table = table;
      this.schema = schema;
      this.catalog = catalog;
      this.path = path;
      this.description = description;
   }

   public String getName() {
      return name;
   }

   public String getAlias() {
      return alias;
   }

   public String getType() {
      return type;
   }

   public String getTable() {
      return table;
   }

   public String getSchema() {
      return schema;
   }

   public String getCatalog() {
      return catalog;
   }

   public String getPath() {
      return path;
   }

   public String getDescription() {
      return description;
   }

   private final String name;
   private final String alias;
   private final String type;
   private final String table;
   private final String schema;
   private final String catalog;
   private final String path;
   private final String description;
}
