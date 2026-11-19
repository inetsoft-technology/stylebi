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
package inetsoft.report.script.viewsheet;

import inetsoft.graph.EGraph;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The scriptable behind {@code EGraph} in the script editor: it only offers names for completion. Every name it offers
 * must exist on {@code EGraph}, a method for {@code name()} and a bean property for the others.
 */
@Tag("core")
class VSEGraphScriptableTest {
   @Test
   void itOffersNamesOnly() {
      VSEGraphScriptable s = new VSEGraphScriptable();

      assertNull(s.getMember("addElement()"));
      assertFalse(s.hasMember("addElement()"));

      s.putMember("addElement()", "x");
      assertNull(s.getMember("addElement()"));
   }

   @Test
   void theOfferedNamesAreTheDocumentedOnes() {
      Set<Object> keys = new HashSet<>(Arrays.asList(new VSEGraphScriptable().getMemberKeys()));

      assertEquals(34, keys.size());
      assertTrue(keys.containsAll(List.of("addElement()", "getCoordinate()", "setScale()", "coordinate",
                                          "legendLayout", "XTitleSpec")));
   }

   @Test
   void everyOfferedNameExistsOnEGraph() {
      Set<String> methods = new HashSet<>();

      for(Method m : EGraph.class.getMethods()) {
         methods.add(m.getName());
      }

      List<String> missing = new ArrayList<>();

      for(Object key : new VSEGraphScriptable().getMemberKeys()) {
         String id = (String) key;

         if(id.endsWith("()")) {
            if(!methods.contains(id.substring(0, id.length() - 2))) {
               missing.add(id);
            }
         }
         else {
            String property = Character.toUpperCase(id.charAt(0)) + id.substring(1);

            if(!methods.contains("get" + property) && !methods.contains("set" + property) &&
               !methods.contains("is" + property))
            {
               missing.add(id);
            }
         }
      }

      assertEquals(List.of(), missing);
   }
}
