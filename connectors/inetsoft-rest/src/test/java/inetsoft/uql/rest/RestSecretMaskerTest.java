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
package inetsoft.uql.rest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RestSecretMaskerTest {
   private static final String SIG = "FAKEsig+/value=";

   @Test
   void masksSasSignatureAndKeepsStatusAndCode() {
      final String msg = "Http request for resource https://acct.blob.core.windows.net/c?sv=2022" +
         "&spr=https&sig=FAKEsig%2B%2Fvalue%3D&restype=container was not successful: 403 Forbidden" +
         "\n<Error><Code>AuthenticationFailed</Code></Error>";
      final String out = RestSecretMasker.mask(msg, List.of(SIG));
      assertFalse(out.contains("FAKEsig"), out);
      assertTrue(out.contains("sig=***"), out);
      assertTrue(out.contains("403 Forbidden"), out);
      assertTrue(out.contains("AuthenticationFailed"), out);
      assertTrue(out.contains("restype=container"), out);
   }

   @Test
   void masksPasswordPropertyValueByValueEvenWithOddParameterName() {
      final String out = RestSecretMasker.mask(
         "https://h/x?weird=FAKEsig%2B%2Fvalue%3D&a=1 and body FAKEsig+/value=", List.of(SIG));
      assertFalse(out.contains("FAKEsig"), out);
      assertTrue(out.contains("a=1"), out);
   }

   @Test
   void masksCommonCredentialParametersByName() {
      final String out = RestSecretMasker.mask(
         "https://h/x?api_key=AAA111&q=1&access_token=BBB222&code=CCC333&" +
            "X-Amz-Signature=DDD444&key=EEE555", null);
      for(String s : new String[] { "AAA111", "BBB222", "CCC333", "DDD444", "EEE555" }) {
         assertFalse(out.contains(s), out);
      }

      assertTrue(out.contains("q=1"), out);
   }

   @Test
   void masksUserInfo() {
      final String out = RestSecretMasker.mask("https://bob:hunter2pw@host/p", null);
      assertFalse(out.contains("hunter2pw"), out);
      assertTrue(out.contains("bob"), out);
   }

   @Test
   void leavesBenignTextAlone() {
      final String in = "https://h/x?monkey=1&compass=north&page=2 was not successful: 404";
      assertEquals(in, RestSecretMasker.mask(in, List.of()));
   }
}
