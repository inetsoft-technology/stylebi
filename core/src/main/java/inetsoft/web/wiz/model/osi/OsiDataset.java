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
 * Logical dataset representing a physical database table (Dataset in osi-schema.json).
 * {@code name} and {@code source} are required.
 * Database-specific metadata (dsName, catalog, schema) is placed in {@code custom_extensions}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OsiDataset {
   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getSource() {
      return source;
   }

   public void setSource(String source) {
      this.source = source;
   }

   public List<String> getPrimaryKey() {
      return primaryKey;
   }

   public void setPrimaryKey(List<String> primaryKey) {
      this.primaryKey = primaryKey;
   }

   public List<List<String>> getUniqueKeys() {
      return uniqueKeys;
   }

   public void setUniqueKeys(List<List<String>> uniqueKeys) {
      this.uniqueKeys = uniqueKeys;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public Object getAiContext() {
      return aiContext;
   }

   public void setAiContext(Object aiContext) {
      this.aiContext = aiContext;
   }

   public List<OsiField> getFields() {
      return fields;
   }

   public void setFields(List<OsiField> fields) {
      this.fields = fields;
   }

   public List<OsiCustomExtension> getCustomExtensions() {
      return customExtensions;
   }

   public void setCustomExtensions(List<OsiCustomExtension> customExtensions) {
      this.customExtensions = customExtensions;
   }

   private String name;
   private String source;

   @JsonProperty("primary_key")
   private List<String> primaryKey;

   @JsonProperty("unique_keys")
   private List<List<String>> uniqueKeys;

   private String description;

   @JsonProperty("ai_context")
   private Object aiContext;

   private List<OsiField> fields;

   @JsonProperty("custom_extensions")
   private List<OsiCustomExtension> customExtensions;
}
