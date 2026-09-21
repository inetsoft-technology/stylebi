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
package inetsoft.web.wiz.binding;

import inetsoft.web.wiz.pairing.*;
import inetsoft.web.wiz.viewsheet.ViewsheetFormatService;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Exercises {@code set_table_column_sort}'s Java-side route through real Spring MVC request
 * binding -- the layer {@link TableBindingServiceTest}'s own {@code setColumnSort*} tests cannot
 * reach, since those call {@code TableBindingService.setColumnSort(...)} directly and never go
 * through an actual {@code @PostMapping}.
 *
 * <p>Bug #76871 (VTB-028's column-sort-404 finding): the plugin's {@code set_table_column_sort}
 * tool posted to {@code table/column-sort}, a path {@code BindingAgentController} never mapped --
 * every call 404ed with Spring's own {@code NoResourceFoundException} (dispatcher-not-found
 * fallback), even though the session, feature flag and every other route were healthy. Run
 * against a revert of {@code BindingAgentController#setTableColumnSort} (this fix's own new
 * route method), this test's {@code status().isOk()} assertion fails with a 404, reproducing
 * that exact symptom; against the current, fixed controller it passes -- see the fix's own
 * write-up for the actual before/after run.
 */
@Tag("core")
class BindingAgentControllerSetTableColumnSortRouteTest {
   private static Principal principal() {
      return () -> "admin";
   }

   @Test
   void routeDispatchesToTheServiceAndReachesTheHandler() throws Exception {
      TableBindingService tableService = mock(TableBindingService.class);
      SheetAgentFeature feature = mock(SheetAgentFeature.class);
      when(feature.isEnabled()).thenReturn(true);

      BindingAgentController controller = new BindingAgentController(
         feature, mock(SheetJoinService.class), mock(SheetSessionService.class),
         mock(ViewsheetSessionService.class), mock(BindableFieldsService.class),
         mock(BindingReadService.class), mock(ChartBindingService.class),
         mock(ChartAestheticAgentService.class), tableService, mock(CalcTableService.class),
         mock(SelectionBindingService.class), mock(CalcFieldAgentService.class),
         mock(ViewsheetFormatService.class), mock(SheetAgentBroadcastService.class));

      MockMvc mvc = standaloneSetup(controller).build();

      mvc.perform(post("/api/wiz/v1/agent/binding/tok/table/column-sort")
            .contentType(MediaType.APPLICATION_JSON)
            .principal(principal())
            .content("{\"assembly\":\"Table1\",\"column\":\"Region\",\"direction\":\"desc\"}"))
         .andExpect(status().isOk());

      verify(tableService).setColumnSort(eq("tok"), any(Principal.class), eq("Table1"),
                                         eq("Region"), eq("desc"), anyString());
   }
}
