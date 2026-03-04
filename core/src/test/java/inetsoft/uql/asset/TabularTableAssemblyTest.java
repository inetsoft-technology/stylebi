/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.asset;

import inetsoft.uql.VariableTable;
import inetsoft.web.composer.model.ws.DependencyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for null-safety of TabularTableAssembly when the backing query is null
 * (e.g. the data source connector plugin is missing).
 */
class TabularTableAssemblyTest {
   private Worksheet ws;
   private TabularTableAssembly assembly;

   @BeforeEach
   void setUp() {
      ws = new Worksheet();
      assembly = new TabularTableAssembly(ws, "test");
      ws.addAssembly(assembly);
      // query is null by default — simulates missing connector plugin after deserialization
   }

   @Test
   void getDependeds_doesNotThrowWhenQueryIsNull() {
      Set<AssemblyRef> refs = new HashSet<>();
      assertDoesNotThrow(() -> assembly.getDependeds(refs));
      // Empty because: no condition assemblies, no expression columns, getInputScript()
      // returns null (query is null), and there are no other assemblies in the worksheet.
      assertTrue(refs.isEmpty());
   }

   @Test
   void getAugmentedDependeds_doesNotThrowWhenQueryIsNull() {
      Map<String, Set<DependencyType>> dependeds = new HashMap<>();
      assertDoesNotThrow(() -> assembly.getAugmentedDependeds(dependeds));
      // Empty for the same reasons as getDependeds_doesNotThrowWhenQueryIsNull above.
      assertTrue(dependeds.isEmpty());
   }

   @Test
   void replaceVariables_doesNotThrowWhenQueryIsNull() {
      assertDoesNotThrow(() -> assembly.replaceVariables(new VariableTable()));
   }

   @Test
   void dependencyChanged_doesNotThrowWhenQueryIsNull() {
      assertDoesNotThrow(() -> assembly.dependencyChanged("someTable"));
   }

   @Test
   void loadColumnSelection_doesNotThrowWhenQueryIsNull() {
      assertDoesNotThrow(() -> assembly.loadColumnSelection(new VariableTable(), true, null));
   }

   @Test
   void printKey_returnsFalseWhenQueryIsNull() throws Exception {
      PrintWriter writer = new PrintWriter(new StringWriter());
      boolean result = assembly.printKey(writer);
      assertFalse(result);
   }
}
