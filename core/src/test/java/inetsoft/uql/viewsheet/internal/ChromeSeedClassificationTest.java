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

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every assembly type is classified correctly by the three predicates that decide which chrome the
 * base seed hook writes: isCornerSeedTarget(), bypassesBaseChrome() and installsOwnTitleFormat().
 *
 * Those are hand-maintained instanceof lists, and a type that writes its own round corner or border
 * colours after super, or installs its own title composite, has to be on the right one. Nothing in
 * the compiler enforces it, so a newly added subtype is silently misclassified until somebody
 * notices the chrome is wrong.
 *
 * The invariant they exist to protect is stated in their own Javadoc: a modernized assembly must
 * look like a freshly created one, and a reverted assembly like one created with the gate closed.
 * That is what this asserts, over every concrete VSAssemblyInfo found by scanning the package rather
 * than a list written here - so a new type joins this test the moment it is added, and fails it if
 * it is left out of the predicate it belongs to.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChromeSeedClassificationTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   /**
    * Every concrete VSAssemblyInfo in the package, found by scanning so the set cannot go stale.
    */
   private static List<Class<?>> infoTypes() throws Exception {
      ClassPathScanningCandidateComponentProvider scanner =
         new ClassPathScanningCandidateComponentProvider(false)
         {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
               return definition.getMetadata().isIndependent() &&
                  !definition.getMetadata().isAbstract();
            }
         };

      scanner.addIncludeFilter(new AssignableTypeFilter(VSAssemblyInfo.class));
      List<Class<?>> types = new ArrayList<>();

      for(BeanDefinition definition :
          scanner.findCandidateComponents("inetsoft.uql.viewsheet.internal"))
      {
         types.add(Class.forName(definition.getBeanClassName()));
      }

      types.sort(Comparator.comparing(Class::getName));
      return types;
   }

   /**
    * Creation as production does it: the mark is stamped before initDefaultFormat, so the creation
    * seeds resolve against the assembly's own mark and not the gate. AbstractVSAssembly's two-arg
    * constructor (which copies the host sheet's mark) and ViewsheetVSAssemblyInfo's both do this.
    */
   private static VSAssemblyInfo create(Class<?> type, VizMark mark) throws Exception {
      VSAssemblyInfo info = (VSAssemblyInfo) type.getDeclaredConstructor().newInstance();
      info.setVizMark(mark);
      info.initDefaultFormat();
      return info;
   }

   private static VSFormat defaultFormat(VSAssemblyInfo info, TableDataPathKey key) {
      VSCompositeFormat composite = info.getFormatInfo() == null
         ? null : info.getFormatInfo().getFormat(key.path());

      return composite == null ? null : composite.getDefaultFormat();
   }

   /**
    * The values the base hook writes, and only those - the predicates decide nothing else.
    */
   private static String chrome(VSAssemblyInfo info) {
      VSFormat object = defaultFormat(info, TableDataPathKey.OBJECT);
      VSFormat title = defaultFormat(info, TableDataPathKey.TITLE);

      return "object.roundCorner=" + (object == null ? null : object.getRoundCornerValue()) +
         " object.borderColors=" + (object == null ? null : object.getBorderColorsValue()) +
         " title.borders=" + (title == null ? null : title.getBordersValue()) +
         " title.borderColors=" + (title == null ? null : title.getBorderColorsValue());
   }

   @Test
   void everyTypeIsFoundByTheScan() throws Exception {
      List<Class<?>> types = infoTypes();
      assertTrue(types.size() > 30,
                 "the scan must actually reach the package; found " + types.size());
      assertTrue(types.contains(ChartVSAssemblyInfo.class), "chart is a concrete info type");
      assertTrue(types.contains(TableVSAssemblyInfo.class), "table is a concrete info type");
      assertTrue(types.contains(CalendarVSAssemblyInfo.class),
                 "calendar reaches the hook by a different route and must not be missed");
   }

   @Test
   void aModernizedAssemblyEqualsAFreshlyCreatedOneForEveryType() throws Exception {
      for(Class<?> type : infoTypes()) {
         gateOn();
         String fresh = chrome(create(type, VizMark.fromGate()));

         gateOff();
         VSAssemblyInfo modernized = create(type, null);
         gateOn();
         modernized.setVizMark(VizMark.fromGate());
         modernized.seedChromeDefaults(VizContext.of(modernized));

         assertEquals(fresh, chrome(modernized),
                      type.getSimpleName() + " is misclassified by isCornerSeedTarget(), " +
                      "bypassesBaseChrome() or installsOwnTitleFormat(): modernizing it does not " +
                      "produce what creating it under an open gate produces");
      }
   }

   @Test
   void aRevertedAssemblyEqualsOneCreatedUnderAClosedGateForEveryType() throws Exception {
      for(Class<?> type : infoTypes()) {
         gateOff();
         String fresh = chrome(create(type, null));

         gateOn();
         VSAssemblyInfo reverted = create(type, VizMark.fromGate());
         reverted.setVizMark(null);
         reverted.seedChromeDefaults(VizContext.of((VizMark) null));

         assertEquals(fresh, chrome(reverted),
                      type.getSimpleName() + " is misclassified: reverting it does not produce " +
                      "what creating it under a closed gate produces");
      }
   }

   @Test
   void theHookIsIdempotentForEveryType() throws Exception {
      for(Class<?> type : infoTypes()) {
         gateOn();
         VSAssemblyInfo info = create(type, VizMark.fromGate());
         info.seedChromeDefaults(VizContext.of(info));
         String once = chrome(info);
         info.seedChromeDefaults(VizContext.of(info));

         assertEquals(once, chrome(info),
                      type.getSimpleName() + ": re-seeding must not change what one seed wrote");
      }
   }

   /**
    * The two paths the predicates write, named rather than repeated at each call site.
    */
   private enum TableDataPathKey {
      OBJECT(VSAssemblyInfo.OBJECTPATH),
      TITLE(VSAssemblyInfo.TITLEPATH);

      private final inetsoft.report.TableDataPath path;

      TableDataPathKey(inetsoft.report.TableDataPath path) {
         this.path = path;
      }

      inetsoft.report.TableDataPath path() {
         return path;
      }
   }
}
