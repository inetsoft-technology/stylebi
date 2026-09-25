package inetsoft.report.composition;

import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The lens's cached last column is filled to the card; inside a grid 32px narrower the fill
 * gives back up to 32px, never below the column's own width.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSTableLensColumnWidthInGridTest {
   // 100 + 100 + the 100px default = 300, so the lens fills the last column by 100 to 200
   @Test
   void aWidthlessLastColumnGivesBackTheInset() {
      Fixture f = fixture(100, 100, Double.NaN);

      assertEquals(168, f.lens.getColumnWidthInGrid(2, f.info, 32));
   }

   // a set 50px last column is filled by 150 to 200; 32 of that is inside the inset
   @Test
   void aSetLastColumnGivesBackTheInset() {
      Fixture f = fixture(100, 100, 50);

      assertEquals(168, f.lens.getColumnWidthInGrid(2, f.info, 32));
   }

   // 150 + 130 + 100 = 380, filled by only 20: all of it comes back, down to the 100px default
   @Test
   void aFillSmallerThanTheInsetComesBackWhole() {
      Fixture f = fixture(150, 130, Double.NaN);

      assertEquals(100, f.lens.getColumnWidthInGrid(2, f.info, 32));
   }

   @Test
   void aHiddenLastColumnStaysHidden() {
      Fixture f = fixture(100, 100, 0);

      assertEquals(0, f.lens.getColumnWidthInGrid(2, f.info, 32));
   }

   @Test
   void anEarlierColumnIsUnchanged() {
      Fixture f = fixture(100, 100, Double.NaN);

      assertEquals(100, f.lens.getColumnWidthInGrid(0, f.info, 32));
   }

   @Test
   void noInsetKeepsTheCardFill() {
      Fixture f = fixture(100, 100, Double.NaN);

      assertEquals(200, f.lens.getColumnWidthInGrid(2, f.info, 0));
   }

   @Test
   void theTakeBackLeavesTheSharedCacheAlone() {
      Fixture f = fixture(100, 100, Double.NaN);
      f.lens.getColumnWidthInGrid(2, f.info, 32);
      f.lens.getColumnWidthInGrid(2, f.info, 32);

      assertArrayEquals(new int[] { 100, 100, 200 }, f.lens.getColumnWidths(),
                        "Excel and HTML read this same cache later in the export");
   }

   private record Fixture(VSTableLens lens, TableVSAssemblyInfo info) {}

   private static Fixture fixture(double... widths) {
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setPixelOffset(new Point(0, 0));
      info.setPixelSize(new Dimension(400, 200));

      for(int i = 0; i < widths.length; i++) {
         info.setColumnWidthValue(i, widths[i]);
      }

      VSTableLens lens = new VSTableLens(new DefaultTableLens(new Object[][] {
         { "A", "B", "C" },
         { 1, 2, 3 }
      }));
      lens.initTableGrid(info);
      return new Fixture(lens, info);
   }
}
