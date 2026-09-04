package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.uql.viewsheet.BindableVSAssembly;
import inetsoft.uql.viewsheet.DataVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
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
 * a worksheet table name was expected). Gates on {@code BindableVSAssembly} (not
 * {@code DataVSAssembly}) so both Data assemblies (Chart/Table/Crosstab) and Output assemblies
 * (Text/Gauge) resolve via their shared {@code getTableName()} accessor (bug 76368).
 */
@Tag("core")
class WizBrowseDataServiceTest {
   @Test
   void resolvesWorksheetTableNameFromDataAssembly() {
      ViewsheetService vsService = mock(ViewsheetService.class);
      Viewsheet vs = mock(Viewsheet.class);
      DataVSAssembly chartAssembly = mock(DataVSAssembly.class);

      when(vs.getAssembly("Chart1")).thenReturn(chartAssembly);
      when(chartAssembly.getTableName()).thenReturn("JOIN_SO_SOL");

      WizBrowseDataService service = new WizBrowseDataService(vsService);

      assertEquals("JOIN_SO_SOL", service.resolveWorksheetTableName(vs, "Chart1"));
   }

   @Test
   void resolvesWorksheetTableNameFromOutputAssembly() {
      ViewsheetService vsService = mock(ViewsheetService.class);
      Viewsheet vs = mock(Viewsheet.class);
      TextVSAssembly textAssembly = mock(TextVSAssembly.class);

      when(vs.getAssembly("Text1")).thenReturn(textAssembly);
      when(textAssembly.getTableName()).thenReturn("JOIN_SO_SOL");

      WizBrowseDataService service = new WizBrowseDataService(vsService);

      assertEquals("JOIN_SO_SOL", service.resolveWorksheetTableName(vs, "Text1"));
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
   void throwsWhenChartAssemblyIsNotBindable() {
      ViewsheetService vsService = mock(ViewsheetService.class);
      Viewsheet vs = mock(Viewsheet.class);
      VSAssembly nonBindableAssembly = mock(VSAssembly.class);
      when(vs.getAssembly("Chart1")).thenReturn(nonBindableAssembly);

      WizBrowseDataService service = new WizBrowseDataService(vsService);

      assertThrows(IllegalArgumentException.class,
                   () -> service.resolveWorksheetTableName(vs, "Chart1"));
   }

   @Test
   void throwsWhenBindableAssemblyHasNoTableName() {
      ViewsheetService vsService = mock(ViewsheetService.class);
      Viewsheet vs = mock(Viewsheet.class);
      BindableVSAssembly chartAssembly = mock(BindableVSAssembly.class);
      when(vs.getAssembly("Chart1")).thenReturn(chartAssembly);
      when(chartAssembly.getTableName()).thenReturn(null);

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
