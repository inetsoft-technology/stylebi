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
 * Bug #77971: a password in the user info of a server location path is moved to the location's
 * password field when the location is saved and when it is loaded, so it is never sent to a
 * client in the path. The password in the path wins over the password field and the secret.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ServerLocationPathPasswordTest {
   @AfterEach
   void tearDown() {
      SreeEnv.setProperty(PROPERTY, null);
   }

   // stored by EM Settings > Properties, or before the fix

   @Test
   void loadMovesPathPasswordToPasswordField() throws Exception {
      SreeEnv.setProperty(PROPERTY, LOCATION + "|Reports");
      ServerLocation location = SUtil.getServerLocations().get(0);
      ServerPathInfoModel model = location.pathInfoModel();

      assertFalse(new ObjectMapper().writeValueAsString(location).contains(PASSWORD));
      assertEquals(SPLIT, location.path());
      assertEquals(SPLIT, model.path());
      assertEquals("svc", model.username());
      assertEquals(PLACEHOLDER, model.password());
      assertTrue(model.ftp());
      assertEquals(SPLIT + "|Reports|svc", model.oldPasswordKey());
      assertEquals(PASSWORD,
                   SUtil.getServerLocationsWithPasswords().get(0).pathInfoModel().password());
   }

   @Test
   void loadedPathPasswordWinsOverFieldAndSecret() throws Exception {
      for(String stored : List.of(LOCATION + "|Reports|other|fieldPw",
                                  LOCATION + "?useSecretId=true|Reports|secret1",
                                  "sftp://svc:" + PASSWORD + "@files.corp.example/reports|Reports",
                                  "FTP://svc:" + PASSWORD + "@files.corp.example/reports/|Reports"))
      {
         SreeEnv.setProperty(PROPERTY, stored);
         ServerLocation location = SUtil.getServerLocations().get(0);
         ServerPathInfoModel model = SUtil.getServerLocationsWithPasswords().get(0).pathInfoModel();

         assertFalse(new ObjectMapper().writeValueAsString(location).contains(PASSWORD), stored);
         assertTrue(model.password().endsWith(PASSWORD), stored);
         assertFalse(model.useCredential(), stored);
         assertNull(model.secretId(), stored);
      }
   }

   @Test
   void keepsLocationsWithoutPathPassword() {
      String locations = "/local/a:b@dir|A;ftp://svc@files.corp.example/r|B|svc|pw1;" +
         "ftp://svc:@files.corp.example/r|C|svc";
      SreeEnv.setProperty(PROPERTY, locations);
      List<ServerLocation> loaded = SUtil.getServerLocationsWithPasswords();

      assertEquals("/local/a:b@dir", loaded.get(0).path());
      assertFalse(loaded.get(0).pathInfoModel().ftp());
      assertEquals("ftp://svc@files.corp.example/r", loaded.get(1).path());
      assertEquals("pw1", loaded.get(1).pathInfoModel().password());
      assertEquals("ftp://svc:@files.corp.example/r", loaded.get(2).path());
   }

   // EM Settings > Schedule > Settings

   @Test
   void saveMovesPathPasswordToPasswordField() {
      saveLocations(location(LOCATION, null, null, false));
      assertEquals(SPLIT + "|Reports|svc|" + PASSWORD, stored());

      saveLocations(location(LOCATION + "/", "other", "fieldPw", true));
      assertEquals(SPLIT + "/|Reports|svc|" + PASSWORD, stored());

      saveLocations(location("FTP://svc:" + PASSWORD + "@files.corp.example/reports", null, null,
                             false));
      String path = stored().substring(0, stored().indexOf('|'));
      assertFalse(path.contains(PASSWORD), stored());
   }

   @Test
   void settingsRoundTripKeepsPathPassword() {
      SreeEnv.setProperty(PROPERTY, LOCATION + "|Reports");
      saveLocations(SUtil.getServerLocations().toArray(new ServerLocation[0]));
      assertEquals(SPLIT + "|Reports|svc|" + PASSWORD, stored());

      saveLocations(SUtil.getServerLocations().toArray(new ServerLocation[0]));
      assertEquals(SPLIT + "|Reports|svc|" + PASSWORD, stored());
   }

   // task editor and task save

   @Test
   void taskEditorModelHasNoPathPassword() throws Exception {
      for(String stored : List.of(LOCATION + "|Reports", LOCATION + "|Reports|svc|fieldPw",
                                  "sftp://svc:" + PASSWORD + "@files.corp.example/reports|Reports"))
      {
         SreeEnv.setProperty(PROPERTY, stored);
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
         assertEquals(PLACEHOLDER, model.serverLocations().get(0).pathInfoModel().password());
      }
   }

   @Test
   void taskInLocationUsesPathPassword() throws Exception {
      SreeEnv.setProperty(PROPERTY, LOCATION + "|Reports");
      ServerPathInfo path = saveToServerPath(SPLIT + "/a.pdf", null, PLACEHOLDER);

      assertEquals(SPLIT + "/a.pdf", path.getPath());
      assertEquals(PASSWORD, path.getPassword());
   }

   @Test
   void taskOutsideLocationLoginDoesNotGetPathPassword() throws Exception {
      SreeEnv.setProperty(PROPERTY, LOCATION + "|Reports");

      assertEquals(PASSWORD, saveToServerPath("ftp://files.corp.example/reports/a.pdf", "svc",
                                              PLACEHOLDER).getPassword());
      assertNull(saveToServerPath("ftp://bob@files.corp.example/reports/a.pdf", null, PLACEHOLDER)
                    .getPassword());
      assertNull(saveToServerPath("ftp://svc@other.example/reports/a.pdf", null, PLACEHOLDER)
                    .getPassword());
      assertNull(saveToServerPath("ftp://svc@files.corp.example/other/a.pdf", null, PLACEHOLDER)
                    .getPassword());
      assertNull(saveToServerPath("sftp://svc@files.corp.example/reports/a.pdf", null, PLACEHOLDER)
                    .getPassword());
   }

   @Test
   void relabeledLocationKeepsPathPassword() {
      SreeEnv.setProperty(PROPERTY, LOCATION + "|Reports");
      ServerLocation location = SUtil.getServerLocations().get(0);
      saveLocations(ServerLocation.builder().from(location).label("Archive").build());

      assertEquals(SPLIT + "|Archive|svc|" + PASSWORD, stored());
   }

   private static ServerLocation location(String path, String username, String password,
                                          boolean ftp)
   {
      return ServerLocation.builder()
         .path(path)
         .label("Reports")
         .pathInfoModel(ServerPathInfoModel.builder()
            .path(path).username(username).password(password).ftp(ftp).build())
         .build();
   }

   private static void saveLocations(ServerLocation... locations) {
      SchedulerConfigurationService service =
         new SchedulerConfigurationService(mock(ScheduleClient.class), null, null, null);
      ReflectionTestUtils.invokeMethod(service, "setServerLocations", List.of(locations));
   }

   private static String stored() {
      return SreeEnv.getProperty(PROPERTY);
   }

   // a new save-to-server path in the portal or EM editor, with the location's model copied in
   private static ServerPathInfo saveToServerPath(String path, String username, String password)
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
      return action.getFilePathInfo(PDF);
   }

   private static final String PROPERTY = "server.save.locations";
   private static final String PASSWORD = "S3cretAdminPwd";
   private static final String LOCATION = "ftp://svc:" + PASSWORD + "@files.corp.example/reports";
   private static final String SPLIT = "ftp://svc@files.corp.example/reports";
   private static final String PLACEHOLDER = Util.PLACEHOLDER_PASSWORD;
   private static final int PDF = FileFormatInfo.EXPORT_TYPE_PDF;
}
