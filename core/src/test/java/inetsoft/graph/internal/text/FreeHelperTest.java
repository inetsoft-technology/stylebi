/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.graph.internal.text;

import inetsoft.graph.VGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.guide.VLabel;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests how FreeHelper reads graph.textlayout.maxstep. The value is the cap on how far a
 * label may be moved to resolve overlapping, and TextLayoutManager.createHelper bounds the
 * per-coordinate budget by it only when the operator actually set it -- so both the parsed
 * value and isMaxStepsFromProperty() have to be right. (76291)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class FreeHelperTest {
   private static final String PROPERTY = "graph.textlayout.maxstep";

   /**
    * With the property unset, the budget falls back to 1000 in all four directions and is
    * not reported as operator-set, so createHelper leaves the coordinate's budget alone.
    */
   @Test
   void unsetPropertyFallsBackToDefaultAndIsNotFromProperty() {
      SreeEnv.remove(PROPERTY);
      FreeHelper helper = createHelper();

      assertArrayEquals(new int[] {1000, 1000, 1000, 1000}, helper.getMaxSteps());
      assertFalse(helper.isMaxStepsFromProperty(),
                  "an unset property must not be reported as operator-set");
   }

   /**
    * A value the operator set is applied in all four directions and reported so that
    * createHelper bounds the coordinate's budget by it.
    */
   @Test
   void setPropertyIsAppliedAndReportedAsFromProperty() {
      withProperty("5", helper -> {
         assertArrayEquals(new int[] {5, 5, 5, 5}, helper.getMaxSteps());
         assertTrue(helper.isMaxStepsFromProperty());
      });
   }

   /**
    * Properties.load() keeps surrounding whitespace and Integer.parseInt does not trim it.
    */
   @Test
   void surroundingWhitespaceIsTrimmed() {
      withProperty(" 5 ", helper -> {
         assertArrayEquals(new int[] {5, 5, 5, 5}, helper.getMaxSteps());
         assertTrue(helper.isMaxStepsFromProperty());
      });
   }

   /**
    * A malformed value must not throw: the constructor runs once per label, so propagating
    * a NumberFormatException would fail the whole chart render instead of degrading.
    */
   @ParameterizedTest
   @ValueSource(strings = {"abc", "1,000", "1000.5", ""})
   void malformedValueFallsBackWithoutThrowing(String value) {
      withProperty(value, helper -> {
         assertArrayEquals(new int[] {1000, 1000, 1000, 1000}, helper.getMaxSteps());
         assertFalse(helper.isMaxStepsFromProperty(),
                     "a value that could not be used must not be reported as operator-set");
      });
   }

   /**
    * A negative budget would stop every free-moving label, since move() returns as soon as
    * steps[n] > max_steps[n] and steps starts at 0. "-1" reads as "unlimited" elsewhere, so
    * it must not be taken at face value.
    */
   @ParameterizedTest
   @ValueSource(strings = {"-1", "-1000"})
   void negativeValueFallsBackToDefault(String value) {
      withProperty(value, helper -> {
         assertArrayEquals(new int[] {1000, 1000, 1000, 1000}, helper.getMaxSteps());
         assertFalse(helper.isMaxStepsFromProperty());
      });
   }

   /**
    * Zero is left alone: it permits a single step, which is a coherent "barely move"
    * setting rather than a value with the opposite of its apparent effect.
    */
   @Test
   void zeroIsAccepted() {
      withProperty("0", helper -> {
         assertArrayEquals(new int[] {0, 0, 0, 0}, helper.getMaxSteps());
         assertTrue(helper.isMaxStepsFromProperty());
      });
   }

   /**
    * The property is global, so it has to be removed again or it leaks into every other
    * test sharing this JVM.
    */
   private void withProperty(String value, java.util.function.Consumer<FreeHelper> assertions) {
      SreeEnv.setProperty(PROPERTY, value);

      try {
         assertions.accept(createHelper());
      }
      finally {
         SreeEnv.remove(PROPERTY);
      }
   }

   private FreeHelper createHelper() {
      return new FreeHelper(new VLabel("abc"), new VGraph(new RectCoord()));
   }
}
