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

import inetsoft.uql.tabular.Property;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;

/**
 * Real (non-mock) query with two string properties that read back differently from what was
 * written: {@code variables} turns a blank value into {@code "{}"} (the shape of GraphQL's
 * {@code variables}), and {@code stuck} has a setter that drops every write.
 *
 * <p>{@code @View} is REQUIRED -- see {@link FakeNamedConnectorQuery}'s own doc for why.</p>
 */
@View(vertical = true, value = {
   @View1("variables"),
   @View1("stuck"),
})
public class FakeBlankNormalizingQuery extends TabularQuery {
   public FakeBlankNormalizingQuery() {
      super("FakeBlankNormalizing");
   }

   @Property(label = "Variables")
   public String getVariables() {
      return variables == null || variables.isEmpty() ? "{}" : variables;
   }

   public void setVariables(String variables) {
      this.variables = variables;
   }

   @Property(label = "Stuck")
   public String getStuck() {
      return stuck;
   }

   public void setStuck(String ignored) {
      // drops every write
   }

   /** Puts {@code stuck} at a value other than its default, bypassing the dropping setter. */
   public void presetStuck(String value) {
      this.stuck = value;
   }

   private String variables;
   private String stuck = "default-stuck";
}
