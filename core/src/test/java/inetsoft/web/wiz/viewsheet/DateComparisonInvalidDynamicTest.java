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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.composer.model.vs.DateComparisonDialogModel;
import inetsoft.web.composer.model.vs.DateComparisonPaneModel;
import inetsoft.web.composer.vs.dialog.DateComparisonDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78677: a dynamic date comparison value that resolves to an invalid combination was
 * reported as "no date-typed field is bound to this chart's x or y axis" (false) and read back as a
 * bare {@code {enabled:false}}. Needs a Spring context because {@code DateComparisonInfo.invalid()}
 * resolves dynamic values, which {@code DateComparisonServiceTest} deliberately avoids.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonInvalidDynamicTest {
   private static DateComparisonInfo spinnerComparison(int resolvedLevel) {
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

   private static DateComparisonInfo customComparison(String startValue, Date start, Date end) {
      DatePeriod period = new DatePeriod();
      period.setStartValue(startValue);
      period.setStart(start);
      period.setEnd(end);
      CustomPeriods periods = new CustomPeriods();
      periods.setDatePeriods(List.of(period));

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevelValue(String.valueOf(DateComparisonInfo.ALL));
      interval.setEndDayAsToDate(true);

      DateComparisonInfo info = new DateComparisonInfo();
      info.setDateComparisonPeriods(periods);
      info.setDateComparisonInterval(interval);
      return info;
   }

   @Test
   void aLevelFinerThanTheIntervalNamesBothFieldsAndTheDynamicSource() {
      String cause = DateComparisonService.describeInvalidComparison(
         spinnerComparison(XConstants.MONTH_DATE_GROUP));

      assertNotNull(cause);
      assertTrue(cause.contains("coarser than"), cause);
      assertTrue(cause.contains("'interval'") && cause.contains("'level'"), cause);
      assertTrue(cause.contains("yearToDate"), cause);
      assertTrue(cause.contains("$(Spinner)"), cause);
      assertFalse(cause.contains("date-typed field"), cause);
   }

   @Test
   void aValidComparisonHasNoInvalidCause() {
      assertNull(DateComparisonService.describeInvalidComparison(
         spinnerComparison(XConstants.YEAR_DATE_GROUP)));
      assertNull(DateComparisonService.describeInvalidComparison(null));
   }

   @Test
   void anEmptyCustomPeriodStartNamesTheStartAndTheInput() {
      String cause = DateComparisonService.describeInvalidComparison(
         customComparison("$(TextInput1)", null, new Date()));

      assertNotNull(cause);
      assertTrue(cause.contains("customPeriods[0].start"), cause);
      assertTrue(cause.contains("$(TextInput1)"), cause);
   }

   @Test
   void aStartAfterTheEndIsNamed() {
      Date end = new Date();
      String cause = DateComparisonService.describeInvalidComparison(
         customComparison("2030-01-01", new Date(end.getTime() + 86400000L), end));

      assertNotNull(cause);
      assertTrue(cause.contains("start after its end"), cause);
   }

   @Test
   void aMissingIntervalEndAnchorNamesTheIntervalEndDate() {
      DateComparisonInfo info = spinnerComparison(XConstants.YEAR_DATE_GROUP);
      info.getInterval().setEndDayAsToDate(false);

      String cause = DateComparisonService.describeInvalidComparison(info);

      assertNotNull(cause);
      assertTrue(cause.contains("intervalEndDate"), cause);
   }

   @Test
   void theChartInactiveReasonIsTheRealCauseNotTheMissingDateField() {
      Fixture f = fixture(true, spinnerComparison(XConstants.MONTH_DATE_GROUP));

      Map<String, Object> out =
         DateComparisonService.describeDateComparisonInactive(f.rvs, "Chart1");

      assertEquals(Boolean.TRUE, out.get("dateComparisonInactive"));
      String reason = (String) out.get("reason");
      assertTrue(reason.contains("coarser than"), reason);
      assertTrue(reason.contains("saved"), reason);
      assertFalse(reason.contains("date-typed field"), reason);
   }

   @Test
   void theCrosstabInactiveReasonIsTheRealCauseNotTheMissingDateField() {
      Fixture f = fixture(false, spinnerComparison(XConstants.MONTH_DATE_GROUP));

      Map<String, Object> out =
         DateComparisonService.describeDateComparisonInactive(f.rvs, "Crosstab1");

      assertEquals(Boolean.TRUE, out.get("dateComparisonInactive"));
      String reason = (String) out.get("reason");
      assertTrue(reason.contains("coarser than"), reason);
      assertFalse(reason.contains("date-typed field"), reason);
   }

   @Test
   void aValidButUnboundComparisonKeepsTheExistingNoDateFieldReason() {
      Fixture f = fixture(true, spinnerComparison(XConstants.YEAR_DATE_GROUP));

      Map<String, Object> out =
         DateComparisonService.describeDateComparisonInactive(f.rvs, "Chart1");

      assertTrue(((String) out.get("reason")).contains("date-typed field"), out.toString());
   }

   @SuppressWarnings("unchecked")
   @Test
   void readReturnsTheStoredSettingsAndTheCauseWhileTheComparisonIsInvalid() throws Exception {
      DateComparisonInfo dcInfo = spinnerComparison(XConstants.MONTH_DATE_GROUP);
      dcInfo.setComparisonOption(1);
      Fixture f = fixture(true, dcInfo);

      Map<String, Object> read = f.service.read("tok", () -> "admin", "Chart1");

      assertEquals(Boolean.FALSE, read.get("enabled"));
      assertTrue(((String) read.get("reason")).contains("coarser than"), read.toString());
      assertEquals("$(Spinner)", ((Map<String, Object>) read.get("period")).get("level"));
      assertEquals("yearToDate", ((Map<String, Object>) read.get("interval")).get("level"));
   }

   @Test
   void readOfAnAssemblyWithNoComparisonIsStillABareDisabledResult() throws Exception {
      Fixture f = fixture(true, null);
      when(f.comparisons.isDateComparisonEnabled(anyString(), anyString(), any(Principal.class)))
         .thenReturn(false);

      Map<String, Object> read = f.service.read("tok", () -> "admin", "Chart1");

      assertEquals(Map.of("assembly", "Chart1", "enabled", false), read);
   }

   private record Fixture(DateComparisonService service, RuntimeViewsheet rvs,
                          DateComparisonDialogService comparisons) {}

   private static Fixture fixture(boolean chart, DateComparisonInfo dcInfo) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt1");

      if(chart) {
         ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
         info.setDateComparisonInfo(dcInfo);
         ChartVSAssembly assembly = mock(ChartVSAssembly.class);
         when(assembly.getVSAssemblyInfo()).thenReturn(info);
         when(assembly.getVSChartInfo()).thenReturn(mock(VSChartInfo.class));
         when(vs.getAssembly("Chart1")).thenReturn(assembly);
      }
      else {
         CrosstabVSAssemblyInfo info = new CrosstabVSAssemblyInfo();
         info.setDateComparisonInfo(dcInfo);
         CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
         when(assembly.getVSAssemblyInfo()).thenReturn(info);
         when(assembly.getVSCrosstabInfo()).thenReturn(mock(VSCrosstabInfo.class));
         when(vs.getAssembly("Crosstab1")).thenReturn(assembly);
      }

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      DateComparisonDialogService comparisons = mock(DateComparisonDialogService.class);

      DateComparisonPaneModel paneModel =
         dcInfo == null ? new DateComparisonPaneModel() : new DateComparisonPaneModel(dcInfo);
      DateComparisonDialogModel shareModel = new DateComparisonDialogModel();

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         when(comparisons.getDateComparison(anyString(), anyString(), any(Principal.class)))
            .thenReturn(paneModel);
         when(comparisons.isDateComparisonEnabled(anyString(), anyString(), any(Principal.class)))
            .thenReturn(true);
         when(comparisons.getShare(anyString(), anyString(), any(Principal.class)))
            .thenReturn(shareModel);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new Fixture(new DateComparisonService(sessions, comparisons), rvs, comparisons);
   }
}
