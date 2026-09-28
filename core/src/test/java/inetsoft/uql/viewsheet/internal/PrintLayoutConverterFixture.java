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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.*;
import inetsoft.report.composition.RegionTableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.internal.SectionElementDef;
import inetsoft.report.internal.TableElementDef;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.XTableUtil;
import inetsoft.uql.viewsheet.*;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * One 400 x 250 table at (20, 10) in print layout, with a THIN object border and a yellow
 * background, fed to VsToReportConverter's private table methods. Its lens caches the viewer's
 * widths: 100, 100, and the last filled to the card, 200.
 */
final class PrintLayoutConverterFixture {
   PrintLayoutConverterFixture() throws Exception {
      vs.addAssembly(table);
      info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setPixelOffset(new Point(20, 10));
      info.setPixelSize(new Dimension(400, 250));
      info.setLayoutPosition(new Point(20, 10));
      info.setLayoutSize(new Dimension(400, 250));
      info.setTitleVisibleValue(true);
      VSFormat object = info.getFormat().getUserDefinedFormat();
      object.setBorders(new Insets(THIN, THIN, THIN, THIN));
      object.setBackground(Color.YELLOW);

      Field field = VsToReportConverter.class.getDeclaredField("report");
      field.setAccessible(true);
      TabularSheet report = (TabularSheet) field.get(converter);
      section = new SectionElementDef(report);
      report.addElement(0, 0, section);
   }

   PrintLayoutConverterFixture inset(int top, int left, int bottom, int right) {
      Insets inset = new Insets(top, left, bottom, right);
      converter.setTableCardInsets(ignored -> (Insets) inset.clone());
      return this;
   }

   /** The table's own title format, created if it has none yet. */
   VSFormat titleFormat() {
      FormatInfo finfo = info.getFormatInfo();
      VSCompositeFormat format = finfo.getFormat(VSAssemblyInfo.TITLEPATH);

      if(format == null) {
         format = new VSCompositeFormat();
         finfo.setFormat(VSAssemblyInfo.TITLEPATH, format);
      }

      return format.getUserDefinedFormat();
   }

   void setColumnWidths(int width) {
      for(int c = 0; c < lens().getColCount(); c++) {
         info.setColumnWidthValue2(c, width, lens());
      }
   }

   int[] columnWidths() throws Exception {
      return (int[]) invoke("calculateColumnWidths",
         new Class<?>[] { TableDataVSAssemblyInfo.class, VSTableLens.class }, info, lens());
   }

   int printHeight() throws Exception {
      return (int) invoke("computePrintLayoutTableHeight",
         new Class<?>[] { TableDataVSAssemblyInfo.class, VSTableLens.class }, info, lens());
   }

   /** Run addTable, and return what it added to the section, in order. */
   List<ReportElement> addTable() throws Exception {
      invoke("addTable",
         new Class<?>[] { TableDataVSAssembly.class, VSTableLens.class, String.class },
         table, lens(), section.getID());
      SectionBand band = band();
      List<ReportElement> elements = new ArrayList<>();

      for(int i = 0; i < band.getElementCount(); i++) {
         elements.add(band.getElement(i));
      }

      return elements;
   }

   TableElementDef tableElement() throws Exception {
      return (TableElementDef) addTable().stream()
         .filter(e -> e instanceof TableElementDef).findFirst().orElseThrow();
   }

   Rectangle bounds(ReportElement element) {
      SectionBand band = band();
      return band.getBounds(band.getElementIndex(element));
   }

   private SectionBand band() {
      return section.getSection().getSectionContent()[0];
   }

   private VSTableLens lens() {
      if(lens == null) {
         VSTableLens base = new VSTableLens(new DefaultTableLens(XTableUtil.getDefaultData()));
         base.initTableGrid(info);
         lens = new RegionTableLens(base, base.getRowCount(), base.getColCount());
      }

      return lens;
   }

   private Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
      Method method = VsToReportConverter.class.getDeclaredMethod(name, types);
      method.setAccessible(true);
      return method.invoke(converter, args);
   }

   private static final int THIN = StyleConstants.THIN_LINE;
   final VsToReportConverter converter = new VsToReportConverter(null, null, null, null, null);
   final Viewsheet vs = new Viewsheet();
   final TableVSAssembly table = new TableVSAssembly(vs, "Table1");
   final TableVSAssemblyInfo info;
   private final SectionElementDef section;
   private VSTableLens lens;
}
