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
package inetsoft.web.admin.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.internal.Util;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #77953, #77958: the passwords of the configured server locations are only sent to a client
 * as a placeholder. A placeholder keeps a stored location password only for a location or a task
 * path that logs in to the same server as the same user.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ServerLocationPasswordTest {
   @BeforeEach
   void setUp() {
      SreeEnv.setProperty(PROPERTY, STORED);
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty(PROPERTY, null);
   }

   // Bug #77953, settings save

   @Test
   void keepsPasswordOfUnchangedLocation() {
      saveLocation("ftp://files.corp.example/reports", "Renamed", "svc", PLACEHOLDER, storedKey());
      assertEquals("ftp://files.corp.example/reports|Renamed|svc|" + PASSWORD, stored());
   }

   @Test
   void keepsPasswordForSameServerAndUser() {
      saveLocation("ftp://files.corp.example:21/reports/2026/", "Reports", "svc", PLACEHOLDER,
                   storedKey());
      assertEquals("ftp://files.corp.example:21/reports/2026/|Reports|svc|" + PASSWORD, stored());
   }

   @Test
   void keepsPasswordOfLocationStoredWithTrailingSlash() {
      SreeEnv.setProperty(PROPERTY, "ftp://files.corp.example/reports/|Reports|svc|" + PASSWORD);
      saveLocation("ftp://files.corp.example/reports", "Reports", "svc", PLACEHOLDER, storedKey());
      assertEquals(STORED, stored());
   }

   @Test
   void doesNotMovePasswordToAnotherServerOrUser() {
      saveLocation("ftp://collector.invalid:2121/drop", "Reports", "attacker", PLACEHOLDER,
                   storedKey());
      assertEquals("ftp://collector.invalid:2121/drop|Reports|attacker", stored());

      saveLocation("ftp://files.corp.example/reports", "Reports", "attacker", PLACEHOLDER,
                   storedKey());
      assertEquals("ftp://files.corp.example/reports|Reports|attacker", stored());
   }

   @Test
   void doesNotKeepPasswordForAnotherPortProtocolOrPathUser() {
      for(String path : List.of("ftp://files.corp.example:2121/reports",
                                "sftp://files.corp.example/reports",
                                "ftp://other@files.corp.example/reports",
                                "ftp://files.corp.example/reports/x@collector.invalid/drop"))
      {
         SreeEnv.setProperty(PROPERTY, STORED);
         saveLocation(path, "Reports", "svc", PLACEHOLDER, storedKey());
         assertEquals(path + "|Reports|svc", stored(), path);
      }
   }

   @Test
   void neverStoresLiteralNullOrPlaceholder() {
      saveLocation("ftp://h/x", "L", "u", PLACEHOLDER, "no-such-key");
      assertEquals("ftp://h/x|L|u", stored());

      saveLocation("ftp://h/y", "M", "u", PLACEHOLDER, null);
      assertEquals("ftp://h/y|M|u", stored());
   }

   @Test
   void storesTypedPassword() {
      saveLocation("ftp://collector.invalid/drop", "Reports", "svc", "typed", storedKey());
      assertEquals("ftp://collector.invalid/drop|Reports|svc|typed", stored());
   }

   // Bug #77958, task editor model

   @Test
   void taskEditorModelHasNoLocationPassword() throws Exception {
      ScheduleService scheduleService = mock(ScheduleService.class);
      when(scheduleService.getServerLocations(any())).thenCallRealMethod();
      when(scheduleService.getViewsheets(any())).thenReturn(new AssetEntry[0]);
      ScheduleManager scheduleManager = mock(ScheduleManager.class);
      ScheduleTask task = new ScheduleTask("alice~;~other-org:t1");
      when(scheduleManager.getScheduleTask("t1")).thenReturn(task);
      AnalyticRepository repository = mock(AnalyticRepository.class);
      when(repository.getFolders(any(), any())).thenReturn(new RepositoryEntry[0]);
      ScheduleTaskService service = new ScheduleTaskService(
         repository, scheduleManager, scheduleService, null, mock(SecurityProvider.class), null,
         mock(SecurityEngine.class));
      XPrincipal user = new XPrincipal(new IdentityID("alice", "other-org"));

      TaskActionPaneModel model = service.getTaskActions("t1", user, false);
      String json = new ObjectMapper().writeValueAsString(model);

      assertFalse(json.contains(PASSWORD), json);
      ServerPathInfoModel location = model.serverLocations().get(0).pathInfoModel();
      assertEquals("svc", location.username());
      assertEquals(PLACEHOLDER, location.password());
   }

   // Bug #77953, #77958, task save

   @Test
   void usesLocationPasswordForPathInLocation() throws Exception {
      assertEquals(PASSWORD, saveToServerPassword("ftp://files.corp.example/reports/a.pdf", "svc",
                                                  PLACEHOLDER));
      assertEquals(PASSWORD, saveToServerPassword("ftp://svc@files.corp.example/reports", null,
                                                  PLACEHOLDER));
   }

   @Test
   void doesNotUseLocationPasswordOutsideLocation() throws Exception {
      for(String path : List.of("ftp://collector.invalid/reports/a.pdf",
                                "ftp://files.corp.example:2121/reports/a.pdf",
                                "sftp://files.corp.example/reports/a.pdf",
                                "ftp://files.corp.example/other/a.pdf",
                                "ftp://files.corp.example/reportsx/a.pdf",
                                "ftp://files.corp.example/reports/../other/a.pdf",
                                "ftp://files.corp.example/reports/x@collector.invalid/reports/a"))
      {
         assertNull(saveToServerPassword(path, "svc", PLACEHOLDER), path);
      }
   }

   @Test
   void doesNotUseLocationPasswordForAnotherUser() throws Exception {
      assertNull(saveToServerPassword("ftp://files.corp.example/reports/a.pdf", "other",
                                      PLACEHOLDER));
      assertNull(saveToServerPassword("ftp://other@files.corp.example/reports/a.pdf", "svc",
                                      PLACEHOLDER));
      assertNull(saveToServerPassword("ftp://svc:own@files.corp.example/reports/a.pdf", "svc",
                                      PLACEHOLDER));
   }

   @Test
   void keepsTypedPassword() throws Exception {
      assertEquals("typed", saveToServerPassword("ftp://files.corp.example/reports/a.pdf", "svc",
                                                 "typed"));
   }

   private static void saveLocation(String path, String label, String username, String password,
                                    String oldPasswordKey)
   {
      SchedulerConfigurationService service =
         new SchedulerConfigurationService(mock(ScheduleClient.class), null, null, null);
      ServerLocation location = ServerLocation.builder()
         .path(path)
         .label(label)
         .pathInfoModel(ServerPathInfoModel.builder()
            .path(path).username(username).password(password).oldPasswordKey(oldPasswordKey)
            .ftp(true).build())
         .build();
      ReflectionTestUtils.invokeMethod(service, "setServerLocations", List.of(location));
   }

   private static String storedKey() {
      return SUtil.getServerLocations().get(0).pathInfoModel().oldPasswordKey();
   }

   private static String stored() {
      return SreeEnv.getProperty(PROPERTY);
   }

   // a new save-to-server path in the portal or EM editor, with the location's model copied in
   private static String saveToServerPassword(String path, String username, String password)
      throws Exception
   {
      ScheduleService service = new ScheduleService(
         null, null, null, null, null, mock(DeployService.class), null, null, null, null, null,
         null, null);
      GeneralActionModel model = mock(GeneralActionModel.class);
      when(model.sheet()).thenReturn("vs1");
      when(model.actionType()).thenReturn("ViewsheetAction");
      when(model.saveToServerEnabled()).thenReturn(true);
      when(model.saveFormats()).thenReturn(new String[] { String.valueOf(PDF) });
      when(model.serverFilePaths()).thenReturn(List.of(ServerPathInfoModel.builder()
         .path(path).ftp(true).username(username).password(password).build()));

      ViewsheetAction action = (ViewsheetAction) service.getActionFromModel(
         model, null, mock(Principal.class), "http://host/");
      return action.getFilePathInfo(PDF).getPassword();
   }

   private static final String PROPERTY = "server.save.locations";
   private static final String PASSWORD = "S3cretAdminPwd";
   private static final String STORED = "ftp://files.corp.example/reports|Reports|svc|" + PASSWORD;
   private static final String PLACEHOLDER = Util.PLACEHOLDER_PASSWORD;
   private static final int PDF = FileFormatInfo.EXPORT_TYPE_PDF;
}
