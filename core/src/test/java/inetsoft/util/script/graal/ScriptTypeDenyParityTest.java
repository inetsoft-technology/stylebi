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
package inetsoft.util.script.graal;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78064: {@link ScriptHostAccess#isTypeDenied} must agree with the type denies
 * Graal enforces for {@link ScriptHostAccess#hostAccess()}. Reads Graal's
 * package-private state by reflection, in this test only, so a GraalVM upgrade
 * that changes it fails here rather than at run time.
 */
@Tag("core")
class ScriptTypeDenyParityTest {
   @Test
   void everyDenyRegisteredWithGraalIsRecorded() throws Exception {
      Field field = HostAccess.class.getDeclaredField("excludeTypes");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      EconomicMap<Class<?>, Boolean> graal =
         (EconomicMap<Class<?>, Boolean>) field.get(ScriptHostAccess.hostAccess());
      Map<Class<?>, Boolean> expected = new HashMap<>();
      MapCursor<Class<?>, Boolean> cursor = graal.getEntries();

      while(cursor.advance()) {
         expected.put(cursor.getKey(), cursor.getValue());
      }

      assertFalse(expected.isEmpty());
      assertEquals(expected, new HashMap<>(ScriptHostAccess.deniedTypes()));
   }

   @Test
   void isTypeDeniedMatchesGraalForDeclaredMembers() throws Exception {
      Method allows = HostAccess.class.getDeclaredMethod("allowsAccess", AnnotatedElement.class);
      allows.setAccessible(true);
      HostAccess hostAccess = ScriptHostAccess.hostAccess();
      Set<Class<?>> classes = new LinkedHashSet<>(ScriptHostAccess.deniedTypes().keySet());
      // denied through an interface, a superclass, Principal, and by exact type
      classes.add(inetsoft.uql.jdbc.DefaultConnectionPoolFactory.class);
      classes.add(inetsoft.uql.jdbc.JDBCDataSource.class);
      classes.add(inetsoft.sree.security.DestinationUserNameProviderPrincipal.class);
      classes.add(inetsoft.uql.viewsheet.BookmarkLockManager.class);
      classes.add(Object.class);
      // subtypes of the exact-type denies, and classes that are not denied
      classes.add(inetsoft.report.internal.Util.class);
      classes.add(inetsoft.graph.mxgraph.io.mxCellCodec.class);
      classes.add(inetsoft.graph.mxgraph.model.mxCell.class);
      classes.add(inetsoft.graph.mxgraph.model.mxGeometry.class);
      classes.add(inetsoft.graph.EGraph.class);
      classes.add(inetsoft.uql.XFormatInfo.class);
      classes.add(ArrayList.class);
      classes.add(Hashtable.class);
      List<String> mismatched = new ArrayList<>();
      int checked = 0;

      for(Class<?> type : classes) {
         AnnotatedElement member = declaredMember(type);

         if(member == null) {
            continue;
         }

         checked++;
         boolean denied = !(boolean) allows.invoke(hostAccess, member);

         if(denied != ScriptHostAccess.isTypeDenied(type)) {
            mismatched.add(type.getName() + " (Graal denies: " + denied + ")");
         }
      }

      assertTrue(checked > 90, "expected every recorded type, checked " + checked);
      assertEquals(List.of(), mismatched);
   }

   @Test
   void exactTypeDeniesDoNotCoverSubtypes() {
      assertTrue(ScriptHostAccess.isTypeDenied(Object.class));
      assertFalse(ScriptHostAccess.isTypeDenied(ArrayList.class));
      assertTrue(ScriptHostAccess.isTypeDenied(inetsoft.report.StyleConstants.class));
      assertFalse(ScriptHostAccess.isTypeDenied(inetsoft.report.internal.Util.class));
      assertTrue(ScriptHostAccess.isTypeDenied(inetsoft.uql.jdbc.JDBCDataSource.class));
   }

   /** A member the class declares itself: a constructor, else a method, else a field. */
   private static AnnotatedElement declaredMember(Class<?> type) {
      if(type.getDeclaredConstructors().length > 0) {
         return type.getDeclaredConstructors()[0];
      }

      if(type.getDeclaredMethods().length > 0) {
         return type.getDeclaredMethods()[0];
      }

      return type.getDeclaredFields().length > 0 ? type.getDeclaredFields()[0] : null;
   }
}
