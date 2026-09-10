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
package inetsoft.uql.tabular;

import java.util.ArrayList;
import java.util.List;

/**
 * A read-only, redacted view of one tabular data source's own connection-level configuration --
 * the bean state on the {@link inetsoft.uql.XDataSource} instance itself (base URL, host, tenant,
 * whatever else the connector declares), as opposed to {@link TabularQuerySchema}, which describes
 * the QUERY built against it (what to fetch, not what to connect to).
 *
 * <p>Deliberately the datasource-level counterpart of {@code TabularQuerySchema}: same
 * {@code @Property} reflection ({@link TabularUtil#findProperties}), same label resolution
 * ({@link PropertyMeta#getDisplayLabel()}), same {@code password} flag off the same {@link Property}
 * annotation every connector already declares its credential fields with -- nothing connector-
 * specific here, which is what lets one implementation cover every {@code TabularDataSource}
 * subclass (REST, REST.XML, METADATA, FILE families alike) with no per-connector code.</p>
 *
 * <p>A {@link Field} marked {@link Field#isPassword()} never carries its actual value -- see
 * {@link Field#isConfigured()} for what a caller gets instead: whether a secret is set, without
 * seeing what it is set to. This is the deliberate difference from {@code /tabular/definition}
 * (see {@code DataSourceDefinition}'s own javadoc), which returns secrets in clear text and is
 * gated on WRITE for exactly that reason -- this type exists so a READ-gated caller (composer-chat's
 * own agent session) can still learn "is this source pointed at what I expect, does it have SOME
 * credential configured" without ever being handed the credential itself.</p>
 */
public class TabularDataSourceConfig {
   public String getDataSourceType() {
      return dataSourceType;
   }

   public void setDataSourceType(String dataSourceType) {
      this.dataSourceType = dataSourceType;
   }

   public List<Field> getFields() {
      return fields;
   }

   public void setFields(List<Field> fields) {
      this.fields = fields;
   }

   private String dataSourceType;
   private List<Field> fields = new ArrayList<>();

   /**
    * One connection-level property on the data source bean.
    */
   public static class Field {
      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }

      public String getLabel() {
         return label;
      }

      public void setLabel(String label) {
         this.label = label;
      }

      /**
       * The current value, as {@code String.valueOf}. {@code null} whenever {@link #isPassword()}
       * is {@code true} -- see {@link #isConfigured()} for that case.
       */
      public String getValue() {
         return value;
      }

      public void setValue(String value) {
         this.value = value;
      }

      public boolean isPassword() {
         return password;
      }

      public void setPassword(boolean password) {
         this.password = password;
      }

      /**
       * Only meaningful when {@link #isPassword()} is {@code true}: whether SOME value is set,
       * without revealing it. {@code false} otherwise (a non-secret field's presence is already
       * answered by {@link #getValue()} being non-null).
       */
      public boolean isConfigured() {
         return configured;
      }

      public void setConfigured(boolean configured) {
         this.configured = configured;
      }

      private String name;
      private String label;
      private String value;
      private boolean password;
      private boolean configured;
   }
}
