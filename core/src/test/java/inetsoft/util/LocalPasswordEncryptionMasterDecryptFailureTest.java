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

import inetsoft.util.config.InetsoftConfig;
import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77628: a {@code \master} value encrypted with another master password (e.g. in an export
 * from another server) cannot be decrypted. It is still returned as is, so that the connectors
 * checking for the prefix keep working, but the failure is counted while an import counts them.
 */
@Tag("core")
class LocalPasswordEncryptionMasterDecryptFailureTest {
   @BeforeEach
   void setUp() {
      // Bug #77845: an earlier class can leave InetsoftConfig.BOOTSTRAP_INSTANCE loaded (a lazy
      // InetsoftConfig.getInstance()), with a masterPasswordCheck that doesn't match TARGET_MASTER
      bootstrapConfig = InetsoftConfig.BOOTSTRAP_INSTANCE;
      InetsoftConfig.BOOTSTRAP_INSTANCE = null;
      LocalPasswordEncryption.masterPassword.set(TARGET_MASTER.toCharArray());
      target = new JcePasswordEncryption();
      foreign = ForeignMasterSecret.encrypt(SOURCE_MASTER, "s3cret");
   }

   @AfterEach
   void tearDown() {
      PasswordEncryption.setMasterDecryptFailures(null);
      LocalPasswordEncryption.masterPassword.remove();
      InetsoftConfig.BOOTSTRAP_INSTANCE = bootstrapConfig;
   }

   @Test
   void foreignMasterValueIsReturnedAsIsAndCounted() {
      AtomicInteger failures = new AtomicInteger();
      PasswordEncryption.setMasterDecryptFailures(failures);

      assertEquals(foreign, target.decryptPassword(foreign));
      assertEquals(foreign, target.decryptMasterPassword(foreign));
      assertEquals(foreign, target.decryptPassword(foreign, null));
      assertEquals(3, failures.get());
   }

   @Test
   void foreignMasterValueIsReturnedAsIsWhenNotCounting() {
      assertNull(PasswordEncryption.getMasterDecryptFailures());
      assertEquals(foreign, target.decryptPassword(foreign));
   }

   @Test
   void decryptableValuesAreNotCounted() {
      AtomicInteger failures = new AtomicInteger();
      PasswordEncryption.setMasterDecryptFailures(failures);
      String own = target.encryptMasterPassword("s3cret");

      assertEquals("s3cret", target.decryptPassword(own));
      // unprefixed legacy clear text
      assertEquals("plainpw", target.decryptPassword("plainpw"));
      assertEquals(0, failures.get());
   }

   @Test
   void aesValueInDecryptMasterPasswordIsNotCounted() {
      AtomicInteger failures = new AtomicInteger();
      PasswordEncryption.setMasterDecryptFailures(failures);
      String aes = AbstractPasswordEncryption.NEW_PREFIX + "AAAAAAAAAAAAAAAAAAAAAA==:AAAAAAAAAAAAAAAAAAAAAA==";

      // the \aes branch is unchanged, it still returns the input
      assertEquals(aes, target.decryptMasterPassword(aes));
      assertEquals(0, failures.get());
   }

   @Test
   void counterIsPerThread() throws Exception {
      AtomicInteger failures = new AtomicInteger();
      PasswordEncryption.setMasterDecryptFailures(failures);
      Thread thread = new Thread(() -> target.decryptPassword(foreign));
      thread.start();
      thread.join();

      assertEquals(0, failures.get());
   }

   private static final String SOURCE_MASTER = "source-master-pw";
   private static final String TARGET_MASTER = "target-master-pw";
   private JcePasswordEncryption target;
   private String foreign;
   private InetsoftConfig bootstrapConfig;
}
