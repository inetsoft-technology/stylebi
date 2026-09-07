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

import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Stubs Tool.encryptPassword/decryptPassword so these tests pin TaskAuditToken's own issue/verify
 * logic (delimiter handling, hash binding, error cases) without depending on the master-key crypto
 * those methods wrap. TaskAuditTokenCryptoTest runs the real round trip once, under the
 * Spring/SreeHome harness the crypto needs, to hold the two halves together.
 */
@Tag("core")
class TaskAuditTokenTest {
   private MockedStatic<Tool> tool;

   @BeforeEach
   void setUp() {
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> Tool.decryptPassword(anyString()))
         .thenAnswer(inv -> {
            String s = inv.getArgument(0);

            if(!s.startsWith("TKN:")) {
               throw new IllegalArgumentException("not a token");
            }

            return s.substring(4);
         });
   }

   @AfterEach
   void tearDown() {
      tool.close();
   }

   @Test
   void verifyRecoversTheIssuedTask() {
      String token = TaskAuditToken.issue("hash123", "raise the row limit");
      assertEquals("raise the row limit", TaskAuditToken.verify(token, "hash123"));
   }

   @Test
   void verifyRejectsATokenIssuedForADifferentPlanHash() {
      String token = TaskAuditToken.issue("hash123", "raise the row limit");
      assertThrows(TaskAuditToken.TaskTokenException.class,
         () -> TaskAuditToken.verify(token, "hash456"));
   }

   @Test
   void verifyRejectsAMissingToken() {
      assertTrue(assertThrows(TaskAuditToken.TaskTokenException.class,
         () -> TaskAuditToken.verify(null, "hash123")).getMessage().startsWith("taskToken:"));
      assertThrows(TaskAuditToken.TaskTokenException.class,
         () -> TaskAuditToken.verify("   ", "hash123"));
   }

   @Test
   void verifyRejectsAnUndecryptableToken() {
      assertThrows(TaskAuditToken.TaskTokenException.class,
         () -> TaskAuditToken.verify("not-a-real-token", "hash123"));
   }

   @Test
   void issueEmbedsTaskAfterTheSeparator() {
      // Pins the wire format one layer down from verify(), so a future change to the delimiter
      // or field order is caught here even if verify() happened to still round-trip correctly.
      String token = TaskAuditToken.issue("hash123", "t");
      assertEquals("TKN:hash123" + (char) 0x1F + "t", token);
   }
}
