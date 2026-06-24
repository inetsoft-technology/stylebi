package inetsoft.web.wiz.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@Tag("core")
class WizVsServiceRemoveVisualizationTest {
   @Test
   void removesAssemblyAndResetsSandboxWhenPresent() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      VSAssembly assembly = mock(VSAssembly.class);

      when(vsService.getViewsheet("rt-1", null)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly("Chart1")).thenReturn(assembly);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      WizVsService service = new WizVsService(vsService, engine);
      service.removeVisualization("rt-1", "Chart1", null);

      verify(vs).removeAssembly("Chart1");
      verify(box).resetDataMap("Chart1");
   }

   @Test
   void idempotentWhenAssemblyMissing() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);

      when(vsService.getViewsheet("rt-1", null)).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly("Chart1")).thenReturn(null);

      WizVsService service = new WizVsService(vsService, engine);
      service.removeVisualization("rt-1", "Chart1", null);

      verify(vs, never()).removeAssembly(anyString());
   }

   @Test
   void idempotentWhenRuntimeUnavailable() throws Exception {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);

      when(vsService.getViewsheet("rt-1", null)).thenThrow(new RuntimeException("expired"));

      WizVsService service = new WizVsService(vsService, engine);
      service.removeVisualization("rt-1", "Chart1", null); // must not throw
   }

   @Test
   void throwsWhenArgsBlank() {
      ViewsheetService vsService = mock(ViewsheetService.class);
      AssetRepository engine = mock(AssetRepository.class);
      WizVsService service = new WizVsService(vsService, engine);

      assertThrows(IllegalArgumentException.class,
                   () -> service.removeVisualization("", "Chart1", null));
      assertThrows(IllegalArgumentException.class,
                   () -> service.removeVisualization("rt-1", "", null));
   }
}
