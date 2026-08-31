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
import inetsoft.uql.tabular.PropertyEditor;
import inetsoft.uql.tabular.TabularQuery;
import inetsoft.uql.tabular.View;
import inetsoft.uql.tabular.View1;

/**
 * A purpose-built fixture whose two properties' {@code @PropertyEditor(dependsOn=...)} mutually
 * reference each other -- a connector bug this codebase's own annotations should never declare
 * for real, but the topological sort (capability 2) must detect and refuse loudly rather than
 * hang or silently drop one side.
 *
 * <p>{@code @View} is REQUIRED -- see {@link FakeNamedConnectorQuery}'s own doc for why.</p>
 */
@View(vertical = true, value = { @View1("a"), @View1("b") })
public class FakeCyclicQuery extends TabularQuery {
   public FakeCyclicQuery() {
      super("FakeCyclic");
   }

   @Property(label = "A")
   @PropertyEditor(dependsOn = "b")
   public String getA() {
      return a;
   }

   public void setA(String a) {
      this.a = a;
   }

   @Property(label = "B")
   @PropertyEditor(dependsOn = "a")
   public String getB() {
      return b;
   }

   public void setB(String b) {
      this.b = b;
   }

   private String a;
   private String b;
}
