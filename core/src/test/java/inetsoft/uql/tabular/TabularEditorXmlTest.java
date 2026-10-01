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
package inetsoft.uql.tabular;

import inetsoft.test.*;
import inetsoft.util.ItemList;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The list property type in a tabular editor's XML is only matched against the known list
 * types, so a class named in the XML is never initialized or constructed (Bug #77449), while
 * the list values of data source properties still round trip.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TabularEditorXmlTest {
   @Test
   void unknownCollectionClassIsNotInitializedOrConstructed() throws Exception {
      TabularEditor editor = parseEditor(e -> {
         e.setPropertyType(SentinelCollection.class.getName());
         e.setValue(new ArrayList<>(List.of("a", "b")));
      });

      assertNull(editor.getValue());
      assertEquals(0, SENTINEL[0], "static initializer ran");
      assertEquals(0, SENTINEL[1], "constructor ran");
   }

   @Test
   void unknownElementClassIsNotLoaded() throws Exception {
      TabularEditor editor = parseEditor(e -> {
         e.setPropertyType(String[].class.getName());
         e.setPropertySubtype(SentinelElement.class.getName());
         e.setValue(new String[] { "a" });
      });

      assertArrayEquals(new String[] { "a" }, (String[]) editor.getValue());
      assertEquals(0, SENTINEL[2], "element static initializer ran");
   }

   @Test
   void stringArrayRoundTrips() throws Exception {
      TabularEditor editor = parseEditor(e -> {
         e.setPropertyType(String[].class.getName());
         e.setValue(new String[] { "a", "b" });
      });

      assertArrayEquals(new String[] { "a", "b" }, (String[]) editor.getValue());
   }

   @Test
   void httpParameterArrayRoundTrips() throws Exception {
      HttpParameter param = new HttpParameter();
      param.setName("key");
      param.setValue("value");
      param.setType(HttpParameter.ParameterType.QUERY);

      TabularEditor editor = parseEditor(e -> {
         e.setSubtype(TabularEditor.Type.HTTP_PARAMETER);
         e.setPropertyType(HttpParameter[].class.getName());
         e.setPropertySubtype(HttpParameter.class.getName());
         e.setValue(new HttpParameter[] { param });
      });

      HttpParameter[] result = assertInstanceOf(HttpParameter[].class, editor.getValue());
      assertEquals(1, result.length);
      assertEquals("key", result[0].getName());
      assertEquals("value", result[0].getValue());
      assertEquals(HttpParameter.ParameterType.QUERY, result[0].getType());
   }

   @Test
   void listInterfaceRoundTrips() throws Exception {
      TabularEditor editor = parseEditor(e -> {
         e.setPropertyType(List.class.getName());
         e.setValue(List.of("a", "b"));
      });

      assertEquals(List.of("a", "b"), editor.getValue());
   }

   @Test
   void setInterfaceRoundTrips() throws Exception {
      TabularEditor editor = parseEditor(e -> {
         e.setPropertyType(Set.class.getName());
         e.setValue(new LinkedHashSet<>(List.of("a", "b")));
      });

      assertEquals(Set.of("a", "b"), editor.getValue());
   }

   @Test
   void collectionWithUnknownSubtypeKeepsValues() throws Exception {
      TabularEditor editor = parseEditor(e -> {
         e.setPropertyType(List.class.getName());
         e.setPropertySubtype("com.example.plugin.AuthType");
         e.setValue(List.of("BASIC"));
      });

      assertEquals(List.of("BASIC"), editor.getValue());
   }

   @Test
   void primitiveArrayIsNotParsed() throws Exception {
      TabularEditor editor = parseEditor(e -> {
         e.setSubtype(TabularEditor.Type.INT);
         e.setPropertyType(int[].class.getName());
         e.setPropertySubtype(Integer.class.getName());
         e.setValue(new Integer[] { 1 });
      });

      assertNull(editor.getValue());
   }

   @Test
   void missingPropertyTypeIsNotParsed() throws Exception {
      TabularEditor editor = parseEditor(e -> e.setValue(new String[] { "a" }));
      assertNull(editor.getValue());
   }

   @Test
   void itemListWithUnknownCollectionEditorKeepsParsing() throws Exception {
      String xml = "<itemList><tabularEditor class=\"" + TabularEditor.class.getName() + "\">" +
         "<type>LIST</type><subtype>TEXT</subtype>" +
         "<propertyType><![CDATA[" + ItemListSentinel.class.getName() + "]]></propertyType>" +
         "<propertySubtype><![CDATA[java.lang.String]]></propertySubtype>" +
         "<value><value><![CDATA[a]]></value></value></tabularEditor></itemList>";
      ItemList list = new ItemList();
      list.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      assertEquals(1, list.size());
      TabularEditor editor = assertInstanceOf(TabularEditor.class, list.getItem(0));
      assertNull(editor.getValue());
      assertEquals(0, SENTINEL[3], "static initializer ran");
      assertEquals(0, SENTINEL[4], "constructor ran");
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

      Document doc = Tool.parseXML(new StringReader(buf.toString()));
      TabularEditor result = new TabularEditor();
      result.parseXML(doc.getDocumentElement());
      return result;
   }

   private static final int[] SENTINEL = new int[5];

   public static class SentinelCollection extends ArrayList<Object> {
      static { SENTINEL[0]++; }
      public SentinelCollection() { SENTINEL[1]++; }
   }

   public static class ItemListSentinel extends ArrayList<Object> {
      static { SENTINEL[3]++; }
      public ItemListSentinel() { SENTINEL[4]++; }
   }

   public static class SentinelElement {
      static { SENTINEL[2]++; }
   }
}
