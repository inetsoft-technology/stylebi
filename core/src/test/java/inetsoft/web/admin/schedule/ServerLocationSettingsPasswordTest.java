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

import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.test.*;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77953: an EM schedule settings save keeps the stored password of a server location only
 * for an unchanged location, or one that logs in to the same server as the same user. The user
 * in the path overrides the user name field.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ServerLocationSettingsPasswordTest {
   @AfterEach
   void tearDown() {
      SreeEnv.setProperty(PROPERTY, null);
   }

   @Test
   void doesNotKeepPasswordForAnotherHost() {
      SreeEnv.setProperty(PROPERTY, "ftp://a.example/r|R|svc|pw1");
      saveLocations(location("ftp://b.example/r", "R", "svc", key(0)));
      assertEquals("ftp://b.example/r|R|svc", stored());
   }

   @Test
   void doesNotKeepPasswordForPathWithItsOwnPassword() {
      SreeEnv.setProperty(PROPERTY, "ftp://a.example/r|R|svc|pw1");
      saveLocations(location("ftp://svc:x@a.example/r", "R", "svc", key(0)));
      assertEquals("ftp://svc:x@a.example/r|R|svc", stored());
   }

   @Test
   void keepsPasswordForSameUserInPath() {
      SreeEnv.setProperty(PROPERTY, "ftp://a.example/r|R|svc|pw1");
      saveLocations(location("ftp://svc@a.example/r", "R", "other", key(0)));
      assertEquals("ftp://svc@a.example/r|R|other|pw1", stored());
   }

   @Test
   void doesNotKeepPasswordWhenUserInPathChanges() {
      SreeEnv.setProperty(PROPERTY, "ftp://svc@a.example/r|R|svc|pw1");
      saveLocations(location("ftp://other@a.example/r", "R", "svc", key(0)));
      assertEquals("ftp://other@a.example/r|R|svc", stored());
   }

   @Test
   void keepsPasswordOfUnchangedLocationThatDoesNotParse() {
      SreeEnv.setProperty(PROPERTY, "ftp://{host}/r|R|svc|pw1");
      saveLocations(location("ftp://{host}/r", "R", "svc", key(0)));
      assertEquals("ftp://{host}/r|R|svc|pw1", stored());

      saveLocations(location("ftp://{other}/r", "R", "svc", key(0)));
      assertEquals("ftp://{other}/r|R|svc", stored());
   }

   @Test
   void doesNotSwapPasswordsBetweenLocations() {
      String locations = "ftp://a.example/r|A|svc|pwA;ftp://b.example/r|B|svc|pwB";
      SreeEnv.setProperty(PROPERTY, locations);
      saveLocations(location("ftp://a.example/r", "A", "svc", key(1)),
                    location("ftp://b.example/r", "B", "svc", key(0)));
      assertEquals("ftp://a.example/r|A|svc;ftp://b.example/r|B|svc", stored());

      SreeEnv.setProperty(PROPERTY, locations);
      saveLocations(location("ftp://a.example/r", "A", "svc", key(0)),
                    location("ftp://b.example/r", "B", "svc", key(1)));
      assertEquals(locations, stored());
   }

   @Test
   void storesNoPasswordWithoutUser() {
      SreeEnv.setProperty(PROPERTY, "ftp://a.example/r|R|svc|pw1");
      saveLocations(location("ftp://a.example/r", "R", "", key(0)));
      assertEquals("ftp://a.example/r|R", stored());
   }

   @Test
   void keepsLocalAndSecretIdLocations() {
      String locations = "/local/dir|L;ftp://s.example/r?useSecretId=true|S|secret1";
      SreeEnv.setProperty(PROPERTY, locations);
      List<ServerLocation> loaded = SUtil.getServerLocations();

      for(ServerLocation location : loaded) {
         assertNull(location.pathInfoModel().password(), location.label());
      }

      saveLocations(loaded.toArray(new ServerLocation[0]));
      assertEquals(locations, stored());
   }

   @Test
   void onlyServerSideLocationsHavePasswords() {
      SreeEnv.setProperty(PROPERTY, "ftp://a.example/r/|R|svc|pw1;ftp://b.example/r|B|u");
      ServerLocation location = SUtil.getServerLocations().get(1);

      assertEquals("ftp://a.example/r", location.path());
      assertEquals(Util.PLACEHOLDER_PASSWORD, location.pathInfoModel().password());
      assertNull(SUtil.getServerLocations().get(0).pathInfoModel().password());
      assertEquals("pw1", SUtil.getServerLocationsWithPasswords().get(1).pathInfoModel().password());
   }

   private static ServerLocation location(String path, String label, String username,
                                          String oldPasswordKey)
   {
      return ServerLocation.builder()
         .path(path)
         .label(label)
         .pathInfoModel(ServerPathInfoModel.builder()
            .path(path).username(username).password(Util.PLACEHOLDER_PASSWORD)
            .oldPasswordKey(oldPasswordKey).ftp(true).build())
         .build();
   }

   private static void saveLocations(ServerLocation... locations) {
      SchedulerConfigurationService service =
         new SchedulerConfigurationService(mock(ScheduleClient.class), null, null, null);
      ReflectionTestUtils.invokeMethod(service, "setServerLocations", List.of(locations));
   }

   // the locations are sorted by label
   private static String key(int index) {
      return SUtil.getServerLocations().get(index).pathInfoModel().oldPasswordKey();
   }

   private static String stored() {
      return SreeEnv.getProperty(PROPERTY);
   }

   private static final String PROPERTY = "server.save.locations";
}
