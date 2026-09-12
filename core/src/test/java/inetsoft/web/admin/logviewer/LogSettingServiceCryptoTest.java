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
 * LogSettingServiceTest stubs Tool.encryptPassword and Tool.decryptPassword so its
 * assertions do not depend on a master key, which means it pins which method is called but
 * not that the two are actually inverses. That is the invariant the Fluentd shared key bug
 * turned on: the value this service stores has to be recoverable by whatever reads it, and
 * the reader that configures the collector connection (ForwardService.getSecret, in the
 * enterprise module) calls Tool.decryptPassword. This test runs the real encryption once,
 * under the Spring/SreeHome harness the crypto needs, to hold the two halves together.
 *
 * Behavioral guarantees covered:
 *
 * [G7] What toPassword stores is not the clear text, and Tool.decryptPassword recovers it
 *      exactly. If the write side moves to an encryption whose inverse is no longer
 *      Tool.decryptPassword, the digest the collector is asked to match goes wrong again
 *      and this fails, where the stubbed suites would stay green.
 */

import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.util.Tool;
import inetsoft.util.log.LogManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class LogSettingServiceCryptoTest {
   // [G7] the stored form is encrypted at rest and the runtime reader recovers it
   @Test
   void storedCredentialIsRecoverableByTheRuntimeReader() {
      LogSettingService service =
         new LogSettingService(mock(SecurityEngine.class), mock(LogManager.class));

      // toPassword is what setFluentdSettings stores for both the shared key and the
      // password; the surrounding whitespace also checks the trim survives the round trip
      String stored = ReflectionTestUtils.invokeMethod(service, "toPassword", "  secret-key  ");

      assertNotNull(stored);
      assertNotEquals("secret-key", stored, "the credential must not be stored in clear text");
      assertEquals("secret-key", Tool.decryptPassword(stored));
   }
}
