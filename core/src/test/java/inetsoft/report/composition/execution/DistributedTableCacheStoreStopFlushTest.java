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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.DistributedTableCacheStore.Metadata;
import inetsoft.report.filter.ColumnMapFilter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.*;
import inetsoft.test.*;
import inetsoft.util.script.graal.ScriptStopTestSupport;
import inetsoft.util.script.graal.ScriptStopTestSupport.Stops;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77949: a cached table whose formula a script timeout stopped is not written to the
 * distributed table cache, where another node would read its stopped cell as a null value.
 * The flush skips that entry as failed and still writes the other entries.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DistributedTableCacheStoreStopFlushTest {
   @Test
   @SuppressWarnings("unchecked")
   void flushSkipsAStoppedTableAndWritesTheOthers() throws Exception {
      TableLens stopped = new ColumnMapFilter(stoppedLens(), new int[] { 0, 1, 2 });
      assertTrue(AssetDataCache.isStopped(stopped));
      TableLens good = new DefaultTableLens(new Object[][] { { "a" }, { 1 } });

      // the stopped table first: the flush goes on after it
      Map<DataKey, TableLens> entries = new LinkedHashMap<>();
      entries.put(key("stopped"), stopped);
      entries.put(key("good"), good);
      AssetDataCache cache = mock(AssetDataCache.class);
      when(cache.getLocalEntries()).thenReturn(entries);
      ObjectProvider<AssetDataCache> provider = mock(ObjectProvider.class);
      when(provider.getObject()).thenReturn(cache);

      Map<String, ByteArrayOutputStream> streams = new LinkedHashMap<>();
      List<String> committed = new ArrayList<>();
      BlobStorage<Metadata> storage = mock(BlobStorage.class);
      when(storage.exists(anyString())).thenReturn(false);
      when(storage.beginTransaction()).thenAnswer(inv -> {
         BlobTransaction<Metadata> tx = mock(BlobTransaction.class);
         String[] path = new String[1];
         when(tx.newStream(anyString(), any())).thenAnswer(s -> {
            path[0] = s.getArgument(0);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            streams.put(path[0], out);
            return out;
         });
         doAnswer(c -> committed.add(path[0])).when(tx).commit();
         return tx;
      });
      BlobStorageManager manager = mock(BlobStorageManager.class);
      when(manager.<Metadata>getStorage(anyString(), anyBoolean())).thenReturn(storage);

      Cluster cluster = mock(Cluster.class, Mockito.RETURNS_DEEP_STUBS);
      when(cluster.getId()).thenReturn("c1");
      when(cluster.getServerClusterNodes()).thenReturn(Set.of("n1", "n2"));

      DistributedTableCacheStore store = new DistributedTableCacheStore(cluster, manager, provider);
      store.flushLocalCache();

      assertEquals(2, streams.size(), "the flush tried both entries: " + streams.keySet());
      assertEquals(1, committed.size(), "only the table without a stop is written");
      assertEquals(new ArrayList<>(streams.keySet()).get(1), committed.get(0),
                   "the written entry is the one without a stop");
   }

   /**
    * A formula table whose row 2 was stopped (an injected stopped script exception).
    */
   private static FormulaTableLens stoppedLens() throws Exception {
      Stops stops = new Stops();
      ScriptStopTestSupport.StoppingEnv env = new ScriptStopTestSupport.StoppingEnv(stops);
      env.init();
      String marker = "/*flush77949*/";
      stops.reset(marker, n -> n == 2, true);
      DefaultTableLens base = new DefaultTableLens(
         new Object[][] { { "key", "value" }, { "a", 1 }, { "b", 2 }, { "c", 3 } });
      FormulaTableLens lens = new FormulaTableLens(
         base, new String[] { "f" }, new String[] { "field['value'] * 10" + marker }, env, null);
      Throwable stop = assertThrows(Throwable.class, () -> {
         for(int r = 0; lens.moreRows(r); r++) {
            lens.getObject(r, 2);
         }
      });
      assertTrue(ScriptTimeoutGuard.isStop(stop));
      assertTrue(lens.isStopped());
      return lens;
   }

   private static DataKey key(String value) {
      DataKey key = mock(DataKey.class);
      when(key.getValue()).thenReturn(value);
      when(key.isLocalCacheOnly()).thenReturn(false);
      return key;
   }
}
