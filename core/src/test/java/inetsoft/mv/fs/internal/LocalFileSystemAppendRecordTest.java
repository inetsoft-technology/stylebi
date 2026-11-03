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
package inetsoft.mv.fs.internal;

import inetsoft.mv.data.SubMV;
import inetsoft.mv.fs.*;
import inetsoft.mv.util.SeekableInputStream;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.*;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #79869: {@link LocalFileSystem#appendRecord} lost blocks added in its new-block branch,
 * because it wrote its stale XFile copy back over the entry stored by add(). Only a map whose
 * get() returns copies (the replicated Ignite map) shows it, so this test uses one.
 */
@Tag("core")
class LocalFileSystemAppendRecordTest {
   @Test
   void newBlockOnlyIsKeptOnCopyingMap() throws Exception {
      // last block full: index -1 means "no block to append into", so a new block is added
      assertEquals(2, appendAndCountBlocks(true, 1, List.of(-1)));
   }

   @Test
   void mixedAppendIntoAndNewBlockKeepsBothOnCopyingMap() throws Exception {
      // sub-MV 0 is appended into block 0, sub-MV 1 becomes a new block
      assertEquals(2, appendAndCountBlocks(true, 1, List.of(0, -1)));
   }

   @Test
   void newBlockOnlyOnSharedInstanceMap() throws Exception {
      assertEquals(2, appendAndCountBlocks(false, 1, List.of(-1)));
   }

   @Test
   void appendIntoOnlyDoesNotAddBlocks() throws Exception {
      assertEquals(1, appendAndCountBlocks(true, 1, List.of(0)));
   }

   private int appendAndCountBlocks(boolean copying, int initialBlocks, List<Integer> indexes)
      throws Exception
   {
      DistributedMap<String, XFile> map = map(copying);
      Cluster cluster = mock(Cluster.class);
      when(cluster.getReplicatedMap(anyString())).thenReturn((DistributedMap) map);
      when(cluster.getLock(anyString())).thenReturn(mock(Lock.class));
      DistributedLong dl = mock(DistributedLong.class);
      when(dl.get()).thenReturn(1L);
      when(cluster.getLong(anyString())).thenReturn(dl);

      XBlockSystem bsys = mock(XBlockSystem.class);
      when(bsys.add(any(), any())).thenReturn(mock(NBlock.class));
      when(bsys.get(anyString())).thenReturn(mock(NBlock.class));
      BlockFile physical = bf();
      when(bsys.getFile(anyString())).thenReturn(physical);
      XDataNode dn = mock(XDataNode.class);
      when(dn.getBSystem()).thenReturn(bsys);
      FSConfig cfg = mock(FSConfig.class);
      XServerNode server = mock(XServerNode.class);
      when(server.getConfig()).thenReturn(cfg);

      try(MockedStatic<FSService> fs = mockStatic(FSService.class);
          MockedStatic<Cluster> cl = mockStatic(Cluster.class);
          MockedStatic<SreeEnv> env = mockStatic(SreeEnv.class);
          MockedStatic<DataSpace> ds = mockStatic(DataSpace.class);
          MockedStatic<SubMV> sub = mockStatic(SubMV.class))
      {
         ds.when(DataSpace::getDataSpace).thenReturn(mock(DataSpace.class));
         sub.when(() -> SubMV.get(any(BlockFile.class))).thenReturn(mock(SubMV.class));
         fs.when(FSService::getDataNode).thenReturn(dn);
         fs.when(() -> FSService.getDataNode(any())).thenReturn(dn);
         fs.when(FSService::getServer).thenReturn(server);
         cl.when(Cluster::getInstance).thenReturn(cluster);
         env.when(() -> SreeEnv.getProperty("mv.preferred.block")).thenReturn("1000");

         LocalFileSystem lfs = new LocalFileSystem(server);
         BlockFile[] initial = new BlockFile[initialBlocks];

         for(int i = 0; i < initial.length; i++) {
            initial[i] = bf();
         }

         lfs.add("mv1", initial);
         assertEquals(initialBlocks, lfs.get("mv1").getBlocks().size());

         List<BlockFile> files = new ArrayList<>();

         for(int i = 0; i < indexes.size(); i++) {
            files.add(bf());
         }

         assertTrue(lfs.appendRecord("mv1", files, new ArrayList<>(indexes)));
         XFile result = lfs.get("mv1");
         Set<String> ids = new HashSet<>();

         for(SBlock block : result.getBlocks()) {
            assertTrue(ids.add(block.getID()), "duplicate block " + block.getID());
         }

         return result.getBlocks().size();
      }
   }

   private static BlockFile bf() throws Exception {
      BlockFile f = mock(BlockFile.class);
      when(f.length()).thenReturn(10L);
      SeekableInputStream in = mock(SeekableInputStream.class);
      when(in.size()).thenReturn(0L);
      when(f.openInputStream()).thenReturn(in);
      return f;
   }

   private static XFile copy(XFile f) throws Exception {
      if(f == null) {
         return null;
      }

      ByteArrayOutputStream bo = new ByteArrayOutputStream();

      try(ObjectOutputStream o = new ObjectOutputStream(bo)) {
         o.writeObject(f);
      }

      try(ObjectInputStream i = new ObjectInputStream(new ByteArrayInputStream(bo.toByteArray()))) {
         return (XFile) i.readObject();
      }
   }

   @SuppressWarnings("unchecked")
   private static DistributedMap<String, XFile> map(boolean copying) throws Exception {
      Map<String, XFile> store = new HashMap<>();
      DistributedMap<String, XFile> m = mock(DistributedMap.class);
      when(m.get(any())).thenAnswer(
         a -> copying ? copy(store.get(a.getArgument(0))) : store.get(a.getArgument(0)));
      when(m.put(any(), any())).thenAnswer(a -> store.put(
         a.getArgument(0), copying ? copy(a.getArgument(1)) : a.getArgument(1)));
      when(m.replace(any(), any())).thenAnswer(a -> store.replace(
         a.getArgument(0), copying ? copy(a.getArgument(1)) : a.getArgument(1)));
      when(m.containsKey(any())).thenAnswer(a -> store.containsKey(a.getArgument(0)));
      when(m.remove(any())).thenAnswer(a -> store.remove(a.getArgument(0)));
      return m;
   }
}
