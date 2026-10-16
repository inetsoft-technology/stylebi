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

import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78677: a date comparison whose dynamic values ($(Spinner), =expr) resolve to an invalid
 * combination used to be erased from the assembly (its authored info and share link nulled), so
 * the comparison never came back when the input became valid again. The comparison must be kept,
 * just not applied, while it is invalid.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonInvalidRecoveryTest {
   /** Dynamic period level + a fixed year-to-date interval: invalid while the level is month. */
   static DateComparisonInfo dc(int resolvedLevel) {
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevelValue("$(Spinner)");
      periods.setDateLevel(resolvedLevel);
      periods.setPreCountValue("1");

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevelValue(String.valueOf(DateComparisonInfo.YEAR_TO_DATE));
      interval.setEndDayAsToDate(true);

      DateComparisonInfo info = new DateComparisonInfo();
      info.setDateComparisonPeriods(periods);
      info.setDateComparisonInterval(interval);
      return info;
   }

   static void resolveTo(DateComparisonInfo info, int level) {
      ((StandardPeriods) info.getPeriods()).setDateLevel(level);
   }

   @Test
   void theSetupIsInvalidAtMonthAndValidAtYear() {
      assertTrue(dc(XConstants.MONTH_DATE_GROUP).invalid());
      assertFalse(dc(XConstants.YEAR_DATE_GROUP).invalid());
   }

   @Test
   void chartKeepsItsDateComparisonWhileInvalidAndRecovers() {
      DateComparisonInfo dcInfo = dc(XConstants.MONTH_DATE_GROUP);
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setDateComparisonInfo(dcInfo);
      Viewsheet vs = mock(Viewsheet.class);

      assertNull(DateComparisonUtil.resolveDateComparisonForUpdate(info, vs));
      assertSame(dcInfo, info.getDateComparisonInfo(),
                 "an invalid dynamic comparison must stay authored on the assembly");
      assertEquals("$(Spinner)",
                   ((StandardPeriods) info.getDateComparisonInfo().getPeriods())
                      .getDateLevelValue());

      resolveTo(dcInfo, XConstants.YEAR_DATE_GROUP);
      assertSame(dcInfo, DateComparisonUtil.resolveDateComparisonForUpdate(info, vs),
                 "the comparison applies again once the input is valid");
   }

   @Test
   void crosstabKeepsItsDateComparisonWhileInvalidAndRecovers() {
      DateComparisonInfo dcInfo = dc(XConstants.MONTH_DATE_GROUP);
      CrosstabVSAssemblyInfo info = new CrosstabVSAssemblyInfo();
      info.setDateComparisonInfo(dcInfo);
      Viewsheet vs = mock(Viewsheet.class);

      assertNull(DateComparisonUtil.resolveDateComparisonForUpdate(info, vs));
      assertSame(dcInfo, info.getDateComparisonInfo());

      resolveTo(dcInfo, XConstants.YEAR_DATE_GROUP);
      assertSame(dcInfo, DateComparisonUtil.resolveDateComparisonForUpdate(info, vs));
   }

   @Test
   void aSharerKeepsItsShareLinkWhileTheSourceIsInvalidAndRecovers() {
      DateComparisonInfo sourceDc = dc(XConstants.MONTH_DATE_GROUP);
      ChartVSAssemblyInfo sourceInfo = new ChartVSAssemblyInfo();
      sourceInfo.setDateComparisonInfo(sourceDc);
      ChartVSAssembly source = mock(ChartVSAssembly.class);
      when(source.getVSAssemblyInfo()).thenReturn(sourceInfo);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Source")).thenReturn(source);

      ChartVSAssemblyInfo sharer = new ChartVSAssemblyInfo();
      sharer.setComparisonShareFrom("Source");

      assertNull(DateComparisonUtil.resolveDateComparisonForUpdate(sharer, vs));
      assertEquals("Source", sharer.getComparisonShareFrom(),
                   "the share link must survive a source that is momentarily invalid");
      assertSame(sourceDc, sourceInfo.getDateComparisonInfo());

      resolveTo(sourceDc, XConstants.YEAR_DATE_GROUP);
      assertSame(sourceDc, DateComparisonUtil.resolveDateComparisonForUpdate(sharer, vs));
   }

   @Test
   void aSharerWhoseSourceIsGoneStillLosesItsShareLink() {
      Viewsheet vs = mock(Viewsheet.class);
      ChartVSAssemblyInfo sharer = new ChartVSAssemblyInfo();
      sharer.setComparisonShareFrom("Gone");

      assertNull(DateComparisonUtil.resolveDateComparisonForUpdate(sharer, vs));
      assertNull(sharer.getComparisonShareFrom());
   }

   @Test
   void anAssemblyWithNoDateComparisonIsUnchanged() {
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();

      assertNull(DateComparisonUtil.resolveDateComparisonForUpdate(info, mock(Viewsheet.class)));
      assertNull(info.getDateComparisonInfo());
   }

   /** Consumers that mean "applied" call getDateComparison(), which must not hand out an invalid dc. */
   @Test
   void getDateComparisonHidesAnInvalidComparisonButTheDesignAccessorDoesNot() {
      DateComparisonInfo dcInfo = dc(XConstants.MONTH_DATE_GROUP);
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setDateComparisonInfo(dcInfo);
      Viewsheet vs = mock(Viewsheet.class);

      assertNull(DateComparisonUtil.getDateComparison(info, vs));
      assertSame(dcInfo, DateComparisonUtil.getDesignDateComparison(info, vs));

      resolveTo(dcInfo, XConstants.YEAR_DATE_GROUP);
      assertSame(dcInfo, DateComparisonUtil.getDateComparison(info, vs));
   }

   @Test
   void theDynamicDependencyOfAnInvalidComparisonIsKept() {
      DateComparisonInfo dcInfo = dc(XConstants.MONTH_DATE_GROUP);
      dcInfo.setComparisonOption(DateComparisonInfo.VALUE);
      CrosstabVSAssemblyInfo info = new CrosstabVSAssemblyInfo();
      info.setDateComparisonInfo(dcInfo);
      info.setViewsheet(mock(Viewsheet.class));

      assertTrue(info.getDynamicValues().stream()
                    .anyMatch(v -> "$(Spinner)".equals(v.getDValue())),
                 "the Spinner must stay a dependency so a change re-evaluates the comparison");
   }

   @Test
   void anInvalidComparisonIsDefinedButNotApplicable() {
      DateComparisonInfo dcInfo = dc(XConstants.MONTH_DATE_GROUP);
      dcInfo.setComparisonOption(1);
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setDateComparisonInfo(dcInfo);
      Viewsheet vs = mock(Viewsheet.class);
      info.setViewsheet(vs);

      assertTrue(DateComparisonUtil.isDateComparisonDefined(info));
      assertFalse(DateComparisonUtil.isDateComparisonApplicable(info));

      resolveTo(dcInfo, XConstants.YEAR_DATE_GROUP);
      assertTrue(DateComparisonUtil.isDateComparisonApplicable(info));
   }
}
