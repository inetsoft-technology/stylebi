/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.PasswordEncryption;
import inetsoft.util.Tool;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.config.SecretsConfig;
import inetsoft.web.admin.schedule.model.ServerPathInfoModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationContext;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/*
 * Tier: [unit] — pure POJO logic; ConfigurationContext mocks only for XML password paths.
 *
 * Known source bugs (documented below; NOT fixed at this time — tests record current behavior):
 *
 * [BUG-SPI-1] checkFTP() never resets ftp/sftp to false
 *   Location : ServerPathInfo.java:116 (checkFTP)
 *   Actual   : ftp/sftp are set true when conditions match but never cleared when path/credentials
 *              change; e.g. ftp:// then setPath("/local") with credentials cleared leaves isFTP()==true.
 *   UI risk  : Low — EM save paths create a new ServerPathInfo or replace via setServerPaths();
 *              ScheduleService does not mutate an existing instance in place for normal task edits.
 *   Status   : Deferred. Covered by setPathToLocalPath_doesNotClearStickyFtpFlag().
 *
 * [BUG-SPI-2] equals() overridden but hashCode() uses Object identity
 *   Location : ServerPathInfo.java:194 (equals)
 *   Actual   : equal instances can have different hashCode(); breaks HashSet/HashMap contract.
 *   UI risk  : None observed — ServerPathInfo is not used as a hash key in scheduler code paths.
 *   Status   : Deferred. Tracked in @Disabled KnownBugs test.
 *
 * [BUG-SPI-3] setUseCredential() / setSecretId() do not invoke checkFTP()
 *   Location : ServerPathInfo.java:78, 86 (setters)
 *   Actual   : isFTP() stays false until setPath/setUsername/setPassword runs, even when
 *              useCredential and secretId would satisfy checkFTP() conditions.
 *   UI risk  : Low — production code uses constructors, ServerPathInfo(Model), or parseXML(),
 *              all of which call checkFTP(); these setters are not called from ScheduleService.
 *   Status   : Deferred. useCredentialWithSecretIdIsTreatedAsFtp() works around via setPath().
 */

/*
 * Cases deferred - low value:
 *
 * [ServerPathInfo] Cloneable with no clone() implementation
 *             -> interface only; NOT duplicated here
 */
@Tag("core")
class ServerPathInfoTest {

   // -------------------------------------------------------------------------
   // P1 — checkFTP() detection and edge cases
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("FTP / SFTP detection (checkFTP)")
   class FtpDetectionTests {

      @Test
      @DisplayName("local path without credentials is not FTP")
      void localPathWithoutCredentials() {
         ServerPathInfo info = new ServerPathInfo("/test/path");
         assertEquals("/test/path", info.getPath());
         assertFalse(info.isFTP());
         assertFalse(info.isSFTP());
      }

      @Test
      @DisplayName("sftp:// sets both isFTP and isSFTP")
      void sftpUrlSetsFtpAndSftpFlags() {
         ServerPathInfo info = new ServerPathInfo("sftp://test/path");
         assertTrue(info.isFTP());
         assertTrue(info.isSFTP());
      }

      @Test
      @DisplayName("ftp:// sets isFTP but not isSFTP")
      void ftpUrlSetsFtpOnly() {
         ServerPathInfo info = new ServerPathInfo("ftp://test/path", "admin", "admin");
         assertEquals("ftp://test/path", info.getPath());
         assertTrue(info.isFTP());
         assertFalse(info.isSFTP());
         assertFalse(info.isUseCredential());
      }

      @Test
      @DisplayName("URL scheme is matched case-insensitively")
      void urlSchemeIsCaseInsensitive() {
         assertTrue(new ServerPathInfo("FTP://host/path").isFTP());
         assertFalse(new ServerPathInfo("FTP://host/path").isSFTP());
         assertTrue(new ServerPathInfo("SFTP://host/path").isSFTP());
      }

      @Test
      @DisplayName("non-empty username on a local path marks the path as FTP")
      void localPathWithUsernameIsTreatedAsFtp() {
         ServerPathInfo info = new ServerPathInfo("/exports/backups");
         assertFalse(info.isFTP());

         info.setUsername("deploy");
         assertTrue(info.isFTP());
         assertFalse(info.isSFTP());
      }

      @Test
      @DisplayName("useCredential with secretId marks the path as FTP after checkFTP runs")
      void useCredentialWithSecretIdIsTreatedAsFtp() {
         ServerPathInfo info = new ServerPathInfo("/exports/backups");
         info.setUseCredential(true);
         info.setSecretId("vault-cred-1");
         // BUG-SPI-3 (deferred): setters alone do not call checkFTP; setPath triggers re-evaluation.
         info.setPath("/exports/backups");

         assertTrue(info.isFTP());
         assertFalse(info.isSFTP());
      }

      @Test
      @DisplayName("setPath updates path and re-runs checkFTP")
      void setPathUpdatesPath() {
         ServerPathInfo info = new ServerPathInfo("/test/path");
         info.setPath("/test/path2");
         assertEquals("/test/path2", info.getPath());
      }

      @Test
      @DisplayName("checkFTP does not clear ftp flag when path changes away from ftp://")
      void setPathToLocalPath_doesNotClearStickyFtpFlag() {
         ServerPathInfo info = new ServerPathInfo("ftp://remote/path");
         assertTrue(info.isFTP());

         info.setPath("/local/path");
         info.setUsername(null);
         info.setPassword(null);

         // BUG-SPI-1 (deferred): documents sticky ftp flag — not cleared when path becomes local.
         assertTrue(info.isFTP());
      }

      @Test
      @DisplayName("default constructor leaves path null and flags false")
      void defaultConstructorInitialState() {
         ServerPathInfo info = new ServerPathInfo();
         assertNull(info.getPath());
         assertFalse(info.isFTP());
         assertFalse(info.isSFTP());
         assertFalse(info.isUseCredential());
      }

      @Test
      @DisplayName("setPath(null) throws because checkFTP calls path.toLowerCase()")
      void setPathNullThrowsNullPointerException() {
         ServerPathInfo info = new ServerPathInfo("/local/path");
         assertThrows(NullPointerException.class, () -> info.setPath(null));
      }

      @Test
      @DisplayName("setUsername on default instance throws when path is still null")
      void setUsernameOnNullPathThrowsNullPointerException() {
         ServerPathInfo info = new ServerPathInfo();
         assertNull(info.getPath());
         assertThrows(NullPointerException.class, () -> info.setUsername("deploy"));
      }

      @Test
      @DisplayName("credential setters retain values")
      void credentialSettersRetainValues() {
         ServerPathInfo info = new ServerPathInfo("ftp://test/path", "admin", "admin");

         info.setUseCredential(true);
         assertTrue(info.isUseCredential());

         info.setSecretId("abc");
         assertEquals("abc", info.getSecretId());

         info.setUsername("admin");
         assertEquals("admin", info.getUsername());

         info.setPassword("123");
         assertEquals("123", info.getPassword());
      }
   }

   // -------------------------------------------------------------------------
   // P2 — ServerPathInfoModel construction
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("ServerPathInfoModel construction")
   class ModelConstructionTests {

      @Test
      @DisplayName("ftp model with username/password copies credentials")
      void ftpModelWithUsernamePassword() {
         ServerPathInfoModel model = mock(ServerPathInfoModel.class);
         when(model.path()).thenReturn("/test/path");
         when(model.ftp()).thenReturn(true);
         when(model.useCredential()).thenReturn(false);
         when(model.username()).thenReturn("admin");
         when(model.password()).thenReturn("123");

         ServerPathInfo info = new ServerPathInfo(model);

         assertEquals("/test/path", info.getPath());
         assertTrue(info.isFTP());
         assertFalse(info.isUseCredential());
         assertEquals("admin", info.getUsername());
         assertEquals("123", info.getPassword());
      }

      @Test
      @DisplayName("ftp model with useCredential copies secretId only")
      void ftpModelWithVaultCredential() {
         ServerPathInfoModel model = mock(ServerPathInfoModel.class);
         when(model.path()).thenReturn("ftp://host/export");
         when(model.ftp()).thenReturn(true);
         when(model.useCredential()).thenReturn(true);
         when(model.secretId()).thenReturn("vault-cred-1");

         ServerPathInfo info = new ServerPathInfo(model);

         assertTrue(info.isUseCredential());
         assertEquals("vault-cred-1", info.getSecretId());
         assertNull(info.getUsername());
         assertNull(info.getPassword());
         assertTrue(info.isFTP());
      }

      @Test
      @DisplayName("non-ftp model copies path only and ignores credentials")
      void nonFtpModelIgnoresCredentials() {
         ServerPathInfoModel model = mock(ServerPathInfoModel.class);
         when(model.path()).thenReturn("/local/export.zip");
         when(model.ftp()).thenReturn(false);
         when(model.useCredential()).thenReturn(true);
         when(model.secretId()).thenReturn("ignored");
         when(model.username()).thenReturn("ignored");
         when(model.password()).thenReturn("ignored");

         ServerPathInfo info = new ServerPathInfo(model);

         assertEquals("/local/export.zip", info.getPath());
         assertFalse(info.isUseCredential());
         assertNull(info.getSecretId());
         assertNull(info.getUsername());
         assertNull(info.getPassword());
         assertFalse(info.isFTP());
      }
   }

   // -------------------------------------------------------------------------
   // P1/P3 — equals / compareTo
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("equals / compareTo")
   class EqualsAndCompareToTests {

      @Test
      @DisplayName("username/password instances: equal, unequal, null, wrong type")
      void usernamePasswordEquality() {
         ServerPathInfo info1 = new ServerPathInfo("/test/path", "user1", "pass1");
         ServerPathInfo info2 = new ServerPathInfo("/test/path", "user1", "pass1");
         ServerPathInfo info3 = new ServerPathInfo("/test/path2", "user2", "pass2");

         assertEquals(info1, info2);
         assertNotEquals(info1, info3);
         assertNotEquals(null, info1);
         assertNotEquals("some string", info1);
      }

      @Test
      @DisplayName("useCredential instances compare secretId and useCredential flag")
      void vaultCredentialEquality() {
         ServerPathInfo info1 = new ServerPathInfo("ftp://host/path");
         info1.setUseCredential(true);
         info1.setSecretId("cred-a");

         ServerPathInfo info2 = new ServerPathInfo("ftp://host/path");
         info2.setUseCredential(true);
         info2.setSecretId("cred-a");

         ServerPathInfo info3 = new ServerPathInfo("ftp://host/path");
         info3.setUseCredential(true);
         info3.setSecretId("cred-b");

         assertEquals(info1, info2);
         assertNotEquals(info1, info3);

         info3.setUseCredential(false);
         assertNotEquals(info1, info3);
      }

      @Test
      @DisplayName("compareTo orders by path only and returns -1 for non-ServerPathInfo")
      void compareToOrdersByPathOnly() {
         ServerPathInfo info1 = new ServerPathInfo("/test/path", "user1", "pass1");
         ServerPathInfo info2 = new ServerPathInfo("/test/path", "user2", "pass2");
         ServerPathInfo info3 = new ServerPathInfo("/test/path2", "user2", "pass2");

         assertEquals(0, info1.compareTo(info2),
            "compareTo ignores username/password differences");
         assertTrue(info1.compareTo(info3) < 0);
         assertTrue(info3.compareTo(info1) > 0);
         assertEquals(-1, info1.compareTo("invalid object"));
      }
   }

   // -------------------------------------------------------------------------
   // P3 — byte encoding helpers
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("Encoding (byteEncode / byteDecode / isEncoding / setEncoding)")
   class EncodingTests {

      @Test
      @DisplayName("setEncoding(true): byteEncode encodes non-ASCII, byteDecode restores it")
      void encodingEnabledRoundTrips() {
         ServerPathInfo info = new ServerPathInfo();
         info.setEncoding(true);
         assertTrue(info.isEncoding());

         String original = "导出目录";
         String encoded = info.byteEncode(original);
         assertNotEquals(original, encoded);
         assertEquals(original, info.byteDecode(encoded));
      }

      @Test
      @DisplayName("setEncoding(false): byteEncode is identity")
      void encodingDisabledIsIdentity() {
         ServerPathInfo info = new ServerPathInfo();
         assertFalse(info.isEncoding());
         assertEquals("/plain/path", info.byteEncode("/plain/path"));
      }
   }

   // -------------------------------------------------------------------------
   // P2/P3 — XML round-trip
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("XML round-trip (writeXML / parseXML)")
   class XmlRoundTripTests {

      private ApplicationContext savedAppContext;

      @BeforeEach
      void setUpPasswordContext() {
         PasswordEncryption mockPwdEnc = mock(PasswordEncryption.class);
         when(mockPwdEnc.decryptPassword(any())).thenAnswer(inv -> inv.getArgument(0));
         when(mockPwdEnc.encryptPassword(any())).thenAnswer(inv -> inv.getArgument(0));

         InetsoftConfig mockInetsoftConfig = mock(InetsoftConfig.class);
         when(mockInetsoftConfig.getSecrets()).thenReturn(new SecretsConfig());

         ApplicationContext mockAppCtx = mock(ApplicationContext.class);
         when(mockAppCtx.getBean(PasswordEncryption.class)).thenReturn(mockPwdEnc);
         when(mockAppCtx.getBean(InetsoftConfig.class)).thenReturn(mockInetsoftConfig);

         savedAppContext = ConfigurationContext.getContext().getApplicationContext();
         ConfigurationContext.getContext().setApplicationContext(mockAppCtx);
      }

      @AfterEach
      void tearDownPasswordContext() {
         ConfigurationContext.getContext().setApplicationContext(savedAppContext);
      }

      @Test
      @DisplayName("local path with username/password survives round-trip")
      void usernamePasswordRoundTrip() throws Exception {
         ServerPathInfo original = new ServerPathInfo("/exports/report.pdf", "scheduler", "s3cret");
         ServerPathInfo loaded = writeAndParse(original);

         assertEquals(original, loaded);
         assertEquals("scheduler", loaded.getUsername());
         assertEquals("s3cret", loaded.getPassword());
         assertFalse(loaded.isUseCredential());
      }

      @Test
      @DisplayName("useCredential with secretId survives round-trip")
      void vaultCredentialRoundTrip() throws Exception {
         ServerPathInfo original = new ServerPathInfo("ftp://host/export");
         original.setUseCredential(true);
         original.setSecretId("vault-cred-42");

         ServerPathInfo loaded = writeAndParse(original);

         assertEquals(original, loaded);
         assertTrue(loaded.isUseCredential());
         assertEquals("vault-cred-42", loaded.getSecretId());
      }

      @Test
      @DisplayName("encoding=true round-trips path with non-ASCII characters")
      void encodedPathRoundTrip() throws Exception {
         ServerPathInfo original = new ServerPathInfo("/导出/报表.pdf", "user", "pass");
         original.setEncoding(true);

         ServerPathInfo loaded = writeAndParse(original);

         assertEquals("/导出/报表.pdf", loaded.getPath());
         assertEquals("user", loaded.getUsername());
         assertEquals("pass", loaded.getPassword());
      }

      @Test
      @DisplayName("encryptForceLocal inlines vault credentials into XML instead of secretId")
      void encryptForceLocalWritesInlineCredentials() throws Exception {
         PasswordEncryption.setEncryptForceLocal(true);

         try(MockedStatic<Tool> tool = mockStatic(Tool.class)) {
            ObjectNode credentials = new ObjectMapper().createObjectNode();
            credentials.put("username", "vault-user");
            credentials.put("password", "vault-pass");
            tool.when(() -> Tool.loadCredentials("vault-id")).thenReturn(credentials);
            tool.when(() -> Tool.encryptPassword("vault-pass")).thenReturn("ENC:vault-pass");
            tool.when(() -> Tool.escape(any())).thenAnswer(inv -> inv.getArgument(0));

            ServerPathInfo info = new ServerPathInfo("ftp://host/export");
            info.setUseCredential(true);
            info.setSecretId("vault-id");

            String xml = writeXml(info);

            assertTrue(xml.contains("username=\"vault-user\""), xml);
            assertTrue(xml.contains("password=\"ENC:vault-pass\""), xml);
            assertFalse(xml.contains("secretId="), xml);
            assertTrue(xml.contains("useCredential=\"false\""), xml);
         }
         finally {
            PasswordEncryption.setEncryptForceLocal(false);
         }
      }
   }

   // -------------------------------------------------------------------------
   // Known source bugs — documented, not fixed at this time
   // -------------------------------------------------------------------------

   /**
    * Known source bugs (deferred — no production fix in this phase).
    *
    * <p>BUG-SPI-1: sticky ftp/sftp flags after in-place path changes (see FtpDetectionTests).
    * BUG-SPI-2: missing hashCode() alongside equals() — @Disabled failing spec below.
    * BUG-SPI-3: setUseCredential/setSecretId skip checkFTP() — not hit by EM/API save paths.
    */
   @Nested
   @Tag("known-bug")
   @DisplayName("Known source bugs (deferred)")
   class KnownBugs {

      @Test
      @Disabled("BUG-SPI-2 (deferred): equal instances have different hashCode — ServerPathInfo:194; " +
         "fix: override hashCode() consistent with equals()")
      @DisplayName("BUG-SPI-2: equal instances must have the same hashCode")
      void equalInstancesMustHaveSameHashCode() {
         ServerPathInfo info1 = new ServerPathInfo("/test/path", "user1", "pass1");
         ServerPathInfo info2 = new ServerPathInfo("/test/path", "user1", "pass1");
         assertEquals(info1, info2);
         assertEquals(info1.hashCode(), info2.hashCode());
      }
   }

   // -------------------------------------------------------------------------
   // Helpers
   // -------------------------------------------------------------------------

   private static ServerPathInfo writeAndParse(ServerPathInfo original) throws Exception {
      ServerPathInfo loaded = new ServerPathInfo();
      loaded.setEncoding(original.isEncoding());
      loaded.parseXML(parseServerPathXml(writeXml(original)));
      return loaded;
   }

   private static String writeXml(ServerPathInfo info) {
      StringWriter sw = new StringWriter();
      info.writeXML(new PrintWriter(sw));
      return sw.toString();
   }

   private static Element parseServerPathXml(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      return factory.newDocumentBuilder()
         .parse(new InputSource(new StringReader(xml)))
         .getDocumentElement();
   }
}
