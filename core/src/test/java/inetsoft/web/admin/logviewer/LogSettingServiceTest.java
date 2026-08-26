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
package inetsoft.web.admin.logviewer;

/*
 * Test strategy
 *
 * Every field saved from Settings > Logging is trimmed by sanitizeProperty, which also
 * stores a blank value as null. The Fluentd shared key is the only field that is
 * additionally encrypted, and it used to encrypt the raw value instead of the sanitized
 * one, so a key pasted with a trailing space or newline kept that whitespace. The defect
 * was invisible in Enterprise Manager because the field round-trips through
 * decryptPassword and redisplays whatever was stored; only the collector, which digests
 * the key, noticed.
 *
 * Behavioral guarantees covered:
 *
 * [G1] A shared key with surrounding whitespace is trimmed before it is encrypted.
 * [G2] A shared key that ends in a newline (the copy-out-of-a-terminal case) is trimmed.
 * [G3] A shared key with no surrounding whitespace is stored unchanged, so the trim
 *      cannot damage a key that was already correct.
 * [G4] A blank shared key clears the property rather than storing an encrypted blank.
 * [G5] Both credentials are encrypted; the non-secret fields are trimmed but stored as
 *      plain text. The password used to be stored in clear text even though the read
 *      already ran it through decryptPassword, which left it readable on the enterprise
 *      Settings > Properties page (PropertiesController only filters the log.fluentd.*
 *      family on community builds).
 * [G6] The read decrypts both credentials, so the write and the Logging page agree. The
 *      runtime read in ForwardService.getClient has to decrypt the same way; it used to
 *      call plain SreeEnv.getProperty and digest the ciphertext, which no Logging-page
 *      input could make authenticate (see enterprise ForwardServiceTest).
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.log.LogManager;
import inetsoft.util.log.logback.LogbackUtil;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class LogSettingServiceTest {
   private static final String SHARED_KEY_PROPERTY = "log.fluentd.security.sharedKey";
   private static final String PASSWORD_PROPERTY = "log.fluentd.security.password";

   private LogSettingService service;
   private Principal principal;
   private MockedStatic<SreeEnv> sreeEnvStatic;
   private MockedStatic<Tool> toolStatic;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<LogbackUtil> logbackStatic;

   @BeforeEach
   void setUp() {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      service = new LogSettingService(securityEngine, mock(LogManager.class));
      principal = mock(Principal.class);

      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      toolStatic =
         mockStatic(Tool.class, withSettings().lenient().defaultAnswer(CALLS_REAL_METHODS));
      sUtilStatic = mockStatic(SUtil.class, withSettings().lenient());
      auditStatic = mockStatic(Audit.class, withSettings().lenient());
      logbackStatic = mockStatic(LogbackUtil.class, withSettings().lenient());

      // stub the encryption so the assertions do not depend on a master key being
      // available; the marker lets the test see exactly what was handed to it
      toolStatic.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(invocation -> "ENC:" + invocation.getArgument(0));

      sUtilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), anyString(),
                                                   anyString()))
         .thenReturn(mock(ActionRecord.class));
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));

      // the stubbing calls above count as invocations on the static mocks, so clear them
      // to let the tests assert on invocation counts made by the service alone
      toolStatic.clearInvocations();
   }

   @AfterEach
   void tearDown() {
      logbackStatic.close();
      auditStatic.close();
      sUtilStatic.close();
      toolStatic.close();
      sreeEnvStatic.close();
   }

   // [G1] a key pasted with surrounding spaces must be trimmed before encryption
   @Test
   void sharedKeyIsTrimmedBeforeEncryption() {
      saveSharedKey("  secret-key  ");

      verifySharedKeyStoredAs("ENC:secret-key");
   }

   // [G2] copying a secret out of a terminal typically appends a newline
   @Test
   void sharedKeyTrailingNewlineIsTrimmedBeforeEncryption() {
      saveSharedKey("secret-key\n");

      verifySharedKeyStoredAs("ENC:secret-key");
   }

   // [G3] the trim must not alter a key that was already correct
   @Test
   void sharedKeyWithoutWhitespaceIsUnchanged() {
      saveSharedKey("secret-key");

      verifySharedKeyStoredAs("ENC:secret-key");
   }

   // [G4] a blank key clears the property instead of storing an encrypted blank
   @Test
   void blankSharedKeyClearsTheProperty() {
      saveSharedKey("   ");

      sreeEnvStatic.verify(() -> SreeEnv.setProperty(SHARED_KEY_PROPERTY, null));
      // scoped to the blank value: the password on the same model is a non-blank
      // credential and is legitimately encrypted
      toolStatic.verify(
         () -> Tool.encryptPassword(argThat(value -> value == null || value.isBlank())),
         never());
   }

   // [G5] both credentials are encrypted; the non-secret fields are only trimmed
   @Test
   void credentialsAreEncryptedAndOtherFieldsAreOnlyTrimmed() {
      service.setConfiguration(model(fluentdSettings("secret-key")), principal);

      sreeEnvStatic.verify(
         () -> SreeEnv.setProperty("log.fluentd.security.password", "ENC:logger-pw"));
      sreeEnvStatic.verify(() -> SreeEnv.setProperty("log.fluentd.host", "fluentd.example.com"));
      sreeEnvStatic.verify(() -> SreeEnv.setProperty("log.fluentd.security.username", "logger"));
      sreeEnvStatic.verify(
         () -> SreeEnv.setProperty("log.fluentd.tls.caCertificateFile", "/certs/ca.pem"));
      toolStatic.verify(() -> Tool.encryptPassword(anyString()), times(2));
   }

   // [G6] the Logging page shows the decrypted credentials, so the write and this read
   // agree; ForwardService must decrypt the same way (see enterprise ForwardServiceTest)
   @Test
   void credentialsAreDecryptedWhenRead() {
      toolStatic.when(() -> Tool.decryptPassword(anyString()))
         .thenAnswer(invocation -> {
            String stored = invocation.getArgument(0);
            return stored.startsWith("ENC:") ? stored.substring(4) : stored;
         });
      sreeEnvStatic.when(() -> SreeEnv.getProperty(anyString(), anyString()))
         .thenAnswer(invocation -> invocation.getArgument(1));
      sreeEnvStatic.when(() -> SreeEnv.getProperty("log.provider")).thenReturn("fluentd");
      sreeEnvStatic.when(() -> SreeEnv.getProperty("log.detail.level")).thenReturn("INFO");
      sreeEnvStatic.when(() -> SreeEnv.getProperty("report.log.max")).thenReturn("1000000");
      sreeEnvStatic.when(() -> SreeEnv.getProperty("report.log.count")).thenReturn("1");
      sreeEnvStatic.when(() -> SreeEnv.getProperty(SHARED_KEY_PROPERTY))
         .thenReturn("ENC:secret-key");
      sreeEnvStatic.when(() -> SreeEnv.getProperty(PASSWORD_PROPERTY))
         .thenReturn("ENC:logger-pw");

      LogSettingsModel model = service.getConfiguration();

      assertNotNull(model);
      assertEquals("secret-key", model.fluentdSettings().sharedKey());
      assertEquals("logger-pw", model.fluentdSettings().password());
   }

   private void saveSharedKey(String sharedKey) {
      service.setConfiguration(model(fluentdSettings(sharedKey)), principal);
   }

   private void verifySharedKeyStoredAs(String expected) {
      sreeEnvStatic.verify(() -> SreeEnv.setProperty(SHARED_KEY_PROPERTY, expected));
   }

   private static FluentdLogSettingsModel fluentdSettings(String sharedKey) {
      return FluentdLogSettingsModel.builder()
         .port(24224)
         .host("  fluentd.example.com  ")
         .connectTimeout(10000)
         .securityEnabled(true)
         .sharedKey(sharedKey)
         .userAuthenticationEnabled(true)
         .username("  logger  ")
         .password("  logger-pw  ")
         .tlsEnabled(true)
         .caCertificateFile("  /certs/ca.pem  ")
         .logViewUrl("http://fluentd.example.com/logs")
         .orgAdminAccess(false)
         .build();
   }

   private static LogSettingsModel model(FluentdLogSettingsModel fluentdSettings) {
      return LogSettingsModel.builder()
         .provider("fluentd")
         .fluentdSettings(fluentdSettings)
         .outputToStd(false)
         .detailLevel("INFO")
         .build();
   }
}
