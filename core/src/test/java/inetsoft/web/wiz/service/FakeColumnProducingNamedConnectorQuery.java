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
 * {@link FakeNamedConnectorQuery} reports NO output columns from {@code loadOutputColumns} --
 * by design, so tests of the empty-column refusal path don't need to fake a real response. That
 * makes it unsuitable, on its own, for a test that needs a query whose column set actually
 * CHANGES when its {@code endpoint} (or its lookup chain) changes -- the exact thing an
 * {@code edit_table} regression test (bug #76777) needs to prove: that rewriting an existing
 * {@link inetsoft.uql.asset.TabularTableAssembly}'s query in place actually produces the NEW
 * endpoint's columns, and that a rejected edit (e.g. the new endpoint returns nothing) leaves the
 * OLD columns/query completely untouched.
 *
 * <p>{@code @View} is re-declared (annotations on a class are not inherited) for the same reason
 * {@link FakeNamedConnectorQuery}'s own javadoc explains: {@code TabularSchemaExtractor.extract}
 * requires it, so anything reaching that path (the {@code queryParams}/{@code extraProperties}
 * forms) needs it even though this fixture is mainly exercised via the endpoint+lookup form.</p>
 */
@View(vertical = true, value = {
   @View1("endpoint"),
   @View1("parameters"),
   @View1("requestType"),
   @View1("suffix"),
   @View1("jsonPath"),
   @View1("expanded"),
   @View1("expandedPath"),
   @View1("lookupExpanded"),
   @View1("lookupTopLevelOnly"),
   @View1("lookupEndpoint0"),
   @View1("lookupEndpoint1"),
   @View1("additionalParameters"),
})
public class FakeColumnProducingNamedConnectorQuery extends FakeNamedConnectorQuery {
   /** Column names to report for each endpoint name, e.g. {@code Map.of("Repos", List.of("id", "name"))}. */
   public void setColumnsByEndpoint(Map<String, List<String>> columnsByEndpoint) {
      this.columnsByEndpoint = columnsByEndpoint;
   }

   @Override
   public void loadOutputColumns(VariableTable vtable) throws Exception {
      // Super's own bookkeeping (hint capture, reportResponseShape/reportException/
      // reportThrownException opt-ins) still applies; it just never sets any columns itself.
      super.loadOutputColumns(vtable);

      List<String> names = columnsByEndpoint == null
         ? List.of() : columnsByEndpoint.getOrDefault(getEndpoint(), List.of());
      XTypeNode[] cols = new XTypeNode[names.size()];

      for(int i = 0; i < names.size(); i++) {
         cols[i] = new XTypeNode(names.get(i));
      }

      setOutputColumns(cols);
   }

   private Map<String, List<String>> columnsByEndpoint;
}
