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
package inetsoft.web.admin.ai.dashboard;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.content.repository.model.RepositoryDashboardSettingsModel;
import inetsoft.web.admin.content.repository.model.RepositoryFolderDashboardSettingsModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Controller-level tests for {@link AdminDashboardController} -- mirrors {@code
 * AdminViewsheetControllerGetFolderTest}'s standalone {@link MockMvc} shape/conventions: no Spring
 * context, {@code DashboardChangePlanService}/{@code DashboardChangesetApplyService} are mocked so
 * this exercises only request mapping/binding/exception handling, not the underlying registry
 * logic.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {
   @Mock private DashboardChangePlanService planService;
   @Mock private DashboardChangesetApplyService applyService;
   @Mock private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockMvc mvc;

   private static final Principal TEST_PRINCIPAL = () -> "test-user";

   @BeforeEach void setUp() {
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.isSiteAdmin(TEST_PRINCIPAL)).thenReturn(true);

      mvc = standaloneSetup(new AdminDashboardController(planService, applyService))
         .setMessageConverters(new MappingJackson2HttpMessageConverter())
         .build();
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
   }

   private static RepositoryDashboardSettingsModel dashboard(String name) {
      return RepositoryDashboardSettingsModel.builder()
         .name(name)
         .oname(name)
         .description("a dashboard")
         .viewsheet("1^128^__NULL__^Examples/Chart")
         .enable(true)
         .visible(true)
         .permissions(null)
         .build();
   }

   @Test void listReturnsPlanServiceResult() throws Exception {
      when(planService.list(isNull(), any())).thenReturn(List.of(dashboard("Dashboard1__GLOBAL")));

      mvc.perform(get("/api/wiz/v1/admin/dashboards")
            .principal(TEST_PRINCIPAL)
            .header("Authorization", "Bearer test-token"))
         .andExpect(status().isOk())
         .andExpect(content().string(org.hamcrest.Matchers.containsString(
            "\"name\":\"Dashboard1__GLOBAL\"")));

      verify(planService).list(isNull(), any());
   }

   @Test void getSettingsReturnsPlanServiceResult() throws Exception {
      when(planService.getSettings(eq("Dashboard1__GLOBAL"), isNull(), any()))
         .thenReturn(dashboard("Dashboard1__GLOBAL"));

      mvc.perform(get("/api/wiz/v1/admin/dashboards/settings")
            .param("path", "Dashboard1__GLOBAL")
            .principal(TEST_PRINCIPAL)
            .header("Authorization", "Bearer test-token"))
         .andExpect(status().isOk())
         .andExpect(content().string(org.hamcrest.Matchers.containsString(
            "\"description\":\"a dashboard\"")));

      verify(planService).getSettings("Dashboard1__GLOBAL", null, TEST_PRINCIPAL);
   }

   @Test void getFolderReturnsPlanServiceResult() throws Exception {
      RepositoryFolderDashboardSettingsModel folder = RepositoryFolderDashboardSettingsModel.builder()
         .dashboards(List.of("Dashboard1__GLOBAL", "Dashboard2__GLOBAL"))
         .permissions(null)
         .build();
      when(planService.getFolder(isNull(), any())).thenReturn(folder);

      mvc.perform(get("/api/wiz/v1/admin/dashboards/folder")
            .principal(TEST_PRINCIPAL)
            .header("Authorization", "Bearer test-token"))
         .andExpect(status().isOk())
         .andExpect(content().string(org.hamcrest.Matchers.containsString("Dashboard2__GLOBAL")));

      verify(planService).getFolder(null, TEST_PRINCIPAL);
   }

   @Test void previewReturnsResolvedPlan() throws Exception {
      PlanChange change = new PlanChange("dashboard::Dashboard3__GLOBAL", "host-org", null,
         "name=Dashboard3", AdminChangeRecord.RISK_LOW, AdminChangeRecord.SCOPE_STORAGE, true,
         "create dashboard \"Dashboard3\" (global)");
      ResolvedPlan plan = new ResolvedPlan("pin a new dashboard", List.of(change), true, false,
                                          "planhash123", "tasktoken123");
      when(planService.resolve(any(), any())).thenReturn(plan);

      String body = "{\"task\":\"pin a new dashboard\",\"changes\":[{\"unitType\":\"dashboard\"," +
         "\"verb\":\"create\",\"name\":\"Dashboard3\",\"viewsheet\":\"1^128^__NULL__^Examples/Chart\"}]}";

      mvc.perform(post("/api/wiz/v1/admin/dashboards/preview")
            .contentType("application/json")
            .content(body)
            .principal(TEST_PRINCIPAL)
            .header("Authorization", "Bearer test-token"))
         .andExpect(status().isOk())
         .andExpect(content().string(org.hamcrest.Matchers.containsString("\"planHash\":\"planhash123\"")));

      verify(planService).resolve(any(), eq(TEST_PRINCIPAL));
   }

   @Test void applyReturnsApplyResult() throws Exception {
      DashboardApplyOutcome outcome = new DashboardApplyOutcome(
         "dashboard::Dashboard3__GLOBAL", null, "name=Dashboard3", AdminChangeRecord.STATUS_VERIFIED, null);
      DashboardApplyResult result = new DashboardApplyResult(
         "dashboard-abc123", AdminChangesetApplyService.STATUS_APPLIED, null, List.of(outcome), null);
      when(applyService.apply(any(), any())).thenReturn(result);

      String body = "{\"task\":\"pin a new dashboard\",\"changes\":[{\"unitType\":\"dashboard\"," +
         "\"verb\":\"create\",\"name\":\"Dashboard3\",\"viewsheet\":\"1^128^__NULL__^Examples/Chart\"}]," +
         "\"planHash\":\"planhash123\",\"taskToken\":\"tasktoken123\"}";

      mvc.perform(post("/api/wiz/v1/admin/dashboards/apply")
            .contentType("application/json")
            .content(body)
            .principal(TEST_PRINCIPAL)
            .header("Authorization", "Bearer test-token"))
         .andExpect(status().isOk())
         .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"applied\"")));

      verify(applyService).apply(any(), eq(TEST_PRINCIPAL));
   }

   @Test void applyPlanHashMismatchReturnsConflict() throws Exception {
      PlanChange change = new PlanChange("dashboard::Dashboard3__GLOBAL", "host-org", null,
         "name=Dashboard3", AdminChangeRecord.RISK_LOW, AdminChangeRecord.SCOPE_STORAGE, true,
         "create dashboard \"Dashboard3\" (global)");
      ResolvedPlan currentPlan = new ResolvedPlan("pin a new dashboard", List.of(change), true, false,
                                                  "newhash456", null);
      when(applyService.apply(any(), any()))
         .thenThrow(new AdminChangesetApplyService.PlanHashMismatchException(currentPlan));

      String body = "{\"task\":\"pin a new dashboard\",\"changes\":[{\"unitType\":\"dashboard\"," +
         "\"verb\":\"create\",\"name\":\"Dashboard3\",\"viewsheet\":\"1^128^__NULL__^Examples/Chart\"}]," +
         "\"planHash\":\"stalehash\",\"taskToken\":\"tasktoken123\"}";

      mvc.perform(post("/api/wiz/v1/admin/dashboards/apply")
            .contentType("application/json")
            .content(body)
            .principal(TEST_PRINCIPAL)
            .header("Authorization", "Bearer test-token"))
         .andExpect(status().isConflict())
         .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"conflict\"")));
   }
}
