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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77722: the {@code \aes} decrypt-failure handling lives in {@link LocalPasswordEncryption},
 * so the JCE and the FIPS implementations must both return a value that cannot be decrypted
 * (wrong key, or a wrong key that passes the CBC padding check) as is, still decrypt values
 * encrypted with the right key (including non-ASCII text), and never log the value.
 */
@Tag("core")
class LocalPasswordEncryptionSecretKeyProviderTest {
   static Stream<Named<Supplier<LocalPasswordEncryption>>> providers() {
      return Stream.of(
         Named.of("JCE", JcePasswordEncryption::new),
         Named.of("FIPS", FipsPasswordEncryption::new));
   }

   @BeforeEach
   void setUp() {
      LocalPasswordEncryption.masterPassword.set(MASTER.toCharArray());
      logger = (Logger) LoggerFactory.getLogger(LocalPasswordEncryption.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      logger.detachAppender(appender);
      LocalPasswordEncryption.masterPassword.remove();
   }

   @ParameterizedTest
   @MethodSource("providers")
   void rightKeyRoundTripsAnyText(Supplier<LocalPasswordEncryption> provider) throws Exception {
      LocalPasswordEncryption encryption = provider.get();
      SecretKey key = encryption.createSecretKey();
      String wrapped = wrap(encryption, key);
      String[] passwords = {
         "s3cret", "pässwörd", "密码", "emoji 😀", "a", " ",
         "\\aes", "\\aes:", "\\master", "x".repeat(1000)
      };

      for(String password : passwords) {
         String value = encryptWith(encryption, key, password);
         assertEquals(password, encryption.decryptPassword(value, wrapped), password);
      }

      assertTrue(appender.list.isEmpty(), "no warning expected for the right key");
   }

   @ParameterizedTest
   @MethodSource("providers")
   void wrongKeyReturnsPrefixedInput(Supplier<LocalPasswordEncryption> provider) throws Exception {
      LocalPasswordEncryption encryption = provider.get();
      String value = encryptWith(encryption, encryption.createSecretKey(), PASSWORD);
      String wrongKey = wrap(encryption, encryption.createSecretKey());

      assertEquals(value, encryption.decryptPassword(value, wrongKey));
   }

   @ParameterizedTest
   @MethodSource("providers")
   void wrongKeyPassingPaddingCheckIsRejected(Supplier<LocalPasswordEncryption> provider)
      throws Exception
   {
      LocalPasswordEncryption encryption = provider.get();
      SecretKey key = encryption.createSecretKey();
      String value = encryptWith(encryption, key, PASSWORD);
      String[] parts = value.substring(AbstractPasswordEncryption.NEW_PREFIX.length()).split(":");
      byte[] iv = Base64.getDecoder().decode(parts[0]);
      byte[] encrypted = Base64.getDecoder().decode(parts[1]);
      SecureRandom random = new SecureRandom();
      List<SecretKey> paddingValidKeys = new ArrayList<>();

      // about one wrong key in 256 decrypts without a padding error
      for(int i = 0; i < 20000 && paddingValidKeys.size() < 5; i++) {
         byte[] keyData = new byte[16];
         random.nextBytes(keyData);
         SecretKey wrongKey = new SecretKeySpec(keyData, "AES");

         try {
            encryption.decrypt(encrypted, iv, wrongKey);
            paddingValidKeys.add(wrongKey);
         }
         catch(Exception ignore) {
            // wrong key detected by the padding check
         }
      }

      assertFalse(paddingValidKeys.isEmpty(), "no wrong key passed the padding check");

      for(SecretKey wrongKey : paddingValidKeys) {
         assertEquals(value, encryption.decryptPassword(value, wrap(encryption, wrongKey)));
      }
   }

   @ParameterizedTest
   @MethodSource("providers")
   void warningDoesNotContainTheValue(Supplier<LocalPasswordEncryption> provider)
      throws Exception
   {
      Level level = logger.getLevel();
      LocalPasswordEncryption encryption = provider.get();
      String value = encryptWith(encryption, encryption.createSecretKey(), PASSWORD);
      String wrongKey = wrap(encryption, encryption.createSecretKey());
      String[] parts = value.substring(AbstractPasswordEncryption.NEW_PREFIX.length()).split(":");

      try {
         // debug level also attaches the exception, check its messages too
         for(Level testLevel : new Level[] { Level.INFO, Level.DEBUG }) {
            logger.setLevel(testLevel);
            appender.list.clear();
            assertEquals(value, encryption.decryptPassword(value, wrongKey));
            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.get(0);
            assertEquals(Level.WARN, event.getLevel());
            List<String> texts = new ArrayList<>();
            texts.add(event.getFormattedMessage());

            for(IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
               texts.add(String.valueOf(t.getMessage()));
            }

            for(String text : texts) {
               assertFalse(text.contains(PASSWORD), text);
               assertFalse(text.contains(parts[0]), text);
               assertFalse(text.contains(parts[1]), text);
               assertFalse(text.contains(wrongKey), text);
            }

            assertFalse(event.getFormattedMessage().contains("clear text"));
         }
      }
      finally {
         logger.setLevel(level);
      }
   }

   private static String encryptWith(LocalPasswordEncryption encryption, SecretKey key,
                                     String password) throws Exception
   {
      byte[][] result = encryption.encrypt(password.getBytes(StandardCharsets.UTF_16), key);
      Base64.Encoder encoder = Base64.getEncoder();
      return AbstractPasswordEncryption.NEW_PREFIX + encoder.encodeToString(result[1]) + ":" +
         encoder.encodeToString(result[0]);
   }

   private static String wrap(LocalPasswordEncryption encryption, SecretKey key) {
      return Base64.getEncoder().encodeToString(
         encryption.encryptSecretKey(key, encryption.getMasterKey()));
   }

   private static final String MASTER = "bug-77722-master-pw";
   private static final String PASSWORD = "s3cret";
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;
}
