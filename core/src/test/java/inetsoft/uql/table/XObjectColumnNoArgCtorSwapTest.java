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
package inetsoft.uql.table;

import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XEmbeddedTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77985, an XObjectColumn value whose class Kryo writes but can't create on read (no
 * no-arg constructor, e.g. java.util.UUID) made the whole column read back as nulls, on the
 * ordinary fragment swap, in the table being saved as a snapshot and in the reloaded snapshot.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  XObjectColumnNoArgCtorSwapTest.StorageConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XObjectColumnNoArgCtorSwapTest {
   @Test
   void customClassSurvivesFragmentSwap() {
      XSwappableTable table = createTable(i -> new NoArg("x" + i));

      try {
         swapAndCheck(table, new NoArg("x2"));
      }
      finally {
         table.dispose();
      }
   }

   @Test
   void uuidSurvivesFragmentSwap() {
      UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
      XSwappableTable table = createTable(i -> uuid);

      try {
         swapAndCheck(table, uuid);
      }
      finally {
         table.dispose();
      }
   }

   @Test
   void customClassAndUuidSurviveSnapshotSaveAndReload() throws Exception {
      UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
      XSwappableTable table = createTable(i -> i == 2 ? new NoArg("x2") : uuid);
      SnapshotEmbeddedTableAssembly assembly = createAssembly(table);

      try {
         String xml = writeEmbeddedData(assembly);
         assertTrue(xml.contains("<path>"));
         // the save swaps the live table's column out, it must read back
         assertEquals("s4", table.getObject(5, 1), "column of the saved table lost");
         assertEquals(new NoArg("x2"), table.getObject(3, 1));

         XSwappableTable reloaded = reload(xml);

         try {
            assertEquals(4, reloaded.getObject(5, 0));
            assertEquals("s0", reloaded.getObject(1, 1));
            assertEquals(new NoArg("x2"), reloaded.getObject(3, 1));
            assertEquals("s4", reloaded.getObject(5, 1), "column lost on snapshot reload");
            assertEquals(uuid, reloaded.getObject(8, 1));
            assertEquals("s49", reloaded.getObject(ROWS, 1));
         }
         finally {
            reloaded.dispose();
         }
      }
      finally {
         table.dispose();
      }
   }

   /**
    * A class with a no-arg constructor is still created through the constructor.
    */
   @Test
   void noArgConstructorStillRuns() {
      XSwappableTable table = createTable(i -> new WithCtor("w" + i));

      try {
         XTableFragment fragment = table.getTables()[0];
         assertInstanceOf(XObjectColumn.class, fragment.getColumns()[1]);
         fragment.swap(false);
         assertFalse(fragment.getColumns()[1].isValid());

         WithCtor value = (WithCtor) table.getObject(3, 1);
         assertEquals("w2", value.v);
         assertNotNull(value.cache, "constructor not run");
      }
      finally {
         table.dispose();
      }
   }

   private static void swapAndCheck(XSwappableTable table, Object expected) {
      XTableFragment fragment = table.getTables()[0];
      assertInstanceOf(XObjectColumn.class, fragment.getColumns()[1]);
      fragment.swap(false);
      assertFalse(fragment.getColumns()[1].isValid(), "column not swapped out");

      assertEquals("s0", table.getObject(1, 1));
      assertEquals(expected, table.getObject(3, 1));
      assertEquals("s4", table.getObject(5, 1), "column lost on swap-in");
      assertEquals("s49", table.getObject(ROWS, 1));
      assertEquals(4, table.getObject(5, 0));
   }

   private interface ValueAt {
      Object get(int i);
   }

   /**
    * Rows {i, "s" + i}, except rows 2 and 7 hold the special value.
    */
   private static XSwappableTable createTable(ValueAt special) {
      XSwappableTable table = new XSwappableTable(2, false);
      table.addRow(new Object[] { "a", "b" });

      for(int i = 0; i < ROWS; i++) {
         table.addRow(new Object[] { i, i == 2 || i == 7 ? special.get(i) : "s" + i });
      }

      table.complete();
      return table;
   }

   private static SnapshotEmbeddedTableAssembly createAssembly(XSwappableTable table) {
      Worksheet ws = new Worksheet();
      SnapshotEmbeddedTableAssembly assembly = new SnapshotEmbeddedTableAssembly(ws, "T77985");
      ws.addAssembly(assembly);
      assembly.setEmbeddedData(new XEmbeddedTable(table));
      return assembly;
   }

   private static String writeEmbeddedData(SnapshotEmbeddedTableAssembly assembly)
      throws Exception
   {
      Method method = SnapshotEmbeddedTableAssembly.class
         .getDeclaredMethod("writeEmbeddedData", PrintWriter.class);
      method.setAccessible(true);
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      method.invoke(assembly, writer);
      writer.flush();
      return buf.toString();
   }

   private static XSwappableTable reload(String xml) throws Exception {
      String doc = "<root>" + xml + "</root>";
      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(doc.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      SnapshotEmbeddedTableAssembly assembly =
         new SnapshotEmbeddedTableAssembly(new Worksheet(), "T77985r");
      Method method = SnapshotEmbeddedTableAssembly.class
         .getDeclaredMethod("parseEmbeddedData", Element.class);
      method.setAccessible(true);
      method.invoke(assembly, root);
      // the constructor sets an empty in-memory table, load the saved one instead
      Field stable = SnapshotEmbeddedTableAssembly.class.getDeclaredField("stable");
      stable.setAccessible(true);
      stable.set(assembly, null);

      XSwappableTable table = assembly.getTable();
      table.moreRows(XTable.EOT);
      return table;
   }

   @Configuration
   static class StorageConfiguration {
      @Bean
      public EmbeddedTableStorage embeddedTableStorage(BlobStorageManager manager) {
         return new EmbeddedTableStorage(manager);
      }
   }

   /**
    * Serializable, but no no-arg constructor.
    */
   public static class NoArg implements Serializable {
      public NoArg(String v) {
         this.v = v;
      }

      @Override
      public boolean equals(Object obj) {
         return obj instanceof NoArg && Objects.equals(v, ((NoArg) obj).v);
      }

      @Override
      public int hashCode() {
         return Objects.hashCode(v);
      }

      @Override
      public String toString() {
         return "NoArg(" + v + ")";
      }

      private String v;
   }

   public static class WithCtor implements Serializable {
      public WithCtor() {
         cache = new HashMap<>();
      }

      WithCtor(String v) {
         this();
         this.v = v;
      }

      private String v;
      private transient Map<String, Object> cache;
   }

   private static final int ROWS = 50;
}
