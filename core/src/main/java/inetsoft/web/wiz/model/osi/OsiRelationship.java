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

package inetsoft.web.wiz.model.osi;

import com.fasterxml.jackson.annotation.*;

import java.util.List;

/**
 * Foreign key relationship between datasets (Relationship in osi-schema.json).
 * {@code name}, {@code from}, {@code to}, {@code from_columns}, and {@code to_columns} are required.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OsiRelationship {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getFrom() {
      return from;
   }

   public void setFrom(String from) {
      this.from = from;
   }

   public String getTo() {
      return to;
   }

   public void setTo(String to) {
      this.to = to;
   }

   public List<String> getFromColumns() {
      return fromColumns;
   }

   public void setFromColumns(List<String> fromColumns) {
      this.fromColumns = fromColumns;
   }

   public List<String> getToColumns() {
      return toColumns;
   }

   public void setToColumns(List<String> toColumns) {
      this.toColumns = toColumns;
   }

   public List<OsiCustomExtension> getCustomExtensions() {
      return customExtensions;
   }

   public void setCustomExtensions(List<OsiCustomExtension> customExtensions) {
      this.customExtensions = customExtensions;
   }

   private String name;
   private String from;
   private String to;

   @JsonProperty("from_columns")
   private List<String> fromColumns;

   @JsonProperty("to_columns")
   private List<String> toColumns;

   @JsonProperty("custom_extensions")
   private List<OsiCustomExtension> customExtensions;
}
