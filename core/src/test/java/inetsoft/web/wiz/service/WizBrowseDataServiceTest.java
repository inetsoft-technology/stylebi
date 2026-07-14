package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.DataVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for {@link WizBrowseDataService#resolveWorksheetTableName}, extracted from
 * the {@code browseData} fix that stopped the endpoint from silently returning an empty value
 * list for every column on every chart (it was passing the VS chart's own presentation name where
 * a worksheet table name was expected).
 */
@Tag("core")
class WizBrowseDataServiceTest {
   @Test
   void resolvesWorksheetTableNameFromChartSourceInfo() {
      ViewsheetService vsService = mock(ViewsheetService.class);
      Viewsheet vs = mock(Viewsheet.class);
      DataVSAssembly chartAssembly = mock(DataVSAssembly.class);
      SourceInfo sourceInfo = mock(SourceInfo.class);

      when(vs.getAssembly("Chart1")).thenReturn(chartAssembly);
      when(chartAssembly.getSourceInfo()).thenReturn(sourceInfo);
      when(sourceInfo.getSource()).thenReturn("JOIN_SO_SOL");

      WizBrowseDataService service = new WizBrowseDataService(vsService);

      assertEquals("JOIN_SO_SOL", service.resolveWorksheetTableName(vs, "Chart1"));
   }

   @Test
   void throwsWhenChartAssemblyMissing() {
      ViewsheetService vsService = mock(ViewsheetService.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Chart1")).thenReturn(null);

      WizBrowseDataService service = new WizBrowseDataService(vsService);

      assertThrows(IllegalArgumentException.class,
                   () -> service.resolveWorksheetTableName(vs, "Chart1"));
   }

   @Test
   void throwsWhenChartAssemblyIsNotADataVSAssembly() {
      ViewsheetService vsService = mock(ViewsheetService.class);
      Viewsheet vs = mock(Viewsheet.class);
      VSAssembly nonDataAssembly = mock(VSAssembly.class);
      when(vs.getAssembly("Chart1")).thenReturn(nonDataAssembly);

      WizBrowseDataService service = new WizBrowseDataService(vsService);

      assertThrows(IllegalArgumentException.class,
                   () -> service.resolveWorksheetTableName(vs, "Chart1"));
   }

   @Test
   void throwsWhenSourceInfoMissing() {
      ViewsheetService vsService = mock(ViewsheetService.class);
      Viewsheet vs = mock(Viewsheet.class);
      DataVSAssembly chartAssembly = mock(DataVSAssembly.class);
      when(vs.getAssembly("Chart1")).thenReturn(chartAssembly);
      when(chartAssembly.getSourceInfo()).thenReturn(null);

      WizBrowseDataService service = new WizBrowseDataService(vsService);

      assertThrows(IllegalStateException.class,
                   () -> service.resolveWorksheetTableName(vs, "Chart1"));
   }

   @Test
   void throwsWhenSourceInfoHasNoSource() {
      ViewsheetService vsService = mock(ViewsheetService.class);
      Viewsheet vs = mock(Viewsheet.class);
      DataVSAssembly chartAssembly = mock(DataVSAssembly.class);
      SourceInfo sourceInfo = mock(SourceInfo.class);
      when(vs.getAssembly("Chart1")).thenReturn(chartAssembly);
      when(chartAssembly.getSourceInfo()).thenReturn(sourceInfo);
      when(sourceInfo.getSource()).thenReturn(null);

      WizBrowseDataService service = new WizBrowseDataService(vsService);

      assertThrows(IllegalStateException.class,
                   () -> service.resolveWorksheetTableName(vs, "Chart1"));
   }

   @Test
   void throwsWhenViewsheetIsNull() {
      ViewsheetService vsService = mock(ViewsheetService.class);
      WizBrowseDataService service = new WizBrowseDataService(vsService);

      assertThrows(IllegalArgumentException.class,
                   () -> service.resolveWorksheetTableName(null, "Chart1"));
   }
}
