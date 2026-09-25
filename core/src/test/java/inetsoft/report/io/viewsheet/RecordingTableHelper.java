package inetsoft.report.io.viewsheet;

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.VSTableLens;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws nothing and records every cell and the title it would have drawn.
 */
class RecordingTableHelper extends VSTableHelper {
   RecordingTableHelper(TableDataVSAssembly table, VSExporter exporter) {
      setViewsheet(table.getViewsheet());
      setExporter(exporter);
      this.assembly = table;
   }

   @Override
   protected void writeTableCell(int startX, int startY, Dimension span,
                                 Rectangle2D pixelbounds, int row, int col,
                                 VSCompositeFormat format, String dispText, Object dispObj,
                                 Hyperlink.Ref hyperlink, VSCompositeFormat parentformat,
                                 Rectangle rec, Insets padding)
   {
      cells.add(new Cell(row, col, pixelbounds, format));
   }

   @Override
   protected void writeTitleCell(int startX, int startY, Dimension span,
                                 Rectangle2D pixelbounds, int row, int col,
                                 VSCompositeFormat format, String dispText, Object dispObj,
                                 Hyperlink.Ref hyperlink, VSCompositeFormat parentformat,
                                 Rectangle rec)
   {
      title = new Rectangle(startX, startY, span.width, span.height);
   }

   @Override
   protected void drawObjectFormat(TableDataVSAssemblyInfo info, VSTableLens lens,
                                   boolean borderOnly)
   {
   }

   Cell cell(int row, int col) {
      return cells.stream().filter(c -> c.row == row && c.col == col).findFirst().orElse(null);
   }

   record Cell(int row, int col, Rectangle2D bounds, VSCompositeFormat format) {}

   final List<Cell> cells = new ArrayList<>();
   Rectangle title;
}
