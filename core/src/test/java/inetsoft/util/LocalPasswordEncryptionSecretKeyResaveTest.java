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

import inetsoft.sree.SreeEnv;
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
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77722: when password.encryption.key changes (e.g. a storage restore overwrites it), a
 * stored {@code \aes} secret cannot be decrypted. Saving its owner again must keep the
 * original ciphertext, so that the password is recovered once the original key is back.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LocalPasswordEncryptionSecretKeyResaveTest {
   @BeforeEach
   void setUp() {
      // make sure the key exists before it is read
      Tool.encryptPassword("init");
      originalKey = SreeEnv.getProperty(KEY_PROPERTY);
      assertNotNull(originalKey);
   }

   @AfterEach
   void tearDown() throws Exception {
      setKey(originalKey);
   }

   @Test
   void resaveUnderWrongKeyKeepsOriginalCiphertext() throws Exception {
      LocalPasswordCredential credential = new LocalPasswordCredential();
      credential.setUser("user");
      credential.setPassword(PASSWORD);
      String savedXml = write(credential);
      String savedValue = storedPassword(savedXml);
      assertTrue(savedValue.startsWith(AbstractPasswordEncryption.NEW_PREFIX));

      // another key, wrapped with the same master password, as a storage restore leaves it
      JcePasswordEncryption encryption = new JcePasswordEncryption();
      setKey(Base64.getEncoder().encodeToString(
         encryption.encryptSecretKey(encryption.createSecretKey(), encryption.getMasterKey())));
      assertNotEquals(originalKey, SreeEnv.getProperty(KEY_PROPERTY));

      LocalPasswordCredential loaded = parse(savedXml);
      assertEquals(savedValue, loaded.getPassword());

      String resavedXml = write(loaded);
      assertEquals(savedValue, storedPassword(resavedXml));

      setKey(originalKey);
      assertEquals(PASSWORD, parse(resavedXml).getPassword());
   }

   @Test
   void roundTripAndClearTextUnchanged() throws Exception {
      String encrypted = Tool.encryptPassword(PASSWORD);
      assertNotEquals(PASSWORD, encrypted);
      assertEquals(PASSWORD, Tool.decryptPassword(encrypted));
      // the same password encrypts with a new IV each time
      assertNotEquals(encrypted, Tool.encryptPassword(PASSWORD));
      // unprefixed legacy clear text
      assertEquals("plainpw", Tool.decryptPassword("plainpw"));
   }

   @Test
   void clearTextResemblingAesValueIsEncrypted() {
      String iv = Base64.getEncoder().encodeToString(new byte[16]);
      String[] clearText = {
         AbstractPasswordEncryption.NEW_PREFIX + "foo",
         AbstractPasswordEncryption.NEW_PREFIX + iv + ":abc",
         // ciphertext is not a whole number of AES blocks
         AbstractPasswordEncryption.NEW_PREFIX + iv + ":" +
            Base64.getEncoder().encodeToString(new byte[15]),
         // IV is not 16 bytes
         AbstractPasswordEncryption.NEW_PREFIX + Base64.getEncoder().encodeToString(new byte[8]) +
            ":" + Base64.getEncoder().encodeToString(new byte[16]),
         // unpadded Base64
         AbstractPasswordEncryption.NEW_PREFIX + iv.replace("=", "") + ":" +
            Base64.getEncoder().encodeToString(new byte[16])
      };

      for(String value : clearText) {
         String encrypted = Tool.encryptPassword(value);
         assertNotEquals(value, encrypted, value);
         assertEquals(value, Tool.decryptPassword(encrypted), value);
      }
   }

   private static void setKey(String key) throws IOException {
      SreeEnv.setProperty(KEY_PROPERTY, key);
      SreeEnv.save();
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

   private static final String KEY_PROPERTY = "password.encryption.key";
   private static final String PASSWORD = "s3cret";
   private String originalKey;
}
