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
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("graph.svg.inline", null);
   }

   @Test
   void offByDefaultWhenLegacy() {
      assertFalse(VSChartInteractionDefaults.isInlineSvg());
   }

   @Test
   void explicitTrueStillWinsWhenLegacy() {
      SreeEnv.setProperty("graph.svg.inline", "true");
      assertTrue(VSChartInteractionDefaults.isInlineSvg());
   }

   @Test
   void followsModernWhenUnset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      assertTrue(VSChartInteractionDefaults.isInlineSvg());
   }

   @Test
   void explicitFalseOptsOutOfModern() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("graph.svg.inline", "false");
      assertFalse(VSChartInteractionDefaults.isInlineSvg());
   }

   @Test
   void emptyValueCountsAsUnset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("graph.svg.inline", "");
      assertTrue(VSChartInteractionDefaults.isInlineSvg());
   }

   @Test
   void anyNonTrueValueIsOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      SreeEnv.setProperty("graph.svg.inline", "no");
      assertFalse(VSChartInteractionDefaults.isInlineSvg());
   }
}
