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
package inetsoft.web.admin.ai;

/*
 * Test strategy
 *
 * TaskAuditTokenTest stubs Tool.encryptPassword/decryptPassword so its assertions pin
 * TaskAuditToken's own logic without depending on a master key - that proves which methods are
 * called, not that the two are actually inverses. This test runs the real encryption once, under
 * the Spring/SreeHome harness the crypto needs, to hold the two halves together, mirroring
 * LogSettingServiceCryptoTest's own strategy for the same primitive.
 */

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class TaskAuditTokenCryptoTest {
   @Test
   void issuedTokenIsVerifiableByTheRealCrypto() {
      String token = TaskAuditToken.issue("hash123", "raise the row limit");

      assertNotEquals("hash123raise the row limit", token,
         "the plan hash and task must not be stored in clear text");
      assertEquals("raise the row limit", TaskAuditToken.verify(token, "hash123"));
   }

   @Test
   void verifyRejectsARealTokenIssuedForADifferentPlanHash() {
      String token = TaskAuditToken.issue("hash123", "raise the row limit");
      assertThrows(TaskAuditToken.TaskTokenException.class,
         () -> TaskAuditToken.verify(token, "hash456"));
   }
}
