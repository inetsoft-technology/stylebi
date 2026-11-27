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
import inetsoft.mv.data.*;
import inetsoft.mv.fs.*;
import inetsoft.mv.fs.internal.CacheBlockFile;
import inetsoft.mv.local.LocalMVIncremental;
import inetsoft.mv.util.SeekableInputStream;
import inetsoft.mv.util.TransactionChannel;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82473: an incremental append into the partly filled last block of an MV shifted the stored
 * values of an integer dimension column whose block holds a NULL (9 read back as 10, ..., 14 as
 * 13). {@code XDimDictionary.mergeFrom} raised the old dictionary's {@code range[0]}, and
 * {@code LocalMVIncremental.initMappings} did not remap the top index of an integer dictionary
 * with a NULL slot.
 *
 * <p>The update runs the real path: {@code MVBuilder} with the old MV, the real (private)
 * {@code LocalMVIncremental.initMappings}, then {@code SubMV.updateData} and
 * {@code SubMV.appendBlock} on the block file, as {@code LocalFileSystem.updateBlock} and
 * {@code appendRecord} do. Only the block id lookup ({@code FSService}, {@code MVStorage}) is
 * mocked.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LocalMVIncrementalIntNullAppendTest {
   private static final String[] HEADERS = { "n3", "n2" };
   private String oldPreferred;
   private final List<BlockFile> files = new ArrayList<>();
   private BlockFile lastFile;

   @BeforeEach
   void setUp() {
      oldPreferred = SreeEnv.getProperty("mv.preferred.block");
      SreeEnv.setProperty("mv.preferred.block", "10");
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("mv.preferred.block", oldPreferred);

      for(BlockFile file : files) {
         SubMV.removeMap(file);
         file.delete();
      }
   }

   @Test
   void consecutiveAppendsKeepTheStoredValuesOfTheLastBlock() throws Exception {
      MVDef def = newDef();
      List<Object[]> src = new ArrayList<>();

      for(int i = 0; i < 10; i++) {
         src.add(new Object[] { 20 + i, 30 + i });
      }

      // last block (5 rows): n3 holds a NULL, n2 has none yet
      Object[][] last = { { 9, 9 }, { 10, 50 }, { null, 99 }, { 12, 20 }, { 14, 40 } };
      src.addAll(Arrays.asList(last));
      List<BlockFile> blocks = new ArrayList<>();
      MV mv = build(def, null, src.toArray(new Object[0][]), blocks);
      assertEquals(2, mv.getBlockSize());
      lastFile = blocks.get(1);
      List<Object[]> rows = new ArrayList<>(Arrays.asList(last));
      assertBlock("base", mv, lastFile, rows);

      Object[][][] appends = {
         { { 11, 60 }, { 13, 70 } }, // the reported case: values inside the old range
         { { 10, null } },           // n2 gets its first NULL
         { { 5, 33 } },              // n3 below the old min, n2 now has a NULL slot
         { { 12, 1 } }               // n2 below the old min
      };

      for(int u = 0; u < appends.length; u++) {
         mv = update(def, mv, appends[u]);
         rows.addAll(Arrays.asList(appends[u]));
         assertBlock("update " + (u + 1), mv, lastFile, rows);
      }
   }

   @Test
   void appendsToALastBlockWhoseIntColumnIsAllNull() throws Exception {
      MVDef def = newDef();
      List<Object[]> src = new ArrayList<>();

      for(int i = 0; i < 10; i++) {
         src.add(new Object[] { 20 + i, 30 + i });
      }

      // last block: n3 holds only NULLs, so its dictionary range is empty
      Object[][] last = { { null, 9 }, { null, 50 }, { null, 99 } };
      src.addAll(Arrays.asList(last));
      List<BlockFile> blocks = new ArrayList<>();
      MV mv = build(def, null, src.toArray(new Object[0][]), blocks);
      assertEquals(2, mv.getBlockSize());
      lastFile = blocks.get(1);
      List<Object[]> rows = new ArrayList<>(Arrays.asList(last));
      assertBlock("base", mv, lastFile, rows);

      Object[][][] appends = {
         { { 11, 60 } },   // first value of n3 in the block
         { { 5, null } },  // n3 below the new min
         { { null, 1 } }   // another NULL
      };

      for(int u = 0; u < appends.length; u++) {
         mv = update(def, mv, appends[u]);
         rows.addAll(Arrays.asList(appends[u]));
         assertBlock("all-null update " + (u + 1), mv, lastFile, rows);
      }
   }

   @Test
   void appendBelowTheMinRemapsTheTopIndex() throws Exception {
      MVDef def = newDef();
      List<Object[]> src = new ArrayList<>();

      for(int i = 0; i < 10; i++) {
         src.add(new Object[] { 20 + i, 30 + i });
      }

      Object[][] last = { { 9, 9 }, { 10, 50 }, { null, 99 }, { 12, 20 }, { 14, 40 } };
      src.addAll(Arrays.asList(last));
      List<BlockFile> blocks = new ArrayList<>();
      MV mv = build(def, null, src.toArray(new Object[0][]), blocks);
      List<Object[]> rows = new ArrayList<>(Arrays.asList(last));
      Object[][] append = { { 5, 60 } };

      lastFile = blocks.get(1);
      mv = update(def, mv, append);
      rows.addAll(Arrays.asList(append));
      assertBlock("append below min", mv, lastFile, rows);
   }

   @Test
   void appendDoesNotChangeTheOldDictionaries() throws Exception {
      MVDef def = newDef();
      List<Object[]> src = new ArrayList<>();

      // block 0 holds the same n3 values and NULL as the last block, so the palette shares
      // one dictionary object between the two blocks
      for(int i = 0; i < 10; i++) {
         src.add(new Object[] { i == 3 ? null : 9 + (i % 6), 30 });
      }

      src.addAll(Arrays.asList(new Object[][] {
         { 9, 30 }, { 10, 30 }, { null, 30 }, { 12, 30 }, { 14, 30 } }));
      List<BlockFile> blocks = new ArrayList<>();
      MV omv = build(def, null, src.toArray(new Object[0][]), blocks);
      XDimDictionary d0 = omv.getDictionary(0, 0);
      XDimDictionary d1 = omv.getDictionary(0, 1);
      String before0 = describe(d0);
      String before1 = describe(d1);

      lastFile = blocks.get(1);
      MV nmv = update(def, omv, new Object[][] { { 11, 30 } });

      assertEquals(before0, describe(d0), "old block 0 dictionary changed");
      assertEquals(before1, describe(d1), "old last block dictionary changed");
      // block 0 is not remapped: its stored indexes must still decode to its values
      assertBlock("block 0", nmv, 0, blocks.get(0), src.subList(0, 10));

      // the merged int block reports its real min, not one lower (#82219 known limit)
      MVScriptable scriptable = new MVScriptable(def, def.getColumns().get(0), nmv);
      assertEquals(9, scriptable.getMember("MinValue"));
      assertEquals(14, scriptable.getMember("MaxValue"));
   }

   private static String describe(XDimDictionary dict) {
      StringBuilder buf = new StringBuilder("size=" + dict.size() + " min=" + dict.min() +
                                            " max=" + dict.max() + " values=");

      for(int i = 0; i <= dict.size(); i++) {
         buf.append(dict.getValue(i)).append(',');
      }

      return buf.toString();
   }

   /**
    * One incremental append into the last block, as LocalMVIncremental.update0 runs it:
    * build the new MV from the old one, remap the old rows of the last block, append the
    * new rows.
    */
   @SuppressWarnings("unchecked")
   private MV update(MVDef def, MV omv, Object[][] add) throws Exception {
      int lastBlock = omv.getBlockSize() - 1;
      MVStorage storage = Mockito.mock(MVStorage.class);
      Mockito.when(storage.get(Mockito.anyString())).thenReturn(omv);
      XServerNode server = Mockito.mock(XServerNode.class);
      XFileSystem fsys = Mockito.mock(XFileSystem.class);
      XFile xfile = Mockito.mock(XFile.class);
      List<SBlock> sblocks = new ArrayList<>();

      for(int i = 0; i < omv.getBlockSize(); i++) {
         SBlock sb = Mockito.mock(SBlock.class);
         Mockito.when(sb.getID()).thenReturn("b" + i);
         sblocks.add(sb);
      }

      Mockito.when(server.getFSystem()).thenReturn(fsys);
      Mockito.when(fsys.get(Mockito.anyString())).thenReturn(xfile);
      Mockito.when(xfile.getBlocks()).thenReturn(sblocks);
      set(def, "mvname", "bug82473");

      List<BlockFile> nfiles = new ArrayList<>();
      Map dictMaps = new HashMap(), sizes = new HashMap(), dimRanges = new HashMap(),
         intRanges = new HashMap();
      MV nmv;

      try(MockedStatic<MVStorage> st = Mockito.mockStatic(MVStorage.class);
          MockedStatic<FSService> fs = Mockito.mockStatic(FSService.class))
      {
         st.when(() -> MVStorage.getFile(Mockito.any())).thenReturn("f");
         st.when(MVStorage::getInstance).thenReturn(storage);
         fs.when(FSService::getServer).thenReturn(server);
         fs.when(FSService::getConfig).thenReturn(Mockito.mock(FSConfig.class));
         LocalMVIncremental inc = new LocalMVIncremental(def);
         nmv = build(def, omv, add, nfiles);
         assertEquals(omv.getBlockSize(), nmv.getBlockSize(), "rows must go to the last block");
         assertEquals(1, nfiles.size());

         Method m = LocalMVIncremental.class.getDeclaredMethod(
            "initMappings", MV.class, Map.class, Map.class, Map.class, Map.class, List.class);
         m.setAccessible(true);
         m.invoke(inc, nmv, dictMaps, sizes, dimRanges, intRanges, List.of(lastBlock));
      }

      String bid = "b" + lastBlock;

      // LocalFileSystem.updateBlock
      if(dictMaps.get(bid) != null) {
         lastFile = rewrite(lastFile, sub -> sub.updateData(
            (Map<Integer, Integer>[]) dictMaps.get(bid), (int[]) sizes.get(bid),
            (int[]) dimRanges.get(bid), (List<Number[]>) intRanges.get(bid)));
      }

      // LocalFileSystem.appendRecord
      try {
         SubMV record = SubMV.get(nfiles.get(0));
         lastFile = rewrite(lastFile, sub -> sub.appendBlock(record));
      }
      finally {
         SubMV.removeMap(nfiles.get(0));
      }

      return nmv;
   }

   /**
    * Run a write of a SubMV the way the server's block storage does it: read the old file and
    * write a new one (CacheBlockFile would overwrite the file it is still reading from).
    */
   private BlockFile rewrite(BlockFile file, SubMVWrite write) throws Exception {
      BlockFile target = newFile();
      BlockFile tx = new CopyOnWriteBlockFile(file, target);

      try {
         write.run(SubMV.get(tx));
      }
      finally {
         SubMV.removeMap(tx);
      }

      return target;
   }

   private BlockFile newFile() {
      BlockFile file = new CacheBlockFile("bug82473-" + UUID.randomUUID(), ".smv");
      files.add(file);
      return file;
   }

   private interface SubMVWrite {
      void run(SubMV sub) throws Exception;
   }

   /**
    * Reads the source block file and writes the target one.
    */
   private static final class CopyOnWriteBlockFile implements BlockFile {
      CopyOnWriteBlockFile(BlockFile source, BlockFile target) {
         this.source = source;
         this.target = target;
      }

      @Override
      public String getName() {
         return target.getName();
      }

      @Override
      public long lastModified() {
         return source.lastModified();
      }

      @Override
      public long length() {
         return source.length();
      }

      @Override
      public boolean exists() {
         return source.exists();
      }

      @Override
      public boolean delete() {
         return false;
      }

      @Override
      public SeekableInputStream openInputStream() throws IOException {
         return source.openInputStream();
      }

      @Override
      public TransactionChannel openWriteChannel() throws IOException {
         return target.openWriteChannel();
      }

      private final BlockFile source;
      private final BlockFile target;
   }

   private void assertBlock(String label, MV mv, BlockFile file, List<Object[]> rows)
      throws Exception
   {
      assertBlock(label, mv, mv.getBlockSize() - 1, file, rows);
   }

   private void assertBlock(String label, MV mv, int block, BlockFile file, List<Object[]> rows)
      throws Exception
   {
      try {
         DefaultTableBlock table = SubMV.get(file).getData();
         Field fd = DefaultTableBlock.class.getDeclaredField("dcols");
         fd.setAccessible(true);
         MVDimColumn[] dcols = (MVDimColumn[]) fd.get(table);
         assertEquals(rows.size(), table.getRowCount(), label + ": row count");

         for(int c = 0; c < HEADERS.length; c++) {
            XDimDictionary dict = mv.getDictionary(c, block);
            List<Object> expected = new ArrayList<>();
            List<Object> actual = new ArrayList<>();

            for(int r = 0; r < rows.size(); r++) {
               expected.add(rows.get(r)[c]);
               actual.add(dict.getValue(dcols[c].getValue(r)));
            }

            assertEquals(expected, actual, label + ": stored values of " + HEADERS[c]);
         }
      }
      finally {
         SubMV.removeMap(file);
      }
   }

   private MV build(MVDef def, MV omv, Object[][] data, List<BlockFile> out) throws Exception {
      Object[][] rows = new Object[data.length + 1][];
      rows[0] = HEADERS;
      System.arraycopy(data, 0, rows, 1, data.length);
      MVBuilder builder = new MVBuilder(new DefaultTableLens(rows), def, false, omv);
      Iterator<SubMV> it = builder.getSubMVs();

      while(it.hasNext()) {
         SubMV smv = it.next();
         BlockFile file = newFile();
         smv.write(file);
         out.add(file);
         smv.dispose();
      }

      return builder.getMV();
   }

   private static MVDef newDef() {
      List<MVColumn> columns = new ArrayList<>();

      for(String name : HEADERS) {
         ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
         ref.setDataType(XSchema.INTEGER);
         columns.add(new MVColumn(ref, true));
      }

      MVDef def = new MVDef();
      def.container = new MVContainer(columns, null);
      return def;
   }

   private static void set(Object target, String name, Object value) throws Exception {
      Field field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }
}
