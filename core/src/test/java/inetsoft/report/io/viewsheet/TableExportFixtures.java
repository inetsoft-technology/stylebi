package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;

import java.awt.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A three-column table at (40, 60) with a visible 20px title, and an exporter that resolves a
 * given card inset.
 */
final class TableExportFixtures {
   static TableVSAssembly table(Insets padding, Dimension size, double... widths) {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      configure(table.getTableDataVSAssemblyInfo(), padding, size, widths);
      return table;
   }

   static void configure(TableDataVSAssemblyInfo info, Insets padding, Dimension size,
                         double... widths)
   {
      info.setPixelOffset(new Point(40, 60));
      info.setPixelSize(size);
      info.setPadding(padding);
      info.setTitleVisibleValue(true);
      info.setTitleHeightValue(20);
      // pin this as the customer's own height, not the type's density default
      info.setUserTitleHeight(true);

      for(int i = 0; i < widths.length; i++) {
         info.setColumnWidthValue(i, widths[i]);
      }
   }

   static VSTableLens lens(TableDataVSAssembly table, int dataRows) {
      Object[][] data = new Object[dataRows + 1][];
      data[0] = new Object[] { "A", "B", "C" };

      for(int r = 1; r <= dataRows; r++) {
         data[r] = new Object[] { r, r * 2, r * 3 };
      }

      DefaultTableLens data0 = new DefaultTableLens(data);
      // no font is set on this bare fixture; skip wrapping so line-count sizing
      // doesn't need font metrics
      data0.setLineWrap(false);
      VSTableLens lens = new VSTableLens(data0);
      lens.initTableGrid(table.getVSAssemblyInfo());
      return lens;
   }

   static VSExporter exporter(Insets inset, boolean match) {
      VSExporter exporter = mock(VSExporter.class);
      when(exporter.getTableCardInset(any())).thenAnswer(i -> (Insets) inset.clone());
      when(exporter.isMatchLayout()).thenReturn(match);
      return exporter;
   }

   static final Insets NONE = new Insets(0, 0, 0, 0);
   static final Insets COMFORTABLE = new Insets(16, 16, 16, 16);

   private TableExportFixtures() {
   }
}
