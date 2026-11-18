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

import inetsoft.mv.data.MV;
import inetsoft.mv.data.MVStorage;
import inetsoft.mv.fs.FSService;
import inetsoft.mv.fs.XServerNode;
import inetsoft.mv.local.LocalMVIncremental;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79854: the time stored as the last update time of an incremental MV (the lower bound of
 * the next update's {@code MV.LastUpdateTime}) is the time {@code update0} starts reading its
 * source, which it also passes to the data cache as the query time.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LocalMVIncrementalUpdateStartTest {
   @Test
   void updateRecordsTheStartOfTheReadBeforeTheSourceReturns() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T");
      table.setMVForceAppendUpdates(true);
      ws.addAssembly(table);
      MVDef def = new MVDef();
      def.container = new MVDef.MVContainer(new ArrayList<>(), ws);
      set(def, "tname", "T");
      set(def, "mvname", "host-org-t79854");

      MV mv = Mockito.mock(MV.class);
      Mockito.when(mv.getDef()).thenReturn(Mockito.mock(MVDef.class));
      MVStorage storage = Mockito.mock(MVStorage.class);
      Mockito.when(storage.get(Mockito.anyString())).thenReturn(mv);
      XServerNode server = Mockito.mock(XServerNode.class);
      long[] readAt = { 0L };
      Object[] cacheTs = { null };

      try(MockedStatic<MVStorage> storageStatics = Mockito.mockStatic(MVStorage.class);
          MockedStatic<FSService> fsStatics = Mockito.mockStatic(FSService.class);
          MockedStatic<AssetDataCache> cacheStatics = Mockito.mockStatic(AssetDataCache.class))
      {
         storageStatics.when(() -> MVStorage.getFile(Mockito.any())).thenReturn("f");
         storageStatics.when(MVStorage::getInstance).thenReturn(storage);
         fsStatics.when(FSService::getServer).thenReturn(server);
         fsStatics.when(FSService::getConfig).thenReturn(Mockito.mock(inetsoft.mv.fs.FSConfig.class));

         // the real getData() runs; the data cache stands in for a source that fails once the
         // read has started
         AssetDataCache cache = Mockito.mock(AssetDataCache.class);
         Mockito.when(cache.getData(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                                    Mockito.anyInt(), Mockito.anyBoolean(), Mockito.anyLong(),
                                    Mockito.any()))
            .thenAnswer(inv -> {
               readAt[0] = System.currentTimeMillis();
               cacheTs[0] = inv.getArgument(6);
               throw new IllegalStateException("stop after the read started");
            });
         cacheStatics.when(AssetDataCache::getCache).thenReturn(cache);
         LocalMVIncremental incremental = new LocalMVIncremental(def);

         long before = System.currentTimeMillis();
         Method update0 = LocalMVIncremental.class.getDeclaredMethod("update0");
         update0.setAccessible(true);
         Exception ex = assertThrows(InvocationTargetException.class,
                                     () -> update0.invoke(incremental));
         assertInstanceOf(IllegalStateException.class, ex.getCause());

         assertTrue(readAt[0] > 0, "the source was not read");
         assertTrue(incremental.updateStart >= before,
                    "the start was not recorded by update0: " + incremental.updateStart);
         assertTrue(incremental.updateStart <= readAt[0], "the start must not be after the read");
         assertEquals(incremental.updateStart, cacheTs[0]);
      }
   }

   private static void set(Object target, String name, Object value) throws Exception {
      Field field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }
}
