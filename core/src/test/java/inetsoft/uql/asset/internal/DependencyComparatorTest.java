/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.asset.internal;

import inetsoft.uql.asset.*;
import inetsoft.util.algo.Comparators;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

class DependencyComparatorTest {
   @Test
   void transitivityContract() {
      final Worksheet ws = new Worksheet();

      final EmbeddedTableAssembly A = new EmbeddedTableAssembly(ws, "table 2");
      final MirrorTableAssembly A_B = new MirrorTableAssembly(ws, "table 5", A);

      final EmbeddedTableAssembly childless = new EmbeddedTableAssembly(ws, "table 3");

      final EmbeddedTableAssembly X = new EmbeddedTableAssembly(ws, "table 4");
      final MirrorTableAssembly X_1 = new MirrorTableAssembly(ws, "table 1", X);
      final MirrorTableAssembly X_2 = new MirrorTableAssembly(ws, "table 8", X);
      final MirrorTableAssembly X_1_1 = new MirrorTableAssembly(ws, "table 6", X_1);
      final MirrorTableAssembly X_1_2 = new MirrorTableAssembly(ws, "table 7", X_1);

      ws.addAssembly(A);
      ws.addAssembly(A_B);

      ws.addAssembly(childless);

      ws.addAssembly(X);
      ws.addAssembly(X_1);
      ws.addAssembly(X_2);
      ws.addAssembly(X_1_1);
      ws.addAssembly(X_1_2);

      final List<AbstractTableAssembly> list =
         Arrays.asList(A, A_B, childless, X, X_1, X_2, X_1_1, X_1_2);

      Comparators.verifyTransitivity(new DependencyComparator(ws), list);
      Comparators.verifyTransitivity(new DependencyComparator(ws, true), list);
   }
}