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
package inetsoft.web.wiz.service;

import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;

import java.util.List;
import java.util.Map;

/**
 * {@link FakeCustomRestQuery} inherits {@link inetsoft.uql.tabular.TabularQuery}'s REAL
 * {@code loadOutputColumns}, which drives an actual {@code TabularHandler} execution -- not
 * usable in a unit test. This subclass overrides it to report columns keyed by the current
 * {@code suffix}, the generic/custom-connector analogue of
 * {@link FakeColumnProducingNamedConnectorQuery} (which does the same thing keyed by
 * {@code endpoint} for a named connector), so an {@code edit_table} test can exercise the suffix
 * form's success path without a live connector.
 *
 * <p>{@code @View} is re-declared for the same reason
 * {@link FakeColumnProducingNamedConnectorQuery}'s own javadoc explains.</p>
 */
@View(vertical = true, value = {
   @View1("suffix"),
   @View1("jsonPath"),
   @View1("paginationMode"),
   @View1("lookupUrl0"),
   @View1("lookupJsonPath0"),
   @View1("lookupKey0"),
   @View1("lookupIgnoreBaseUrl0"),
   @View1("lookupUrl1"),
   @View1("lookupJsonPath1"),
   @View1("lookupKey1"),
   @View1("lookupIgnoreBaseUrl1"),
})
public class FakeColumnProducingCustomRestQuery extends FakeCustomRestQuery {
   /** Column names to report for each suffix, e.g. {@code Map.of("/widgets", List.of("id"))}. */
   public void setColumnsBySuffix(Map<String, List<String>> columnsBySuffix) {
      this.columnsBySuffix = columnsBySuffix;
   }

   @Override
   public void loadOutputColumns(VariableTable vtable) {
      List<String> names = columnsBySuffix == null
         ? List.of() : columnsBySuffix.getOrDefault(getSuffix(), List.of());
      XTypeNode[] cols = new XTypeNode[names.size()];

      for(int i = 0; i < names.size(); i++) {
         cols[i] = new XTypeNode(names.get(i));
      }

      setOutputColumns(cols);
   }

   /**
    * Overridden as a no-op for the same reason {@link FakeNamedConnectorQuery#revalidate()} is:
    * the real {@code XQuery.revalidate()} looks up {@code DataSourceRegistry.getRegistry()}, a
    * Spring-bean-backed singleton this fixture's test context does not provide. Unlike
    * {@link FakeNamedConnectorQuery}, {@link FakeCustomRestQuery} does not override this itself,
    * so {@code TabularTableAssembly.loadColumnSelection}'s own {@code query.revalidate()} call
    * would otherwise throw {@code NoSuchBeanDefinitionException} before ever reaching
    * {@link #loadOutputColumns}.
    */
   @Override
   public void revalidate() {
   }

   private Map<String, List<String>> columnsBySuffix;
}
