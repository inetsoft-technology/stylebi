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
package inetsoft.mv;

import inetsoft.mv.MVDef.MVContainer;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.TabularTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.asset.internal.TabularTableAssemblyInfo;
import inetsoft.uql.tabular.TabularQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Bug #78643: an MV dispatched to a remote scheduler node must not carry a worksheet holding
 * classes that only a data source plugin class loader can resolve (the receiving node's
 * marshaller loader cannot). The receiver is simulated by an ObjectInputStream that refuses
 * to resolve the plugin-only class, as Ignite does on a node without access to the plugin
 * class loader.
 */
@Tag("core")
class MVDefDispatchPluginClassTest {
   @Test
   void liveWorksheetWithPluginClassCannotBeUnmarshalled() throws Exception {
      MVDef mv = newMV(true);

      // documents the failure mode the fix avoids
      assertThrows(ClassNotFoundException.class, () -> roundTrip(mv));
   }

   @Test
   void registeredMVDropsWorksheetSoPluginClassIsNotMarshalled() throws Exception {
      MVDef mv = newMV(true);
      MVManager manager = mock(MVManager.class);
      when(manager.containsMV(mv)).thenReturn(true);

      try(MockedStatic<MVManager> managerStatic = mockStatic(MVManager.class)) {
         managerStatic.when(MVManager::getManager).thenReturn(manager);
         MVDef copy = (MVDef) mv.clone();
         copy.detachWorksheetForDispatch();

         MVDef received = roundTrip(copy);
         assertNull(received.container.ws);
         // the sender's definition keeps its worksheet
         assertNotNull(mv.container.ws);
      }
   }

   @Test
   void unregisteredMVKeepsWorksheet() throws Exception {
      MVDef mv = newMV(false);
      MVManager manager = mock(MVManager.class);
      when(manager.containsMV(mv)).thenReturn(false);

      try(MockedStatic<MVManager> managerStatic = mockStatic(MVManager.class)) {
         managerStatic.when(MVManager::getManager).thenReturn(manager);
         mv.detachWorksheetForDispatch();
         assertNotNull(mv.container.ws);
      }
   }

   private static MVDef newMV(boolean withPluginQuery) throws Exception {
      Worksheet ws;

      try(MockedStatic<SreeEnv> sreeEnvStatic = mockStatic(SreeEnv.class)) {
         sreeEnvStatic.when(() -> SreeEnv.getProperty(anyString())).thenReturn("");
         ws = new Worksheet();
         TabularTableAssembly table = new TabularTableAssembly(ws, "Query1");

         if(withPluginQuery) {
            ((TabularTableAssemblyInfo) table.getInfo()).setQuery(new PluginOnlyQuery());
         }

         ws.addAssembly(table);
      }

      MVDef mv = new MVDef();
      Field field = MVDef.class.getDeclaredField("mvname");
      field.setAccessible(true);
      field.set(mv, "test-mv");
      mv.container = new MVContainer(Collections.emptyList(), ws);
      return mv;
   }

   private static MVDef roundTrip(MVDef mv) throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(mv);
      }

      try(ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
         @Override
         protected Class<?> resolveClass(ObjectStreamClass desc)
            throws IOException, ClassNotFoundException
         {
            if(desc.getName().equals(PluginOnlyQuery.class.getName())) {
               throw new ClassNotFoundException(desc.getName());
            }

            return super.resolveClass(desc);
         }
      })
      {
         return (MVDef) in.readObject();
      }
   }

   /** Stands in for a plugin-provided query such as RestJsonQuery. */
   public static class PluginOnlyQuery extends TabularQuery {
      public PluginOnlyQuery() {
         super("plugin-only");
      }
   }
}
