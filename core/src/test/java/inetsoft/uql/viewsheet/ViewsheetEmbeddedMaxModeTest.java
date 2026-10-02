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
package inetsoft.uql.viewsheet;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.awt.Dimension;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77290: {@link Viewsheet#update} replaces embedded viewsheets with fresh copies and
 * must keep the runtime max mode state of an assembly enlarged inside an embedded viewsheet.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetEmbeddedMaxModeTest {
   private static final Dimension MAX_SIZE = new Dimension(800, 600);
   private static final int MAX_MODE_Z = 12345;

   private final AssetEntry childEntry = new AssetEntry(
      AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "child", null);
   private final AssetEntry parentEntry = new AssetEntry(
      AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "parent", null);

   @Test
   void oneLevelTableKeepsMaxModeAcrossUpdate() throws Exception {
      Viewsheet child = createChild();
      Viewsheet root = new Viewsheet();
      embed(root, child, childEntry, "Embedded1", 7);
      AssetRepository rep = repository(child, null);
      String name = "Embedded1.Table1";

      toggleMaxMode(root, name, MAX_SIZE);
      Viewsheet oldEmbedded = (Viewsheet) root.getAssembly("Embedded1");
      root.update(rep, null, null);

      Viewsheet embedded = (Viewsheet) root.getAssembly("Embedded1");
      assertNotSame(oldEmbedded, embedded, "update() should replace the embedded viewsheet");
      assertTrue(root.isMaxMode());
      assertTrue(embedded.isMaxMode());
      TableDataVSAssemblyInfo info = tableInfo(root, name);
      assertEquals(MAX_SIZE, info.getMaxSize());
      assertEquals(MAX_MODE_Z, info.getMaxModeZIndex());
      assertEquals(100007, embedded.getZIndex());
      assertTrue(isVisible(root, name));

      toggleMaxMode(root, name, null);

      assertFalse(root.isMaxMode());
      assertFalse(((Viewsheet) root.getAssembly("Embedded1")).isMaxMode());
      assertNull(tableInfo(root, name).getMaxSize());
      assertEquals(7, root.getAssembly("Embedded1").getZIndex());
   }

   @Test
   void twoLevelTableKeepsMaxModeAndInnerZIndexAcrossUpdate() throws Exception {
      Viewsheet child = createChild();
      Viewsheet parent = new Viewsheet();
      embed(parent, child, childEntry, "Inner", 5);
      Viewsheet root = new Viewsheet();
      embed(root, parent, parentEntry, "Embedded1", 7);
      AssetRepository rep = repository(child, parent);
      String name = "Embedded1.Inner.Table1";

      toggleMaxMode(root, name, MAX_SIZE);
      root.update(rep, null, null);

      Viewsheet embedded = (Viewsheet) root.getAssembly("Embedded1");
      Viewsheet inner = (Viewsheet) root.getAssembly("Embedded1.Inner");
      assertTrue(embedded.isMaxMode());
      assertTrue(inner.isMaxMode());
      assertEquals(100007, embedded.getZIndex());
      assertEquals(100005, inner.getZIndex());
      assertEquals(MAX_SIZE, tableInfo(root, name).getMaxSize());
      assertTrue(isVisible(root, name));

      toggleMaxMode(root, name, null);

      assertFalse(root.isMaxMode());
      assertFalse(((Viewsheet) root.getAssembly("Embedded1")).isMaxMode());
      assertFalse(((Viewsheet) root.getAssembly("Embedded1.Inner")).isMaxMode());
      assertNull(tableInfo(root, name).getMaxSize());
      assertEquals(7, root.getAssembly("Embedded1").getZIndex());
      assertEquals(5, root.getAssembly("Embedded1.Inner").getZIndex());
   }

   @Test
   void chartAndSelectionKeepMaxModeAcrossUpdate() throws Exception {
      Viewsheet child = createChild();
      child.addAssembly(new ChartVSAssembly(child, "Chart1"));
      child.addAssembly(new SelectionListVSAssembly(child, "List1"));
      Viewsheet root = new Viewsheet();
      embed(root, child, childEntry, "Embedded1", 7);
      AssetRepository rep = repository(child, null);

      toggleMaxMode(root, "Embedded1.Chart1", MAX_SIZE);
      root.update(rep, null, null);
      ChartVSAssemblyInfo chartInfo =
         (ChartVSAssemblyInfo) root.getAssembly("Embedded1.Chart1").getInfo();
      assertEquals(MAX_SIZE, chartInfo.getMaxSize());
      assertEquals(MAX_MODE_Z, chartInfo.getMaxModeZIndex());
      toggleMaxMode(root, "Embedded1.Chart1", null);

      toggleMaxMode(root, "Embedded1.List1", MAX_SIZE);
      root.update(rep, null, null);
      MaxModeSupportAssemblyInfo listInfo =
         (MaxModeSupportAssemblyInfo) root.getAssembly("Embedded1.List1").getInfo();
      assertEquals(MAX_SIZE, listInfo.getMaxSize());
      assertEquals(MAX_MODE_Z, listInfo.getMaxModeZIndex());
      assertTrue(((Viewsheet) root.getAssembly("Embedded1")).isMaxMode());
   }

   @Test
   void updateWithoutMaxModeLeavesRefreshedCopyUntouched() throws Exception {
      Viewsheet child = createChild();
      Viewsheet root = new Viewsheet();
      embed(root, child, childEntry, "Embedded1", 7);
      AssetRepository rep = repository(child, null);

      root.update(rep, null, null);

      assertFalse(root.isMaxMode());
      assertFalse(((Viewsheet) root.getAssembly("Embedded1")).isMaxMode());
      assertNull(tableInfo(root, "Embedded1.Table1").getMaxSize());
      assertEquals(7, root.getAssembly("Embedded1").getZIndex());
   }

   @Test
   void changedAssemblyTypeIsNotCopied() throws Exception {
      Viewsheet child = createChild();
      Viewsheet root = new Viewsheet();
      embed(root, child, childEntry, "Embedded1", 7);
      toggleMaxMode(root, "Embedded1.Table1", MAX_SIZE);

      // the embedded child is redesigned: Table1 is now a text assembly
      Viewsheet redesigned = new Viewsheet();
      redesigned.addAssembly(new TextVSAssembly(redesigned, "Table1"));
      root.update(repository(redesigned, null), null, null);

      Assembly assembly = root.getAssembly("Embedded1.Table1");
      assertInstanceOf(TextVSAssembly.class, assembly);
   }

   @Test
   void maxModeSurvivesBookmarkStateApplyAfterUpdate() throws Exception {
      Viewsheet child = createChild();
      Viewsheet root = new Viewsheet();
      embed(root, child, childEntry, "Embedded1", 7);
      String name = "Embedded1.Table1";

      toggleMaxMode(root, name, MAX_SIZE);
      root.update(repository(child, null), null, null);

      // emulate RuntimeViewsheet.gotoBookmark(): vs.clone() + VSBookmark.getBookmark()
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
      root.writeState(writer, true);
      writer.close();
      Element state = Tool.parseXML(new ByteArrayInputStream(out.toByteArray()))
         .getDocumentElement();
      Viewsheet processed = root.clone();
      processed.parseState(state, true);

      assertTrue(processed.isMaxMode());
      assertTrue(((Viewsheet) processed.getAssembly("Embedded1")).isMaxMode());
      assertEquals(MAX_SIZE, tableInfo(processed, name).getMaxSize());
      assertTrue(isVisible(processed, name));
   }

   private Viewsheet createChild() {
      Viewsheet child = new Viewsheet();
      child.addAssembly(new TableVSAssembly(child, "Table1"));
      child.addAssembly(new TextVSAssembly(child, "Text1"));
      return child;
   }

   private void embed(Viewsheet container, Viewsheet design, AssetEntry entry, String name,
                      int zIndex)
   {
      Viewsheet vs = design.clone();
      vs.setEntry(entry);
      vs.getVSAssemblyInfo().setName(name);
      container.addAssembly(vs);
      // addAssembly() adjusts the z-index, set it afterwards
      vs.setZIndex(zIndex);
   }

   private AssetRepository repository(Viewsheet child, Viewsheet parent) throws Exception {
      AssetRepository rep = mock(AssetRepository.class);
      when(rep.getSheet(any(), any(), anyBoolean(), any())).thenAnswer(inv ->
         childEntry.equals(inv.getArgument(0)) ? child.clone() :
            parent == null ? null : parent.clone());
      return rep;
   }

   /**
    * Same state changes as VSTableMaxModeService/VSChartMaxModeService/MaxModeAssemblyService.
    */
   private void toggleMaxMode(Viewsheet root, String name, Dimension maxSize) {
      Assembly assembly = root.getAssembly(name);
      Viewsheet vs = (Viewsheet) root.getAssembly(name.substring(0, name.lastIndexOf('.')));
      int zAdjust = maxSize == null ? -100000 : 100000;
      vs.setMaxMode(maxSize != null);

      while(vs.getViewsheet() != null) {
         vs.setZIndex(vs.getZIndex() + zAdjust);
         vs = vs.getViewsheet();
         vs.setMaxMode(maxSize != null);
      }

      Consumer<Integer> setZ;

      if(assembly.getInfo() instanceof TableDataVSAssemblyInfo info) {
         info.setMaxSize(maxSize);
         setZ = info::setMaxModeZIndex;
      }
      else if(assembly.getInfo() instanceof ChartVSAssemblyInfo info) {
         info.setMaxSize(maxSize);
         setZ = info::setMaxModeZIndex;
      }
      else {
         MaxModeSupportAssemblyInfo info = (MaxModeSupportAssemblyInfo) assembly.getInfo();
         info.setMaxSize(maxSize);
         setZ = info::setMaxModeZIndex;
      }

      setZ.accept(maxSize == null ? -1 : MAX_MODE_Z);
   }

   private static TableDataVSAssemblyInfo tableInfo(Viewsheet root, String name) {
      return (TableDataVSAssemblyInfo) root.getAssembly(name).getInfo();
   }

   /**
    * Same visibility rule as VSObjectModel for an assembly while the top viewsheet is in max mode.
    */
   private static boolean isVisible(Viewsheet root, String name) {
      Assembly assembly = root.getAssembly(name);
      boolean sheetMaxMode = false;

      for(Viewsheet p = ((VSAssembly) assembly).getViewsheet(); p != null; p = p.getViewsheet()) {
         sheetMaxMode = p.isMaxMode();
      }

      return !sheetMaxMode || ((DataVSAssemblyInfo) assembly.getInfo()).getMaxSize() != null;
   }
}
