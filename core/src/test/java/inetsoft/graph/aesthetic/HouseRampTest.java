package inetsoft.graph.aesthetic;

import inetsoft.graph.rgb.AbstractSplineColorFrame;
import inetsoft.uql.viewsheet.graph.aesthetic.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.*;

import static inetsoft.graph.aesthetic.ChartRampDerivation.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class HouseRampTest {
   @Test
   void authoredStopsAgreeWithTheDerivationRule() throws Exception {
      assertEquals(joined(derive(AMBER_SOURCE, Kind.SEQUENTIAL)), rampOf(AmberColorFrame.class));
      assertEquals(joined(derive(TEAL_SOURCE, Kind.SEQUENTIAL)), rampOf(TealColorFrame.class));
      assertEquals(joined(derive(VARIANCE_SOURCE, Kind.DIVERGING)), rampOf(VarianceColorFrame.class));
   }

   @Test
   void eachRampDeclaresExactlySevenStops() throws Exception {
      for(String ramp : new String[] { rampOf(AmberColorFrame.class),
                                       rampOf(TealColorFrame.class),
                                       rampOf(VarianceColorFrame.class) })
      {
         assertEquals(42, ramp.length(), "seven six-digit stops");
      }
   }

   @Test
   void eachFrameSurvivesTheWrapperRoundTrip() throws Exception {
      assertRoundTrips(new AmberColorFrame(), AmberColorFrameWrapper.class);
      assertRoundTrips(new TealColorFrame(), TealColorFrameWrapper.class);
      assertRoundTrips(new VarianceColorFrame(), VarianceColorFrameWrapper.class);
   }

   private void assertRoundTrips(LinearColorFrame frame, Class<?> expected) throws Exception {
      VisualFrameWrapper wrapper = VisualFrameWrapper.wrap(frame);
      assertSame(expected, wrapper.getClass(), "wrap() must resolve the declared switch case");

      StringWriter buf = new StringWriter();
      wrapper.writeXML(new PrintWriter(buf));

      Document doc = Tool.parseXML(new StringReader(buf.toString()));
      VisualFrameWrapper parsed = VisualFrameWrapper.createVisualFrame(doc.getDocumentElement());

      assertSame(expected, parsed.getClass(), "parse must resolve the same wrapper");
      assertEquals(frame.getClass(), parsed.getVisualFrame().getClass());
   }

   /**
    * getColorRamps() is protected, and the house frames live in a different package from this test's
    * AbstractSplineColorFrame, so read the table reflectively rather than widening production access
    * for a test.
    */
   private static String rampOf(Class<? extends AbstractSplineColorFrame> cls) throws Exception {
      java.lang.reflect.Method m =
         AbstractSplineColorFrame.class.getDeclaredMethod("getColorRamps");
      m.setAccessible(true);

      String[] ramps = (String[]) m.invoke(cls.getDeclaredConstructor().newInstance());
      assertEquals(1, ramps.length, "a house ramp declares exactly one stop table");

      return ramps[0];
   }
}
