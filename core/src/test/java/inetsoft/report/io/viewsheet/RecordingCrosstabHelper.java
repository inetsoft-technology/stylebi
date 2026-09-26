package inetsoft.report.io.viewsheet;

import inetsoft.report.Hyperlink;
import inetsoft.report.composition.VSTableLens;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;

import java.awt.*;
import java.awt.geom.Rectangle2D;

/**
 * A crosstab helper that draws nothing, for the crosstab's own geometry.
 */
class RecordingCrosstabHelper extends VSCrosstabHelper {
   RecordingCrosstabHelper(TableDataVSAssembly table, VSExporter exporter) {
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
   }

   @Override
   protected void drawObjectFormat(TableDataVSAssemblyInfo info, VSTableLens lens,
                                   boolean borderOnly)
   {
   }
}
