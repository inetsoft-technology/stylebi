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
package inetsoft.util;

import inetsoft.test.*;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77722: {@link LocalPasswordEncryption#encryptPassword(String)} passes a well-formed
 * {@code \aes} value that the current key cannot decrypt through unchanged. This must not
 * swallow clear-text passwords that merely start with {@code \aes}, must not stop an export (force master) from converting a stored
 * {@code \aes} secret to {@code \master}, and must not affect empty or non-ASCII passwords.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LocalPasswordEncryptionSecretKeyPassthroughTest {
   @Test
   void clearTextStartingWithAesPrefixIsEncrypted() {
      String[] clearText = {
         "\\aes", "\\aes:", "\\aes::", "\\aesYWJj:ZGVm", "\\aesYWJjZGVm", "\\aes secret",
         "\\aes\u00e4:\u00f6", "\\AESYWJj:ZGVm"
      };

      for(String value : clearText) {
         String encrypted = Tool.encryptPassword(value);
         assertNotEquals(value, encrypted, value);
         assertTrue(encrypted.startsWith(AbstractPasswordEncryption.NEW_PREFIX), value);
         assertEquals(value, Tool.decryptPassword(encrypted), value);
      }
   }

   @Test
   void emptyAndNonAsciiPasswordsRoundTrip() {
      assertEquals("", Tool.encryptPassword(""));
      assertEquals("", Tool.decryptPassword(""));
      assertNull(Tool.encryptPassword(null));

      String[] passwords = { "p\u00e4ssw\u00f6rd", "\u5bc6\u7801", "emoji \ud83d\ude00", " " };

      for(String password : passwords) {
         String encrypted = Tool.encryptPassword(password);
         assertTrue(encrypted.startsWith(AbstractPasswordEncryption.NEW_PREFIX), password);
         assertEquals(password, Tool.decryptPassword(encrypted), password);
      }
   }

   @Test
   void exportConvertsStoredAesSecretToMaster() throws Exception {
      LocalPasswordCredential credential = new LocalPasswordCredential();
      credential.setUser("user");
      credential.setPassword(PASSWORD);
      String savedXml = write(credential);
      String savedValue = storedPassword(savedXml);
      assertTrue(savedValue.startsWith(AbstractPasswordEncryption.NEW_PREFIX));

      // as DeployUtil.createExport() does around writing the exported assets
      PasswordEncryption.setForceMaster(true);
      PasswordEncryption.setEncryptForceLocal(true);
      String exportedXml;
      String exportedRawValue;

      try {
         exportedXml = write(parse(savedXml));
         // a caller that hands the stored value itself to encryptPassword
         exportedRawValue = Tool.encryptPassword(savedValue);
      }
      finally {
         PasswordEncryption.setForceMaster(false);
         PasswordEncryption.setEncryptForceLocal(false);
      }

      String exportedValue = storedPassword(exportedXml);
      assertTrue(exportedValue.startsWith(AbstractPasswordEncryption.MASTER_PREFIX), exportedValue);
      assertEquals(PASSWORD, parse(exportedXml).getPassword());
      assertTrue(exportedRawValue.startsWith(AbstractPasswordEncryption.MASTER_PREFIX));
      assertEquals(savedValue, Tool.decryptPassword(exportedRawValue));
   }

   private static String write(LocalPasswordCredential credential) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         credential.writeXML(writer);
      }

      return buffer.toString();
   }

   private static LocalPasswordCredential parse(String xml) throws Exception {
      Element elem = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      LocalPasswordCredential credential = new LocalPasswordCredential();
      credential.parseXML(elem);
      return credential;
   }

   private static String storedPassword(String xml) {
      String start = "<password><![CDATA[";
      int index = xml.indexOf(start);
      assertTrue(index >= 0, xml);
      index += start.length();
      return xml.substring(index, xml.indexOf("]]>", index));
   }

   private static final String PASSWORD = "s3cret";
}
