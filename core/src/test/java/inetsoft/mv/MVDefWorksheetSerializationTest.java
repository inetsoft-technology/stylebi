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
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.Worksheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@code MVDef.container} (no longer {@code transient} as of 1b6ae84f) behaves as
 * intended across serialization: {@link MVDef#persistAndClearWorksheet()} must clear the
 * worksheet reference before the def is serialized to other cluster nodes, and the current
 * (unmodified) behavior of serializing without calling it first is pinned so any future change
 * is caught rather than silently drifting.
 */
@Tag("core")
class MVDefWorksheetSerializationTest {
   @Test
   void persistAndClearWorksheetClearsTheWorksheetBeforeSerialization() throws Exception {
      Worksheet ws = newWorksheet();
      MVDef mv = new MVDef();
      setMvName(mv, "test-mv");
      mv.container = new MVContainer(Collections.emptyList(), ws);

      MVWorksheetStorage storage = mock(MVWorksheetStorage.class);
      OrganizationManager orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("host-org");

      try(MockedStatic<MVWorksheetStorage> storageStatic = mockStatic(MVWorksheetStorage.class);
          MockedStatic<OrganizationManager> orgManagerStatic =
             mockStatic(OrganizationManager.class))
      {
         storageStatic.when(MVWorksheetStorage::getInstance).thenReturn(storage);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

         mv.persistAndClearWorksheet();

         verify(storage).putWorksheet(anyString(), same(ws), eq("host-org"));
      }

      assertNull(mv.container.ws, "worksheet reference should be cleared in memory immediately");

      MVDef roundTripped = (MVDef) deserialize(serialize(mv));

      assertNotNull(roundTripped.container, "container itself should still serialize");
      assertNull(roundTripped.container.ws,
                 "cleared worksheet reference must stay cleared across serialization");
   }

   /**
    * Pins the current (unmodified) behavior when a caller forgets to call
    * {@code persistAndClearWorksheet()} before serializing: it succeeds and the worksheet
    * survives the round trip. If a production code path is ever found to rely on serializing
    * without persisting first, that should be escalated as a bug, not just re-pinned here.
    */
   @Test
   void serializingWithoutPersistingKeepsTheWorksheetAttached() throws Exception {
      Worksheet ws = newWorksheet();
      MVDef mv = new MVDef();
      setMvName(mv, "test-mv");
      mv.container = new MVContainer(Collections.emptyList(), ws);

      MVDef roundTripped = (MVDef) deserialize(serialize(mv));

      assertNotNull(roundTripped.container);
      assertNotNull(roundTripped.container.ws,
                    "current behavior: the worksheet is still present when not persisted first");
   }

   private static Worksheet newWorksheet() {
      // Worksheet()'s constructor builds a WorksheetInfo, which reads
      // "asset.sample.maxrows" via SreeEnv -- stub it out so construction doesn't need a
      // running Spring application context.
      try(MockedStatic<SreeEnv> sreeEnvStatic = mockStatic(SreeEnv.class)) {
         sreeEnvStatic.when(() -> SreeEnv.getProperty(anyString())).thenReturn("");
         return new Worksheet();
      }
   }

   private static void setMvName(MVDef mv, String name) throws Exception {
      Field field = MVDef.class.getDeclaredField("mvname");
      field.setAccessible(true);
      field.set(mv, name);
   }

   private static byte[] serialize(Object o) throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(o);
      }

      return bytes.toByteArray();
   }

   private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
      try(ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
         return in.readObject();
      }
   }
}
