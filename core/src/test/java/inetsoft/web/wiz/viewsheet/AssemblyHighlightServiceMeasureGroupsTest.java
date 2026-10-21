package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.test.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.vs.HighlightDialogModel;
import inetsoft.web.composer.model.vs.HighlightModel;
import inetsoft.web.composer.model.vs.VSConditionDialogModel;
import inetsoft.web.composer.vs.dialog.HighlightDialogService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.service.HighlightService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
   initializers = ConfigurationContextInitializer.class)
@SreeHome
/**
 * Bug #79114 against the real HighlightDialogService (real read and write of the chart's highlight
 * groups; only the session, the viewsheet and the highlight converters are mocked).
 */
@Tag("core")
class AssemblyHighlightServiceMeasureGroupsTest {
   static final Principal ADMIN = () -> "admin";

   static ColumnRef column(String name, String type) {
      AttributeRef attr = new AttributeRef(name);
      attr.setDataType(type);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(type);
      return column;
   }

   static VSChartDimensionRef dimension(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(column(name, XSchema.STRING));
      dim.setDataType(XSchema.STRING);
      dim.setGroupColumnValue(name);
      return dim;
   }

   static VSChartAggregateRef sum(String name) {
      VSChartAggregateRef m = new VSChartAggregateRef();
      m.setDataRef(column(name, XSchema.DOUBLE));
      m.setColumnValue(name);
      m.setFormula(AggregateFormula.SUM);
      return m;
   }

   static <T extends VSChartInfo> T withRuntime(T info) {
      info.setRTXFields(Arrays.stream(info.getXFields())
                           .map(r -> (ChartRef) r.clone()).toArray(ChartRef[]::new));
      info.setRTYFields(Arrays.stream(info.getYFields())
                           .map(r -> (ChartRef) r.clone()).toArray(ChartRef[]::new));
      return info;
   }

   static VSChartInfo bar(String... measures) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartDescriptor(new ChartDescriptor());
      info.setChartType(GraphTypes.CHART_BAR);
      info.addXField(dimension("REGION"));

      for(String m : measures) {
         info.addYField(sum(m));
      }

      return withRuntime(info);
   }

   static VSChartInfo radar(String measure) {
      VSChartInfo info = new RadarVSChartInfo();
      info.setChartDescriptor(new ChartDescriptor());
      info.setChartType(GraphTypes.CHART_RADAR);
      info.addXField(dimension("REGION"));
      info.addYField(sum(measure));
      return withRuntime(info);
   }

   static class Env {
      final AssemblyHighlightService service;
      final HighlightDialogService hds;
      final ChartVSAssembly chart;

      Env(VSChartInfo info) {
         Viewsheet vs = mock(Viewsheet.class);
         chart = new ChartVSAssembly(vs, "Chart1");
         ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).setVSChartInfo(info);

         when(vs.getAssembly("Chart1")).thenReturn(chart);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.getID()).thenReturn("rt1");
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));

         HighlightService hs = mock(HighlightService.class);
         try {
            when(hs.convertModelToHighlight(any(), any())).thenAnswer(i -> {
               TextHighlight h = new TextHighlight();
               h.setName(((HighlightModel) i.getArgument(0)).getName());
               return h;
            });
            when(hs.convertHighlightToModel(any())).thenAnswer(i -> {
               HighlightModel m = new HighlightModel();
               m.setName(((inetsoft.report.filter.Highlight) i.getArgument(0)).getName());
               m.setVsConditionDialogModel(new VSConditionDialogModel());
               return m;
            });
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }

         VSObjectPropertyService props = mock(VSObjectPropertyService.class, inv -> {
            if(inv.getMethod().getName().equals("editObjectProperty")) {
               chart.setVSAssemblyInfo((VSAssemblyInfo) inv.getArgument(1));
            }

            return null;
         });

         inetsoft.analytic.composition.ViewsheetService vsvc =
            mock(inetsoft.analytic.composition.ViewsheetService.class);
         try {
            when(vsvc.getViewsheet(anyString(), any())).thenReturn(rvs);
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }

         hds = new HighlightDialogService(
            mock(inetsoft.web.binding.service.DataRefModelFactoryService.class), hs, props, vsvc,
            mock(inetsoft.web.composer.vs.objects.controller.VSTrapService.class));

         ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
         try {
            when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
            doAnswer(inv -> {
               ViewsheetSessionService.Mutation m = inv.getArgument(2);
               m.run(rvs, "rt1", null); return null;
            }).when(sessions).mutate(anyString(), any(Principal.class), any());
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }

         service = new AssemblyHighlightService(sessions, hds);
      }

      VSChartInfo info() {
         return ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).getVSChartInfo();
      }

      /** What set_highlight with colName does: the real write path. */
      void seed(String colName, String... names) throws Exception {
         HighlightDialogModel model = new HighlightDialogModel();
         model.setMeasure(colName);
         HighlightModel[] hl = new HighlightModel[names.length];

         for(int i = 0; i < names.length; i++) {
            hl[i] = new HighlightModel();
            hl[i].setName(names[i]);
         }

         model.setHighlights(hl);
         hds.setHighlightDialogModel("rt1", "Chart1", model, "", ADMIN, null);
      }

      String groups() {
         StringBuilder sb = new StringBuilder();
         VSChartInfo info = info();
         sb.append("whole=").append(names(info.getHighlightGroup()));

         for(inetsoft.uql.viewsheet.VSDataRef r : info.getYFields()) {
            sb.append(" ").append(((ChartRef) r).getFullName()).append("=")
               .append(names(((HighlightRef) r).getHighlightGroup()));
         }

         return sb.toString();
      }

      static String names(HighlightGroup g) {
         return g == null ? "[]" : Arrays.toString(g.getNames());
      }

      String tryDelete(AssemblyHighlightService.Region r, String name) {
         try {
            service.delete("tok", ADMIN, "Chart1", r, name, "");
            return "OK";
         }
         catch(Exception e) {
            return "THREW: " + e.getMessage();
         }
      }

      String trySetReplace(AssemblyHighlightService.Region r, String name) {
         try {
            service.set("tok", ADMIN, "Chart1", r,
                        new AssemblyHighlightService.Highlight(name, null, "#ff0000", null,
                           List.of(), false), true, "");
            return "OK";
         }
         catch(Exception e) {
            return "THREW: " + e.getClass().getSimpleName() + ": " + e.getMessage();
         }
      }

      Map<String, Object> list(AssemblyHighlightService.Region r) throws Exception {
         return service.list("tok", ADMIN, "Chart1", r);
      }
   }

   static AssemblyHighlightService.Region col(String c) {
      return new AssemblyHighlightService.Region(null, null, c, false, false);
   }

   static final AssemblyHighlightService.Region NOCOL = AssemblyHighlightService.Region.whole();

   private static void assertHintNamesMeasure(String message, String measure, String name) {
      assertTrue(message.contains("has no highlight named '" + name + "'"), message);
      assertTrue(message.contains(measure + " (" + name), message);
      assertTrue(message.contains("colName"), message);
      assertFalse(message.contains("It has: (none)"), message);
   }

   @Test
   void colNameLessDeleteNamesTheMeasureAndDeletesNothing() throws Exception {
      Env e = new Env(bar("REVENUE"));
      e.seed("Sum(REVENUE)", "HG-Del");

      assertEquals(List.of("HG-Del"),
                   ((List<Map<?, ?>>) e.list(col("Sum(REVENUE)")).get("highlights")).stream()
                      .map(h -> h.get("name")).toList());

      Map<String, Object> whole = e.list(NOCOL);
      assertEquals(List.of(), whole.get("highlights"));
      assertEquals(List.of(), whole.get("usedNames"));
      Map<?, ?> other = (Map<?, ?>) ((List<?>) whole.get("otherGroups")).get(0);
      assertEquals("Sum(REVENUE)", other.get("colName"));
      assertEquals(List.of("HG-Del"), other.get("names"));

      for(AssemblyHighlightService.Region r : new AssemblyHighlightService.Region[]{ NOCOL, null }) {
         String message = e.tryDelete(r, "HG-Del");
         assertTrue(message.startsWith("THREW"), message);
         assertHintNamesMeasure(message, "Sum(REVENUE)", "HG-Del");
      }

      assertEquals("whole=[] Sum(REVENUE)=[HG-Del]", e.groups());
      assertEquals("OK", e.tryDelete(col("Sum(REVENUE)"), "HG-Del"));
      assertEquals("whole=[] Sum(REVENUE)=[]", e.groups());
      assertEquals("THREW: 'Chart1' has no highlight named 'HG-Del'. It has: (none).",
                   e.tryDelete(NOCOL, "HG-Del"));
   }

   @Test
   void twoMeasuresEachHoldingAHighlightAreAllNamedAndOnlyTheNamedOneIsDeleted()
      throws Exception
   {
      Env e = new Env(bar("REVENUE", "COST"));
      e.seed("Sum(REVENUE)", "HR");
      e.seed("Sum(COST)", "HC", "HC2");

      String message = e.tryDelete(NOCOL, "HC");
      assertHintNamesMeasure(message, "Sum(COST)", "HC");
      assertTrue(message.contains("Sum(REVENUE) (HR)"), message);
      assertEquals("whole=[] Sum(REVENUE)=[HR] Sum(COST)=[HC, HC2]", e.groups());

      assertTrue(e.tryDelete(col("Sum(COST)"), "HR").contains("It has: HC, HC2."));
      assertEquals("OK", e.tryDelete(col("Sum(COST)"), "HC"));
      assertEquals("whole=[] Sum(REVENUE)=[HR] Sum(COST)=[HC2]", e.groups());
   }

   @Test
   void aWholeChartHighlightStaysListedAndTheHintOnlyFiresWhenAMeasureHoldsTheName()
      throws Exception
   {
      Env e = new Env(bar("REVENUE"));
      HighlightGroup g = new HighlightGroup();
      TextHighlight t = new TextHighlight();
      t.setName("Whole");
      g.addHighlight("Whole", t);
      e.info().setHighlightGroup(g);
      e.seed("Sum(REVENUE)", "HM");

      assertEquals(List.of("Whole"), e.list(NOCOL).get("usedNames"));
      assertTrue(e.tryDelete(NOCOL, "HM").contains("It has: Whole. The chart has highlights on"));
      assertTrue(e.tryDelete(NOCOL, "Typo").endsWith("It has: Whole."));
      assertEquals("OK", e.tryDelete(NOCOL, "Whole"));
      assertEquals("whole=[] Sum(REVENUE)=[HM]", e.groups());
   }

   /** A merged-info chart keeps measure highlights on the whole chart: old behaviour throughout. */
   @Test
   void aMergedInfoChartKeepsItsWholeChartHighlightsAndTheOldMessage() throws Exception {
      Env e = new Env(radar("REVENUE"));
      e.seed("Sum(REVENUE)", "HG-Merged");

      assertEquals(List.of("HG-Merged"), e.list(NOCOL).get("usedNames"));
      assertFalse(e.list(NOCOL).containsKey("otherGroups"));
      assertEquals("THREW: 'Chart1' has no highlight named 'Typo'. It has: HG-Merged.",
                   e.tryDelete(NOCOL, "Typo"));
      assertEquals("OK", e.tryDelete(NOCOL, "HG-Merged"));
   }

   @Test
   void aChartWithNoHighlightAnywhereKeepsTheOldMessageAndListShape() throws Exception {
      Env e = new Env(bar("REVENUE"));

      assertFalse(e.list(NOCOL).containsKey("otherGroups"));
      assertEquals("THREW: 'Chart1' has no highlight named 'X'. It has: (none).",
                   e.tryDelete(NOCOL, "X"));
   }
}
