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
package inetsoft.mv.data;

import inetsoft.mv.fs.BlockFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link SubMV#dispose()} removes its entry from the shared, static
 * {@code SubMV.map} keyed by the backing block file's name -- previously this cleanup was
 * missing, leaking an entry (and the {@code SubMV} it points to) every time a sub-MV block was
 * disposed instead of garbage collected.
 */
@Tag("core")
class SubMVDisposeTest {
   private static final String FILE_NAME = "SubMVDisposeTest-block.smv";

   @AfterEach
   void cleanUp() throws Exception {
      mapField().remove(FILE_NAME);
   }

   @Test
   void removesItsEntryFromTheSharedMapOnDispose() throws Exception {
      DefaultTableBlock table = mock(DefaultTableBlock.class);
      SubMV subMV = new SubMV(new XDimIndex[0], table);

      BlockFile file = mock(BlockFile.class);
      when(file.getName()).thenReturn(FILE_NAME);
      setFileField(subMV, file);
      mapField().put(FILE_NAME, subMV);

      subMV.dispose();

      assertFalse(mapField().containsKey(FILE_NAME));
      verify(table).dispose();
   }

   @SuppressWarnings("unchecked")
   private static ConcurrentMap<String, SubMV> mapField() throws Exception {
      Field field = SubMV.class.getDeclaredField("map");
      field.setAccessible(true);
      return (ConcurrentMap<String, SubMV>) field.get(null);
   }

   private static void setFileField(SubMV subMV, BlockFile file) throws Exception {
      Field field = SubMV.class.getDeclaredField("file");
      field.setAccessible(true);
      field.set(subMV, file);
   }
}
