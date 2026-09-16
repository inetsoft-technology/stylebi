package inetsoft.report.composition.graph;

import inetsoft.graph.aesthetic.CompositeColorFrame;
import inetsoft.graph.aesthetic.StaticColorFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.filter.Highlight;
import inetsoft.report.filter.TextHighlight;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class BrushedMarksTest {
   @Test
   void aRowMatchingTheBrushConditionIsBrushed() {
      assertTrue(BrushedMarks.isBrushed(sourceComposite(0), data(), 0));
   }

   @Test
   void aRowNotMatchingTheBrushConditionIsNotBrushed() {
      assertFalse(BrushedMarks.isBrushed(sourceComposite(0), data(), 1));
   }

   @Test
   void aCompositeWithNoHighlightFrameIsNotBrushed() {
      CompositeColorFrame frame = new CompositeColorFrame();
      frame.addFrame(new StaticColorFrame(Color.BLUE));
      assertFalse(BrushedMarks.isBrushed(frame, data(), 0));
   }

   @Test
   void aNonCompositeFrameIsNotBrushed() {
      assertFalse(BrushedMarks.isBrushed(new StaticColorFrame(Color.BLUE), data(), 0));
   }

   @Test
   void aNullFrameOrDataIsNotBrushed() {
      assertFalse(BrushedMarks.isBrushed(null, data(), 0));
      assertFalse(BrushedMarks.isBrushed(sourceComposite(0), null, 0));
   }

   @Test
   void aNullGeometryIsNotBrushed() {
      assertFalse(BrushedMarks.isBrushed(null));
   }

   @Test
   void brushedMarksSortAfterUnbrushedOnes() {
      assertEquals(1, BrushedMarks.order(true, false));
      assertEquals(-1, BrushedMarks.order(false, true));
      assertEquals(0, BrushedMarks.order(true, true));
      assertEquals(0, BrushedMarks.order(false, false));
   }

   private DataSet data() {
      return new DefaultDataSet(new Object[][] {
         { "Region", "Sales" },
         { "East", 100.0 },
         { "West", 5.0 }
      });
   }

   // the composite a brushing-source chart carries: HLColorFrame over the real palette.
   // getHighlight is stubbed so the test does not depend on the condition-building API.
   private CompositeColorFrame sourceComposite(int brushedRow) {
      HLColorFrame hframe = new HLColorFrame(null, null, null) {
         @Override
         public Highlight getHighlight(DataSet data, int row) {
            return row == brushedRow ? new TextHighlight() : null;
         }
      };

      CompositeColorFrame composite = new CompositeColorFrame();
      composite.addFrame(hframe);
      composite.addFrame(new StaticColorFrame(Color.BLUE));
      return composite;
   }
}
