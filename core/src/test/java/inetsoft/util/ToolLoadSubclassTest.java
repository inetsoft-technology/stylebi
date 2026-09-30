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
package inetsoft.util;

import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.tabular.TabularEditor;
import inetsoft.uql.util.Config;
import inetsoft.uql.viewsheet.SelectionList;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.uql.viewsheet.vslayout.AbstractLayout;
import inetsoft.uql.viewsheet.vslayout.VSAssemblyLayout;
import inetsoft.util.credential.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.*;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Class names read from asset XML must be checked against the expected type before the
 * named class is initialized or constructed (Bug #77419).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ToolLoadSubclassTest {
   @Test
   void loadSubclassRejectsWrongTypeWithoutInitializing() {
      ClassCastException ex = assertThrows(ClassCastException.class, () ->
         Tool.loadSubclass(HelperSentinel.class.getName(), WSAssembly.class));

      assertTrue(ex.getMessage().contains(WSAssembly.class.getName()), ex.getMessage());
      assertEquals(0, HELPER[0], "static initializer ran");
      assertEquals(0, HELPER[1], "constructor ran");
   }

   @Test
   void loadSubclassAcceptsSubclassWithoutConstructing() throws Exception {
      Class<? extends WSAssembly> cls =
         Tool.loadSubclass(EmbeddedTableAssembly.class.getName(), WSAssembly.class);
      assertEquals(EmbeddedTableAssembly.class, cls);

      // JDK expected types and arrays still resolve through the core class loader
      assertEquals(ExtendedDateFormat.class,
                   Tool.loadSubclass(ExtendedDateFormat.class.getName(), java.text.Format.class));
      assertEquals(String[].class, Tool.loadSubclass("[Ljava.lang.String;", Object.class));
   }

   @Test
   void loadSubclassKeepsClassNotFoundException() {
      assertThrows(ClassNotFoundException.class, () ->
         Tool.loadSubclass("inetsoft.util.NoSuchClass77419", Object.class));
   }

   @Test
   void worksheetRejectsWrongAssemblyClass() throws Exception {
      Element root = writeWorksheet();
      setAssemblyClass(root, WorksheetSentinel.class.getName());

      assertThrows(ClassCastException.class, () -> new Worksheet().parseXML(root));
      assertEquals(0, WORKSHEET[0], "static initializer ran");
      assertEquals(0, WORKSHEET[1], "constructor ran");
   }

   @Test
   void worksheetParsesAssemblyClass() throws Exception {
      Element root = writeWorksheet();
      Worksheet ws = new Worksheet();
      ws.parseXML(root);
      assertInstanceOf(EmbeddedTableAssembly.class, ws.getAssembly("T1"));

      // legacy short class name without a package
      Element legacy = writeWorksheet();
      setAssemblyClass(legacy, "EmbeddedTableAssembly");
      ws = new Worksheet();
      ws.parseXML(legacy);
      assertInstanceOf(EmbeddedTableAssembly.class, ws.getAssembly("T1"));
   }

   @Test
   void layoutRejectsWrongAssemblyLayoutClass() throws Exception {
      Element elem = writeLayout();
      elem.setAttribute("class", LayoutSentinel.class.getName());

      assertThrows(ClassCastException.class, () -> AbstractLayout.createAssemblyLayout(elem));
      assertEquals(0, LAYOUT[0], "static initializer ran");
      assertEquals(0, LAYOUT[1], "constructor ran");
   }

   @Test
   void layoutParsesAssemblyLayoutClass() throws Exception {
      VSAssemblyLayout layout = AbstractLayout.createAssemblyLayout(writeLayout());
      assertEquals(VSAssemblyLayout.class, layout.getClass());
      assertEquals("Chart1", layout.getName());
   }

   @Test
   void selectionListRejectsWrongValueClass() throws Exception {
      Element elem = writeSelectionList();
      setValueClass(elem, SelectionSentinel.class.getName());

      assertThrows(ClassCastException.class, () -> new SelectionList().parseXML(elem));
      assertEquals(0, SELECTION[0], "static initializer ran");
      assertEquals(0, SELECTION[1], "constructor ran");
   }

   @Test
   void selectionListParsesValueClass() throws Exception {
      SelectionList list = new SelectionList();
      list.parseXML(writeSelectionList());
      assertEquals(2, list.getSelectionValueCount());
      assertEquals("b", list.getSelectionValue(1).getValue());
   }

   @Test
   void jdbcDataSourceRejectsWrongCredentialClass() throws Exception {
      withCredentialService(() -> {
         Element elem = createDataSource(CredentialSentinel.class.getName());

         assertThrows(ClassCastException.class, () -> new JDBCDataSource().parseXML(elem));
         assertEquals(0, CREDENTIAL[0], "static initializer ran");
         assertEquals(0, CREDENTIAL[1], "constructor ran");
      });
   }

   @Test
   void jdbcDataSourceParsesCredentialClass() throws Exception {
      withCredentialService(() -> {
         JDBCDataSource ds = new JDBCDataSource();
         ds.parseXML(createDataSource(LocalPasswordCredential.class.getName()));

         assertInstanceOf(LocalPasswordCredential.class, ds.getCredential());
         assertEquals("dbuser", ds.getUser());
      });
   }

   @Test
   void tabularEditorParsesArrayAndCollectionListValues() throws Exception {
      TabularEditor array = parseEditor(e -> {
         e.setPropertyType(String[].class.getName());
         e.setValue(new String[] { "a", "b" });
      });
      assertArrayEquals(new String[] { "a", "b" }, (String[]) array.getValue());

      TabularEditor collection = parseEditor(e -> {
         e.setPropertyType(ArrayList.class.getName());
         e.setValue(new ArrayList<>(Arrays.asList("a", "b")));
      });
      assertEquals(Arrays.asList("a", "b"), collection.getValue());

      assertThrows(ClassCastException.class, () -> parseEditor(e -> {
         e.setPropertyType(EditorSentinel.class.getName());
         e.setValue(new ArrayList<>(Arrays.asList("a", "b")));
      }));
      assertEquals(0, EDITOR[0], "static initializer ran");
      assertEquals(0, EDITOR[1], "constructor ran");
   }

   private static Element writeWorksheet() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "T1"));
      StringWriter buf = new StringWriter();
      ws.writeXML(new PrintWriter(buf));
      return parse(buf.toString());
   }

   private static void setAssemblyClass(Element root, String cls) {
      NodeList list = root.getElementsByTagName("oneAssembly");
      assertTrue(list.getLength() > 0);
      Element assembly = Tool.getChildNodeByTagName(list.item(0), "assembly");
      assertNotNull(assembly);
      assembly.setAttribute("class", cls);
   }

   private static Element writeLayout() throws Exception {
      VSAssemblyLayout layout =
         new VSAssemblyLayout("Chart1", new Point(10, 20), new Dimension(100, 200));
      StringWriter buf = new StringWriter();
      layout.writeXML(new PrintWriter(buf));
      return parse(buf.toString());
   }

   private static Element writeSelectionList() throws Exception {
      SelectionList list = new SelectionList();
      list.addSelectionValue(new SelectionValue("a", "a"));
      list.addSelectionValue(new SelectionValue("b", "b"));
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      list.writeXML(writer);
      writer.flush();
      return parse(buf.toString());
   }

   private static void setValueClass(Element elem, String cls) {
      NodeList list = elem.getElementsByTagName("VSValue");
      assertTrue(list.getLength() > 0);
      ((Element) list.item(0)).setAttribute("class", cls);
   }

   private static Element createDataSource(String credentialClass) throws Exception {
      return parse(
         "<ds_jdbc name=\"ds77419\" url=\"jdbc:h2:mem:ds77419\" driver=\"org.h2.Driver\">" +
         "<PasswordCredential class=\"" + credentialClass + "\"><user>dbuser</user>" +
         "</PasswordCredential></ds_jdbc>");
   }

   private static TabularEditor parseEditor(Consumer<TabularEditor> init) throws Exception {
      TabularEditor editor = new TabularEditor();
      editor.setType(TabularEditor.Type.LIST);
      editor.setSubtype(TabularEditor.Type.TEXT);
      editor.setPropertySubtype(String.class.getName());
      init.accept(editor);
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      editor.writeXML(writer);
      writer.flush();

      TabularEditor result = new TabularEditor();
      result.parseXML(parse(buf.toString()));
      return result;
   }

   private static void withCredentialService(ThrowingRunnable test) throws Exception {
      CredentialService service = mock(CredentialService.class);
      when(service.createCredential(any(), anyBoolean()))
         .thenAnswer(inv -> new LocalPasswordCredential());

      try(MockedStatic<CredentialService> serviceStatic = mockStatic(CredentialService.class);
          MockedStatic<Config> configStatic = mockStatic(Config.class))
      {
         serviceStatic.when(CredentialService::getInstance).thenReturn(service);
         configStatic.when(Config::getConfig).thenReturn(mock(Config.class));
         test.run();
      }
   }

   private static Element parse(String xml) throws Exception {
      return Tool.parseXML(new StringReader(xml)).getDocumentElement();
   }

   @FunctionalInterface
   private interface ThrowingRunnable {
      void run() throws Exception;
   }

   // [static initializer runs, constructor runs] per sentinel. The counters live here so that
   // reading them does not initialize the sentinel class itself.
   private static final int[] HELPER = new int[2];
   private static final int[] WORKSHEET = new int[2];
   private static final int[] LAYOUT = new int[2];
   private static final int[] SELECTION = new int[2];
   private static final int[] CREDENTIAL = new int[2];
   private static final int[] EDITOR = new int[2];

   public static class HelperSentinel {
      static { HELPER[0]++; }
      public HelperSentinel() { HELPER[1]++; }
   }

   public static class WorksheetSentinel {
      static { WORKSHEET[0]++; }
      public WorksheetSentinel() { WORKSHEET[1]++; }
   }

   public static class LayoutSentinel {
      static { LAYOUT[0]++; }
      public LayoutSentinel() { LAYOUT[1]++; }
   }

   public static class SelectionSentinel {
      static { SELECTION[0]++; }
      public SelectionSentinel() { SELECTION[1]++; }
   }

   public static class CredentialSentinel {
      static { CREDENTIAL[0]++; }
      public CredentialSentinel() { CREDENTIAL[1]++; }
   }

   public static class EditorSentinel {
      static { EDITOR[0]++; }
      public EditorSentinel() { EDITOR[1]++; }
   }
}
