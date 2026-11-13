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
import inetsoft.mv.fs.*;
import inetsoft.mv.fs.internal.AbstractFileSystem;
import inetsoft.mv.fs.internal.LocalFileSystem;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedLong;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.storage.BlobStorage;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80096: after an organization id change the new org's distributed file system map can be
 * stale (it lacks the old MV names), so {@code fsys.rename} failed and the MVs lost their data.
 * {@code migrateMVStorage} now reloads the new org's file system from {@code <new>/fs.xml} once
 * when it finds a missing MV name, and fails loudly when names are still missing.
 */
@Tag("core")
class MVManagerMigrateStorageReloadTest {
   /** T1: stale map, fs.xml of the new org holds the old name -> reload makes the rename work. */
   @Test
   void staleMapIsReloadedFromFsXmlAndRenameSucceeds() throws Exception {
      Scenario s = new Scenario(Map.of(), List.of("mv1_orgA"), List.of("mv1_orgA"));
      s.run(false);

      assertEquals(1, s.fsXmlReads);
      assertTrue(s.map.containsKey("mv1_orgB"));
      assertFalse(s.map.containsKey("mv1_orgA"));
      verify(s.blobStorage).deleteBlobStorage();
   }

   /** T2: the entry is already present -> no reload. */
   @Test
   void presentEntryDoesNotReload() throws Exception {
      Scenario s = new Scenario(Map.of("mv1_orgA", new XFile("mv1_orgA", new ArrayList<>())),
                                List.of("mv1_orgA"), List.of());
      s.run(false);

      assertEquals(0, s.fsXmlReads);
      assertTrue(s.map.containsKey("mv1_orgB"));
   }

   /** T3: case-only id change, entries are present in the shared map -> kept, no reload. */
   @Test
   void caseOnlyRenameKeepsEntriesWithoutReload() throws Exception {
      Scenario s = new Scenario(Map.of("mv1_orga", new XFile("mv1_orga", new ArrayList<>())),
                                List.of("mv1_orga"), List.of(), "orga", "ORGA");
      s.run(false);

      assertEquals(0, s.fsXmlReads);
      assertTrue(s.map.containsKey("mv1_ORGA"));
   }

   /** T4: still missing after reload -> error names all missing names, old storage kept. */
   @Test
   void stillMissingAfterReloadThrowsListingAllNamesAndKeepsOldStorage() throws Exception {
      Scenario s = new Scenario(Map.of(), List.of("mv1_orgA", "mv2_orgA"), List.of());
      RuntimeException ex = assertThrows(RuntimeException.class, () -> s.run(false));

      assertTrue(ex.getMessage().contains("mv1_orgA"), ex.getMessage());
      assertTrue(ex.getMessage().contains("mv2_orgA"), ex.getMessage());
      verify(s.blobStorage, never()).deleteBlobStorage();
   }

   /** T5: reload happens before the refresh against the block system. */
   @Test
   void reloadHappensBeforeRefresh() throws Exception {
      LocalFileSystem fsys = mock(LocalFileSystem.class);
      when(fsys.get("mv1_orgA")).thenReturn(null, new XFile("mv1_orgA", new ArrayList<>()));
      Scenario s = new Scenario(Map.of(), List.of("mv1_orgA"), List.of());
      s.fsOverride = fsys;
      s.run(false);

      InOrder order = inOrder(fsys);
      order.verify(fsys).refresh(any(), eq(true));
      order.verify(fsys).reloadFromDataSpace();
      order.verify(fsys).refresh(any(), eq(true));
   }

   /** T6: several missing names -> exactly one reload. */
   @Test
   void severalMissingNamesReloadOnce() throws Exception {
      Scenario s = new Scenario(Map.of(), List.of("mv1_orgA", "mv2_orgA", "mv3_orgA"),
                                List.of("mv1_orgA", "mv2_orgA", "mv3_orgA"));
      s.run(false);

      assertEquals(1, s.fsXmlReads);
      assertTrue(s.map.containsKey("mv3_orgB"));
   }

   /** Missing fs.xml -> a named error, not an NPE. */
   @Test
   void missingFsXmlThrowsNamedError() throws Exception {
      Scenario s = new Scenario(Map.of(), List.of("mv1_orgA"), List.of());
      s.fsXmlPresent = false;
      RuntimeException ex = assertThrows(RuntimeException.class, () -> s.run(false));

      assertTrue(ex.getMessage().contains("orgB"), ex.getMessage());
      assertFalse(ex instanceof NullPointerException);
      verify(s.blobStorage, never()).deleteBlobStorage();
   }

   private static final class Scenario {
      final Map<String, XFile> map = new HashMap<>();
      final List<String> oldNames;
      final List<String> fsXmlNames;
      final String oldId;
      final String newId;
      int fsXmlReads;
      boolean fsXmlPresent = true;
      XFileSystem fsOverride;
      @SuppressWarnings("unchecked")
      final BlobStorage<MVStorage.Metadata> blobStorage = mock(BlobStorage.class);

      Scenario(Map<String, XFile> initial, List<String> oldNames, List<String> fsXmlNames) {
         this(initial, oldNames, fsXmlNames, "orgA", "orgB");
      }

      Scenario(Map<String, XFile> initial, List<String> oldNames, List<String> fsXmlNames,
               String oldId, String newId)
      {
         map.putAll(initial);
         this.oldNames = oldNames;
         this.fsXmlNames = fsXmlNames;
         this.oldId = oldId;
         this.newId = newId;
      }

      @SuppressWarnings({"unchecked", "rawtypes"})
      void run(boolean copy) throws Exception {
         DistributedMap<String, XFile> dmap = mock(DistributedMap.class);
         when(dmap.get(any())).thenAnswer(a -> map.get(a.getArgument(0)));
         when(dmap.put(any(), any())).thenAnswer(a -> map.put(a.getArgument(0), a.getArgument(1)));
         when(dmap.containsKey(any())).thenAnswer(a -> map.containsKey(a.getArgument(0)));
         when(dmap.remove(any())).thenAnswer(a -> map.remove(a.getArgument(0)));
         when(dmap.values()).thenAnswer(a -> new ArrayList<>(map.values()));
         when(dmap.keySet()).thenAnswer(a -> new HashSet<>(map.keySet()));
         doAnswer(a -> { map.clear(); return null; }).when(dmap).clear();
         doAnswer(a -> { map.putAll(a.getArgument(0)); return null; }).when(dmap).putAll(any());

         Cluster cluster = mock(Cluster.class);
         when(cluster.getReplicatedMap(anyString())).thenReturn((DistributedMap) dmap);
         when(cluster.getLock(anyString())).thenReturn(mock(Lock.class));
         DistributedLong dl = mock(DistributedLong.class);
         when(dl.get()).thenReturn(1L);
         when(cluster.getLong(anyString())).thenReturn(dl);

         XBlockSystem bsys = mock(XBlockSystem.class);
         when(bsys.list(anyBoolean())).thenReturn(new NBlock[0]);
         XDataNode dn = mock(XDataNode.class);
         when(dn.getBSystem()).thenReturn(bsys);
         FSConfig cfg = mock(FSConfig.class);
         XServerNode server = mock(XServerNode.class);
         when(server.getConfig()).thenReturn(cfg);

         DataSpace space = mock(DataSpace.class);
         String fsXml = "<FileSystem><Files>" + fsXmlEntries() + "</Files></FileSystem>";
         when(space.getInputStream(any(), anyString())).thenAnswer(a -> {
            fsXmlReads++;
            return fsXmlPresent
               ? new ByteArrayInputStream(fsXml.getBytes(StandardCharsets.UTF_8)) : null;
         });

         MVStorage mvStorage = mock(MVStorage.class);
         List<String> files = new ArrayList<>();
         when(mvStorage.getStorage(anyString())).thenReturn(blobStorage);

         try(MockedStatic<Cluster> cl = mockStatic(Cluster.class);
             MockedStatic<FSService> fs = mockStatic(FSService.class);
             MockedStatic<SreeEnv> env = mockStatic(SreeEnv.class);
             MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
             MockedStatic<MVStorage> mvs = mockStatic(MVStorage.class);
             MockedStatic<AssetEntry> ae = mockStatic(AssetEntry.class))
         {
            cl.when(Cluster::getInstance).thenReturn(cluster);
            ds.when(DataSpace::getDataSpace).thenReturn(space);
            env.when(() -> SreeEnv.getEarlyLoadedProperty("fs.files")).thenReturn("fs.xml");
            mvs.when(MVStorage::getInstance).thenReturn(mvStorage);
            fs.when(FSService::getDataNode).thenReturn(dn);
            fs.when(() -> FSService.getDataNode(any())).thenReturn(dn);
            fs.when(() -> FSService.getServer(anyString())).thenReturn(server);
            AssetEntry entry = mock(AssetEntry.class);
            when(entry.cloneAssetEntry(any(Organization.class))).thenReturn(entry);
            when(entry.toIdentifier(true)).thenReturn("vs");
            ae.when(() -> AssetEntry.createAssetEntry(anyString())).thenReturn(entry);

            XFileSystem fsys = fsOverride != null ? fsOverride : new LocalFileSystem(server, newId);
            when(server.getFSystem()).thenReturn(fsys);

            for(String oldName : oldNames) {
               String file = "f_" + oldName;
               files.add(file);
               String newName = oldName.substring(0, oldName.lastIndexOf('_') + 1) + newId;
               MVDef def = mock(MVDef.class);
               String[] current = { oldName };
               when(def.getName()).thenAnswer(a -> current[0]);
               doAnswer(a -> { current[0] = newName; return null; })
                  .when(def).setMVName(anyString());
               when(def.getMVName()).thenReturn(oldName);
               when(def.getVsId()).thenReturn("vs");
               MV mv = mock(MV.class);
               when(mv.getDef()).thenReturn(def);
               MV orig = mock(MV.class);
               when(orig.clone(oldId)).thenReturn(mv);
               when(mvStorage.get(file, oldId)).thenReturn(orig);
            }

            when(mvStorage.listFiles(oldId)).thenReturn(files);
            MVManager manager = mock(MVManager.class, CALLS_REAL_METHODS);
            Field f = MVManager.class.getDeclaredField("cluster");
            f.setAccessible(true);
            f.set(manager, cluster);
            java.lang.reflect.Method m = MVManager.class.getDeclaredMethod(
               "migrateMVStorage", Organization.class, Organization.class, boolean.class);
            m.setAccessible(true);

            try {
               m.invoke(manager, new Organization(oldId), new Organization(newId), copy);
            }
            catch(java.lang.reflect.InvocationTargetException e) {
               if(e.getCause() instanceof Exception) {
                  throw (Exception) e.getCause();
               }

               throw e;
            }
         }
      }

      private String fsXmlEntries() {
         StringBuilder sb = new StringBuilder();

         for(String name : fsXmlNames) {
            StringWriter w = new StringWriter();
            PrintWriter pw = new PrintWriter(w);
            new XFile(name, new ArrayList<>()).writeXML(pw);
            pw.flush();
            sb.append(w);
         }

         return sb.toString();
      }
   }
}
