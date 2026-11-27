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
 * Bug #82485: an incremental update whose new rows need more than one sub-MV appended every
 * sub-MV to the old last block, because {@code MVBuilder}'s iterator set {@code subMVBlockIndex}
 * once and never reset it. The stored dimension indexes of the earlier sub-MVs were then never
 * remapped to the final merged dictionary, so rows read back with the wrong value when a later
 * sub-MV added values that sort before the earlier ones. After the fix only the first sub-MV (the
 * one that fills the last block) targets it and the others create new blocks.
 *
 * <p>The test runs the real {@code MVBuilder}, the real (private)
 * {@code LocalMVIncremental.initMappings} and {@code SubMV.updateData}/{@code appendBlock} on the
 * block files, as {@code LocalFileSystem.updateBlock} and {@code appendRecord} do. Only the block
 * id lookup ({@code FSService}, {@code MVStorage}) is mocked.
 *
 * <p>The break column branch of {@code MVBuilder.getIncrementalBlockSize} is not covered: it
 * needs an MV whose break values were registered by {@code MVIncremental} and a date or
 * numeric break column; that infrastructure does not exist in the test tree.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVBuilderMultiSubMVAppendTest {
   private static final String[] HEADERS = { "n3", "n2" };
   private static final int PREFERRED = 10;
   private String oldPreferred;
   private final List<BlockFile> files = new ArrayList<>();

   @BeforeEach
   void setUp() {
      oldPreferred = SreeEnv.getProperty("mv.preferred.block");
      SreeEnv.setProperty("mv.preferred.block", Integer.toString(PREFERRED));
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("mv.preferred.block", oldPreferred);

      for(BlockFile file : files) {
         SubMV.removeMap(file);
         file.delete();
      }
   }

   /**
    * Later sub-MVs add int values below the ones of the earlier sub-MVs (no NULL).
    */
   @Test
   void descendingIntsAcrossSubMVsKeepTheirValues() throws Exception {
      List<Object[]> add = new ArrayList<>();

      for(int i = 0; i < 20; i++) {
         add.add(new Object[] { 8 - i, 500 + i });
      }

      Result result = run(XSchema.INTEGER, intBase(), add);
      assertEquals(List.of(1, -1, -1), result.subIndexes, "only the first sub-MV fills the last block");
      assertNoWrongRows(result);
      assertBlockSizes(result, 35);
   }

   /**
    * String values added later sort before the earlier ones, which shifts the dictionary indexes.
    */
   @Test
   void laterStringValuesSortingFirstKeepTheirValues() throws Exception {
      List<Object[]> base = new ArrayList<>();

      for(int i = 0; i < 10; i++) {
         base.add(new Object[] { "w" + (10 + i), 30 + i });
      }

      for(int i = 0; i < 5; i++) {
         base.add(new Object[] { "m" + (10 + i), 40 + i });
      }

      List<Object[]> add = new ArrayList<>();

      for(int i = 0; i < 20; i++) {
         add.add(new Object[] { "k" + (99 - i), 500 + i });
      }

      Result result = run(XSchema.STRING, base, add);
      assertEquals(List.of(1, -1, -1), result.subIndexes);
      assertNoWrongRows(result);
      assertBlockSizes(result, 35);
   }

   /**
    * The new rows fit in the last block: one sub-MV, appended to the last block, no new block.
    */
   @Test
   void rowsThatFitInTheLastBlockStayThere() throws Exception {
      List<Object[]> add = new ArrayList<>();

      for(int i = 0; i < 3; i++) {
         add.add(new Object[] { 8 - i, 500 + i });
      }

      Result result = run(XSchema.INTEGER, intBase(), add);
      assertEquals(List.of(1), result.subIndexes);
      assertEquals(2, result.blockCount, "no new block is needed");
      assertNoWrongRows(result);
      assertBlockSizes(result, 18);
   }

   /**
    * Ascending values (indexes do not shift) must keep working and use the same block layout.
    */
   @Test
   void ascendingIntsAcrossSubMVsKeepTheirValues() throws Exception {
      List<Object[]> add = new ArrayList<>();

      for(int i = 0; i < 20; i++) {
         add.add(new Object[] { 100 + i * 3, 500 + i });
      }

      Result result = run(XSchema.INTEGER, intBase(), add);
      assertEquals(List.of(1, -1, -1), result.subIndexes);
      assertNoWrongRows(result);
      assertBlockSizes(result, 35);
   }

   private static void assertNoWrongRows(Result result) {
      assertEquals(result.expected.size(), result.actual.size(), "row count");
      assertEquals(Collections.emptyList(), result.missing, "rows read back with a wrong value");
      assertEquals(Collections.emptyList(), result.extra, "unexpected rows");
   }

   private static void assertBlockSizes(Result result, int total) {
      int sum = 0;

      for(int count : result.blockRows) {
         // the builder may exceed the preferred size by at most 25% when filling the last block
         assertTrue(count <= PREFERRED * 1.25, "block of " + count + " rows is too large");
         sum += count;
      }

      assertEquals(total, sum, "total rows");
   }

   private static List<Object[]> intBase() {
      List<Object[]> base = new ArrayList<>();

      // block 0: 10 rows, last block: 5 rows
      for(int i = 0; i < 10; i++) {
         base.add(new Object[] { 20 + i, 30 + i });
      }

      Object[][] last = { { 9, 9 }, { 10, 50 }, { 11, 99 }, { 12, 20 }, { 14, 40 } };
      base.addAll(Arrays.asList(last));
      return base;
   }

   /**
    * One incremental update: build the new MV from the old one, remap the old rows of the last
    * block, append the sub-MVs and read every block back.
    */
   @SuppressWarnings("unchecked")
   private Result run(String type, List<Object[]> src, List<Object[]> add) throws Exception {
      MVDef def = newDef(type);
      List<BlockFile> blocks = new ArrayList<>();
      MV omv = build(def, null, src.toArray(new Object[0][]), blocks);
      assertEquals(2, omv.getBlockSize(), "base MV must have a full block and a partial one");
      BlockFile lastFile = blocks.get(1);

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
      set(def, "mvname", "bug82485");

      Map dictMaps = new HashMap(), sizes = new HashMap(), dimRanges = new HashMap(),
         intRanges = new HashMap();
      List<BlockFile> nfiles = new ArrayList<>();
      List<Integer> idx = new ArrayList<>();
      MV nmv;

      try(MockedStatic<MVStorage> st = Mockito.mockStatic(MVStorage.class);
          MockedStatic<FSService> fs = Mockito.mockStatic(FSService.class))
      {
         st.when(() -> MVStorage.getFile(Mockito.any())).thenReturn("f");
         st.when(MVStorage::getInstance).thenReturn(storage);
         fs.when(FSService::getServer).thenReturn(server);
         fs.when(FSService::getConfig).thenReturn(Mockito.mock(FSConfig.class));
         LocalMVIncremental inc = new LocalMVIncremental(def);
         Object[][] rows = new Object[add.size() + 1][];
         rows[0] = HEADERS;

         for(int i = 0; i < add.size(); i++) {
            rows[i + 1] = add.get(i);
         }

         MVBuilder builder = new MVBuilder(new DefaultTableLens(rows), def, false, omv);
         Iterator<SubMV> it = builder.getSubMVs();

         while(it.hasNext()) {
            SubMV smv = it.next();
            BlockFile f = newFile();
            smv.write(f);
            nfiles.add(f);
            idx.add(smv.getBlockIndex());
            smv.dispose();
         }

         nmv = builder.getMV();
         Method m = LocalMVIncremental.class.getDeclaredMethod(
            "initMappings", MV.class, Map.class, Map.class, Map.class, Map.class, List.class);
         m.setAccessible(true);
         m.invoke(inc, nmv, dictMaps, sizes, dimRanges, intRanges, new ArrayList<>(idx));
      }

      int lb = omv.getBlockSize() - 1;
      String bid = "b" + lb;

      // LocalFileSystem.updateBlock
      if(dictMaps.get(bid) != null) {
         lastFile = rewrite(lastFile, sub -> sub.updateData(
            (Map<Integer, Integer>[]) dictMaps.get(bid), (int[]) sizes.get(bid),
            (int[]) dimRanges.get(bid), (List<Number[]>) intRanges.get(bid)));
      }

      // LocalFileSystem.appendRecord: every sub-MV with a block index is appended, last first
      for(int i = idx.size() - 1; i >= 0; i--) {
         if(idx.get(i) >= 0) {
            SubMV record = SubMV.get(nfiles.get(i));
            lastFile = rewrite(lastFile, sub -> sub.appendBlock(record));
         }
      }

      // sub-MVs without a block index become new blocks, in order
      Map<Integer, BlockFile> bf = new TreeMap<>();
      bf.put(0, blocks.get(0));
      bf.put(lb, lastFile);
      int nb = lb + 1;

      for(int i = 0; i < idx.size(); i++) {
         if(idx.get(i) < 0) {
            bf.put(nb++, nfiles.get(i));
         }
      }

      Result result = new Result();
      result.subIndexes = idx;
      result.blockCount = nmv.getBlockSize();

      for(Object[] r : src) {
         result.expected.add(r[0] + "/" + r[1]);
      }

      for(Object[] r : add) {
         result.expected.add(r[0] + "/" + r[1]);
      }

      for(Map.Entry<Integer, BlockFile> e : bf.entrySet()) {
         DefaultTableBlock table = SubMV.get(e.getValue()).getData();
         Field fd = DefaultTableBlock.class.getDeclaredField("dcols");
         fd.setAccessible(true);
         MVDimColumn[] dcols = (MVDimColumn[]) fd.get(table);
         result.blockRows.add(table.getRowCount());

         for(int r = 0; r < table.getRowCount(); r++) {
            Object a = nmv.getDictionary(0, e.getKey()).getValue(dcols[0].getValue(r));
            Object b = nmv.getDictionary(1, e.getKey()).getValue(dcols[1].getValue(r));
            result.actual.add(a + "/" + b);
         }

         SubMV.removeMap(e.getValue());
      }

      result.missing.addAll(result.expected);

      for(String a : result.actual) {
         result.missing.remove(a);
      }

      result.extra.addAll(result.actual);

      for(String a : result.expected) {
         result.extra.remove(a);
      }

      return result;
   }

   private static final class Result {
      List<Integer> subIndexes;
      int blockCount;
      final List<String> expected = new ArrayList<>();
      final List<String> actual = new ArrayList<>();
      final List<String> missing = new ArrayList<>();
      final List<String> extra = new ArrayList<>();
      final List<Integer> blockRows = new ArrayList<>();
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
      BlockFile file = new CacheBlockFile("bug82485-" + UUID.randomUUID(), ".smv");
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

   private static MVDef newDef(String firstType) {
      List<MVColumn> columns = new ArrayList<>();

      for(int i = 0; i < HEADERS.length; i++) {
         ColumnRef ref = new ColumnRef(new AttributeRef(null, HEADERS[i]));
         ref.setDataType(i == 0 ? firstType : XSchema.INTEGER);
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
