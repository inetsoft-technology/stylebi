package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSChartInteractionDefaultsTest {
   private static final VizContext MODERN = VizContext.of(VizMark.MODERN_LIGHT);
   private static final VizContext CLASSIC = VizContext.of((VizMark) null);

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("graph.svg.inline", null);
   }

   @Test
   void markedChartIsInlineWithGateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      assertTrue(VSChartInteractionDefaults.isInlineSvg(MODERN));
   }

   @Test
   void unmarkedChartIsAnImageWithGateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertFalse(VSChartInteractionDefaults.isInlineSvg(CLASSIC));
   }

   @Test
   void explicitTrueWinsForAnUnmarkedChart() {
      SreeEnv.setProperty("graph.svg.inline", "true");
      assertTrue(VSChartInteractionDefaults.isInlineSvg(CLASSIC));
   }

   @Test
   void explicitFalseWinsForAMarkedChart() {
      SreeEnv.setProperty("graph.svg.inline", "false");
      assertFalse(VSChartInteractionDefaults.isInlineSvg(MODERN));
   }

   @Test
   void emptyValueCountsAsUnset() {
      SreeEnv.setProperty("graph.svg.inline", "");
      assertTrue(VSChartInteractionDefaults.isInlineSvg(MODERN));
      assertFalse(VSChartInteractionDefaults.isInlineSvg(CLASSIC));
   }

   @Test
   void anyNonTrueValueIsOff() {
      SreeEnv.setProperty("graph.svg.inline", "no");
      assertFalse(VSChartInteractionDefaults.isInlineSvg(MODERN));
   }
}
