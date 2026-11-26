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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.web.composer.model.vs.VSObjectFormatInfoModel;
import inetsoft.web.composer.vs.controller.FormatPainterService;
import inetsoft.web.composer.vs.objects.event.FormatVSObjectEvent;
import inetsoft.web.wiz.binding.CalcTableService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.controller.UndoRedoServiceProxy;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static inetsoft.web.wiz.viewsheet.ViewsheetFormatServiceTest.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82311: {@code set_format} carries {@code presenterProperties} to the painter, refuses
 * properties that cannot be stored before anything is written, and {@code get_format} reads the
 * stored ones back.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServicePresenterPropertiesTest {
   private static final String BAR = "inetsoft.report.painter.BarPresenter";
   private static final ObjectMapper JSON = new ObjectMapper();

   private static VSObjectFormatInfoModel parse(String format) throws Exception {
      return ViewsheetFormatService.FormatRequest.fromJson(
         List.of("Gauge1"), JSON.readTree(format), false, "object", null, null, null).format();
   }

   @Test
   void presenterPropertiesAreReadIntoTheFormatModel() throws Exception {
      VSObjectFormatInfoModel model = parse(
         "{\"presenter\":\"" + BAR + "\",\"presenterProperties\":" +
         "{\"maximum\":100,\"color\":\"#2E8B57\",\"autoMaximum\":false}}");

      assertEquals(BAR, model.getPresenter());
      assertEquals(100, model.getPresenterProperties().get("maximum"));
      assertEquals("#2E8B57", model.getPresenterProperties().get("color"));
   }

   @Test
   void presenterPropertiesAreKeysOfTheFormat() throws Exception {
      ViewsheetFormatService.FormatRequest request = ViewsheetFormatService.FormatRequest.fromJson(
         List.of("Gauge1"),
         JSON.readTree("{\"presenter\":\"" + BAR + "\",\"presenterProperties\":{\"maximum\":1}}"),
         false, "object", null, null, null);

      assertTrue(request.formatKeys().contains("presenterProperties"));
   }

   @Test
   void propertiesWithoutAPresenterAreLeftForTheStoredPresenter() throws Exception {
      VSObjectFormatInfoModel model = parse("{\"presenterProperties\":{\"maximum\":100}}");

      assertNull(model.getPresenter());
      assertEquals(100, model.getPresenterProperties().get("maximum"));
   }

   @Test
   void anUnknownPropertyIsRefusedByNameWithTheSettableList() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> parse("{\"presenter\":\"" + BAR + "\",\"presenterProperties\":{\"maxium\":1}}"));

      assertTrue(e.getMessage().startsWith("set_format: presenterProperties:"), e.getMessage());
      assertTrue(e.getMessage().contains("'maxium'"), e.getMessage());
      assertTrue(e.getMessage().contains("maximum (a number)"), e.getMessage());
   }

   @Test
   void aWrongTypedValueIsRefusedByName() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> parse("{\"presenter\":\"" + BAR + "\",\"presenterProperties\":" +
                     "{\"maximum\":\"lots\"}}"));

      assertTrue(e.getMessage().contains("'maximum' must be a number"), e.getMessage());
   }

   @Test
   void propertiesWithAnEmptyPresenterAreRefused() {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> parse("{\"presenter\":\"\",\"presenterProperties\":{\"maximum\":1}}"));

      assertTrue(e.getMessage().contains("empty 'presenter'"), e.getMessage());
   }

   @Test
   void theWriteReachesThePainterWithTheProperties() throws Exception {
      GaugeVSAssemblyInfo gauge = gaugeInfo();
      FormatPainterService painter = mock(FormatPainterService.class);
      service(sessionsFor(rvsOf(Map.of("Gauge1", (VSAssemblyInfo) gauge))), painter,
              mock(CalcTableService.class))
         .setFormat("tok", principal(), jsonRequest(
            "[\"Gauge1\"]", "object",
            "{\"presenter\":\"" + BAR + "\",\"presenterProperties\":{\"maximum\":100}}"), "");

      FormatVSObjectEvent event = paintedEvents(painter, 1).get(0);

      assertEquals(BAR, event.getFormat().getPresenter());
      assertEquals(100, event.getFormat().getPresenterProperties().get("maximum"));
   }

   /**
    * Bug #82429: through the real {@code mutate}, a write the painter refuses (an unknown
    * property, a wrong type, a Text with no presenter) leaves no undo step; a valid write adds
    * exactly one.
    */
   private static final String UNKNOWN_PROP = "{\"presenterProperties\":{\"maxValue\":1}}";

   private RuntimeViewsheet realMutateRvs;

   private ViewsheetFormatService realMutateService(FormatPainterService painter)
      throws Exception
   {
      realMutateRvs = rvsOf(Map.of("Gauge1", (VSAssemblyInfo) gaugeInfo()));
      java.security.Principal agent = TestPrincipals.user("alice", "host-org");
      SheetSessionService sessions = mock(SheetSessionService.class);
      SheetRuntimeAccess access = mock(SheetRuntimeAccess.class);
      JoinSession js = new JoinSession("tok", "Viewsheet/foo-7", "alice~;~host-org",
                                       SheetType.VIEWSHEET, 0L, Long.MAX_VALUE,
                                       JoinSession.ConnectionMode.PAIRED, null, null, null);
      when(sessions.resolve(eq("tok"), any())).thenReturn(js);
      when(access.getSheetForPairing(eq(SheetType.VIEWSHEET), eq("Viewsheet/foo-7"), eq(agent)))
         .thenReturn(realMutateRvs);
      ViewsheetSessionService real = new ViewsheetSessionService(
         sessions, access, mock(SheetAgentBroadcastService.class),
         mock(UndoRedoServiceProxy.class));
      return new ViewsheetFormatService(
         real, FormatPainterProxies.local(painter), mock(CalcTableService.class), null);
   }

   @Test
   void aPainterRefusalLeavesNoUndoStep82429() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      doThrow(new IllegalArgumentException("'maxValue' is not a property of presenter"))
         .when(painter).setFormat(anyString(), any(), any(java.security.Principal.class), any(),
                                  anyString());
      ViewsheetFormatService service = realMutateService(painter);
      java.security.Principal agent = TestPrincipals.user("alice", "host-org");

      assertThrows(IllegalArgumentException.class, () -> service.setFormat(
         "tok", agent, jsonRequest("[\"Gauge1\"]", "object", UNKNOWN_PROP), ""));

      verify(realMutateRvs).restoreCurrentCheckpoint();
      verify(realMutateRvs, never()).addCheckpoint(any());
   }

   @Test
   void aValidWriteAddsExactlyOneUndoStep82429() throws Exception {
      FormatPainterService painter = mock(FormatPainterService.class);
      ViewsheetFormatService service = realMutateService(painter);
      java.security.Principal agent = TestPrincipals.user("alice", "host-org");

      service.setFormat("tok", agent, jsonRequest("[\"Gauge1\"]", "object",
                                                  "{\"color\":\"#ff0000\"}"), "");

      verify(realMutateRvs, times(1)).addCheckpoint(any());
      verify(realMutateRvs, never()).restoreCurrentCheckpoint();
   }

   @Test
   void theStoredPropertiesAreReadBack() throws Exception {
      PresenterRef ref = new PresenterRef(BAR);
      Map<String, Object> props = new LinkedHashMap<>();
      props.put("maximum", 100);
      props.put("color", "#2E8B57");
      inetsoft.web.composer.vs.dialog.PresenterPropertySupport.apply(ref, props);

      VSCompositeFormat own = new VSCompositeFormat();
      own.getUserDefinedFormat().setPresenterValue(ref);

      VSObjectFormatInfoModel model = new VSObjectFormatInfoModel();
      model.setPresenter(BAR);

      Map<String, Object> wire = toStoredWireFormat(model, own);

      assertEquals(BAR, wire.get("presenter"));
      @SuppressWarnings("unchecked")
      Map<String, Object> read = (Map<String, Object>) wire.get("presenterProperties");
      assertEquals(100.0, read.get("maximum"));
      assertEquals("#2E8B57", ((String) read.get("color")).toUpperCase());
   }

   @Test
   void aFormatWithNoPresenterReadsNoProperties() throws Exception {
      VSCompositeFormat own = new VSCompositeFormat();
      own.getUserDefinedFormat().setBackgroundValue("#FF0000");

      Map<String, Object> wire = toStoredWireFormat(new VSObjectFormatInfoModel(), own);

      assertFalse(wire.containsKey("presenter"));
      assertFalse(wire.containsKey("presenterProperties"));
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> toStoredWireFormat(VSObjectFormatInfoModel model,
                                                         VSCompositeFormat own)
      throws Exception
   {
      Method method = ViewsheetFormatService.class.getDeclaredMethod(
         "toStoredWireFormat", VSObjectFormatInfoModel.class, VSCompositeFormat.class);
      method.setAccessible(true);
      return (Map<String, Object>) method.invoke(null, model, own);
   }
}
