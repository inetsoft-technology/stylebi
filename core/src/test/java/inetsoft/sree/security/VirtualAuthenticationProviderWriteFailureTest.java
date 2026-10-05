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
package inetsoft.sree.security;

import inetsoft.sree.internal.SUtil;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.util.DataSpace;
import inetsoft.util.MessageException;
import inetsoft.web.admin.security.ChangePasswordController;
import inetsoft.web.admin.security.ChangePasswordRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77814: with security disabled, a password change of the virtual admin must report a failed
 * write of virtual_security.xml to the caller and keep the running node on the saved password,
 * while the first-run write made when the provider is created stays best-effort.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  VirtualAuthenticationProviderWriteFailureTest.FailingDataSpaceConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VirtualAuthenticationProviderWriteFailureTest {
   @AfterEach
   void resetFailures() {
      FAIL_COMMIT.set(false);
      FAIL_STREAM.set(false);
   }

   @Test
   void securityEngineChangePasswordReportsFailedWriteAndKeepsSavedPassword() throws Exception {
      SecurityEngine engine = SecurityEngine.getSecurity();
      assertFalse(engine.isSecurityEnabled(), "precondition: security disabled");
      AuthenticationProvider live = engine.getSecurityProvider().getAuthenticationProvider();
      assertInstanceOf(VirtualAuthenticationProvider.class, live);

      engine.changePassword(null, SAVED_PASSWORD);
      assertTrue(authenticate(new VirtualAuthenticationProvider(), SAVED_PASSWORD));

      FAIL_COMMIT.set(true);
      assertThrows(MessageException.class, () -> engine.changePassword(null, NEW_PASSWORD));
      FAIL_COMMIT.set(false);

      assertSavedPasswordKept(live);
   }

   @Test
   void emChangePasswordControllerReportsFailedWriteAndKeepsSavedPassword() throws Exception {
      SecurityEngine engine = SecurityEngine.getSecurity();
      AuthenticationProvider live = engine.getSecurityProvider().getAuthenticationProvider();
      ChangePasswordController controller = new ChangePasswordController(engine.getSecurityProvider());
      Principal principal = adminPrincipal();

      controller.changePassword(request(SAVED_PASSWORD), principal);
      assertTrue(authenticate(new VirtualAuthenticationProvider(), SAVED_PASSWORD));

      FAIL_STREAM.set(true);
      assertThrows(MessageException.class,
                   () -> controller.changePassword(request(NEW_PASSWORD), principal));
      FAIL_STREAM.set(false);

      assertSavedPasswordKept(live);
   }

   @Test
   void providerAddUserReportsFailedWriteAndLaterChangeStillSaves() throws Exception {
      SecurityEngine engine = SecurityEngine.getSecurity();
      VirtualAuthenticationProvider live =
         (VirtualAuthenticationProvider) engine.getSecurityProvider().getAuthenticationProvider();
      engine.changePassword(null, SAVED_PASSWORD);

      // the call the enterprise SecurityApiService.changeUserPassword makes
      FSUser admin = (FSUser) live.getUser(ADMIN);
      SUtil.setPassword(admin, NEW_PASSWORD);
      FAIL_COMMIT.set(true);
      assertThrows(MessageException.class, () -> live.addUser(admin));
      FAIL_COMMIT.set(false);

      assertSavedPasswordKept(live);

      // a change made after the storage recovers is saved and used
      engine.changePassword(null, NEW_PASSWORD);
      assertTrue(authenticate(live, NEW_PASSWORD));
      assertFalse(authenticate(live, SAVED_PASSWORD));
      assertTrue(authenticate(new VirtualAuthenticationProvider(), NEW_PASSWORD));
   }

   @Test
   void firstRunWriteFailureDoesNotFailProviderCreation() {
      String envPassword = System.getenv("INETSOFT_ADMIN_PASSWORD");
      assumeEnvPassword(envPassword);
      DataSpace space = DataSpace.getDataSpace();
      space.delete(null, FILE_NAME);
      assertFalse(space.exists(null, FILE_NAME), "precondition: no virtual_security.xml");

      FAIL_COMMIT.set(true);
      VirtualAuthenticationProvider provider =
         assertDoesNotThrow(() -> new VirtualAuthenticationProvider());
      FAIL_COMMIT.set(false);

      assertTrue(authenticate(provider, envPassword));
      assertFalse(space.exists(null, FILE_NAME));
   }

   @Test
   void securityEngineInitSurvivesFirstRunWriteFailureAndKeepsEnvPassword() throws Exception {
      String envPassword = System.getenv("INETSOFT_ADMIN_PASSWORD");
      assumeEnvPassword(envPassword);
      SecurityEngine engine = SecurityEngine.getSecurity();
      DataSpace space = DataSpace.getDataSpace();
      space.delete(null, FILE_NAME);

      FAIL_STREAM.set(true);
      assertDoesNotThrow(engine::init);
      AuthenticationProvider live = engine.getSecurityProvider().getAuthenticationProvider();
      assertTrue(authenticate(live, envPassword));
      assertFalse(space.exists(null, FILE_NAME));

      // storage still failing: the change is reported and the node stays on the env password
      ChangePasswordController controller = new ChangePasswordController(engine.getSecurityProvider());
      assertThrows(MessageException.class,
                   () -> controller.changePassword(request(NEW_PASSWORD), adminPrincipal()));
      FAIL_STREAM.set(false);
      assertTrue(authenticate(live, envPassword), "running node keeps the env password");
      assertFalse(authenticate(live, NEW_PASSWORD));

      // storage recovered: the change is saved and survives a re-init
      engine.changePassword(null, NEW_PASSWORD);
      engine.init();
      AuthenticationProvider reinit = engine.getSecurityProvider().getAuthenticationProvider();
      assertTrue(authenticate(reinit, NEW_PASSWORD));
      assertFalse(authenticate(reinit, envPassword));
   }

   private static void assumeEnvPassword(String password) {
      Assumptions.assumeTrue(password != null && !password.isEmpty(),
                             "INETSOFT_ADMIN_PASSWORD is not set");
   }

   private static void assertSavedPasswordKept(AuthenticationProvider live) {
      assertTrue(authenticate(live, SAVED_PASSWORD), "running node keeps the saved password");
      assertFalse(authenticate(live, NEW_PASSWORD), "running node must not use the unsaved password");

      VirtualAuthenticationProvider reloaded = new VirtualAuthenticationProvider();
      assertTrue(authenticate(reloaded, SAVED_PASSWORD), "saved file keeps the old password");
      assertFalse(authenticate(reloaded, NEW_PASSWORD));
   }

   private static boolean authenticate(AuthenticationProvider provider, String password) {
      return provider.authenticate(ADMIN, new DefaultTicket(ADMIN, password));
   }

   private static ChangePasswordRequest request(String password) {
      return ChangePasswordRequest.builder().password(password).build();
   }

   private static Principal adminPrincipal() {
      String orgId = Organization.getDefaultOrganizationID();
      return new SRPrincipal(ADMIN, new IdentityID[] { new IdentityID("Administrator", orgId) },
                             new String[0], orgId, 1L);
   }

   @Configuration
   static class FailingDataSpaceConfig {
      // replaces BaseTestConfiguration.dataSpace with a real DataSpace whose writes can be failed
      @Bean
      public DataSpace dataSpace(BlobStorageManager blobStorageManager) {
         return new DataSpace(blobStorageManager) {
            @Override
            public Transaction beginTransaction() {
               Transaction real = super.beginTransaction();

               return new Transaction() {
                  @Override
                  public OutputStream newStream(String dir, String file) throws IOException {
                     failStream();
                     return real.newStream(dir, file);
                  }

                  @Override
                  public OutputStream newStream(String dir, String file, long ts) throws IOException {
                     failStream();
                     return real.newStream(dir, file, ts);
                  }

                  @Override
                  public void commit() throws IOException {
                     if(FAIL_COMMIT.get()) {
                        throw new IOException("simulated storage commit failure");
                     }

                     real.commit();
                  }

                  @Override
                  public void close() throws IOException {
                     real.close();
                  }
               };
            }
         };
      }

      private static void failStream() throws IOException {
         if(FAIL_STREAM.get()) {
            throw new IOException("simulated storage stream failure");
         }
      }
   }

   private static final AtomicBoolean FAIL_COMMIT = new AtomicBoolean(false);
   private static final AtomicBoolean FAIL_STREAM = new AtomicBoolean(false);
   private static final String FILE_NAME = "virtual_security.xml";
   private static final IdentityID ADMIN =
      new IdentityID("admin", Organization.getDefaultOrganizationID());
   private static final String SAVED_PASSWORD = "Known#Pass1234";
   private static final String NEW_PASSWORD = "Changed#Pass5678";
}
