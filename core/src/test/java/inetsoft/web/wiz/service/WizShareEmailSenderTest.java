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
package inetsoft.web.wiz.service;

import inetsoft.web.share.ShareController;
import inetsoft.web.share.ShareMessage;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Redmine #82497: a share by email whose recipients are not addresses, or whose mail transport
 * fails, is answered with a handled message instead of an unexpected server error.
 */
@Tag("core")
class WizShareEmailSenderTest {
   private final ShareController shares = mock(ShareController.class);
   private final Principal user = mock(Principal.class);

   /** Expands (User)/(Group) entries to one address each and keeps the rest, like the server. */
   private static final WizShareEmailSender.Resolver RESOLVER = (text, principal) ->
      Arrays.stream(text.replace(';', ',').replaceAll(",\s+", ",").replace(' ', ',')
                       .split(","))
         .map(e -> e.endsWith("(User)") ? e.replace("(User)", "@users.test") : e)
         .toList();

   private static ShareMessage message(List<String> to, List<String> cc, List<String> bcc) {
      return ShareMessage.builder().link("http://h/l").message("hi").recipients(to).ccs(cc)
         .bccs(bcc).build();
   }

   private void send(List<String> to, List<String> cc, List<String> bcc) throws Exception {
      WizShareEmailSender.send(shares, message(to, cc, bcc), user, RESOLVER);
   }

   @Test
   void aBareNameIsRefusedNamingTheFieldAndNothingIsSent() throws Exception {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> send(List.of("admin"), null, null));

      assertTrue(e.getMessage().contains("'recipients'"), e.getMessage());
      assertTrue(e.getMessage().contains("\"admin\""), e.getMessage());
      verifyNoInteractions(shares);
   }

   @Test
   void aBadCcOrBccIsRefusedNamingThatField() throws Exception {
      IllegalArgumentException cc = assertThrows(IllegalArgumentException.class,
         () -> send(List.of("a@x.com"), List.of("nobody"), null));
      IllegalArgumentException bcc = assertThrows(IllegalArgumentException.class,
         () -> send(List.of("a@x.com"), null, List.of("b@x.com", "nobody")));

      assertTrue(cc.getMessage().contains("'ccs'"), cc.getMessage());
      assertTrue(bcc.getMessage().contains("'bccs'"), bcc.getMessage());
      verifyNoInteractions(shares);
   }

   @Test
   void displayNameFormIsRefusedBecauseTheServerSplitsItOnTheSpace() throws Exception {
      assertThrows(IllegalArgumentException.class,
                   () -> send(List.of("Ann <ann@x.com>"), null, null));
   }

   @Test
   void identitiesAndPlainAddressesAreSent() throws Exception {
      send(List.of("a@x.com", " ann(User) "), List.of("c@x.com;d@x.com"), List.of());

      verify(shares).sendEmailMessage(any(), any());
   }

   @Test
   void addressesWithSymbolsTheNativeDialogAcceptsAreStillSent() throws Exception {
      send(List.of("a&b@x.com", "a=b@x.com", "a%b@x.com", "o'brien@x.com"), null, null);

      verify(shares).sendEmailMessage(any(), any());
   }

   @Test
   void anIdentityWithNoEmailAddressIsRefused() throws Exception {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> WizShareEmailSender.send(shares, message(List.of("ghost(User)"), null, null), user,
                                        (text, principal) -> List.of()));

      assertTrue(e.getMessage().contains("'recipients'"), e.getMessage());
      verifyNoInteractions(shares);
   }

   @Test
   void aRecipientListWithoutEntriesIsRefused() throws Exception {
      assertThrows(IllegalArgumentException.class, () -> send(null, null, null));
      assertThrows(IllegalArgumentException.class, () -> send(List.of(" "), null, null));
   }

   @Test
   void aMailTransportFailureIsAFixedBadGatewayWithoutTheMailHost() throws Exception {
      doThrow(new MessagingException("mail.example.com: connect timed out"))
         .when(shares).sendEmailMessage(any(), any());

      ResponseStatusException e = assertThrows(ResponseStatusException.class,
         () -> send(List.of("a@x.com"), null, null));

      assertEquals(HttpStatus.BAD_GATEWAY, e.getStatusCode());
      assertFalse(e.getReason().contains("mail.example.com"), e.getReason());
      assertTrue(e.getReason().contains("not emailed"), e.getReason());
   }

   @Test
   void otherFailuresPassThroughUnchanged() throws Exception {
      doThrow(new IllegalStateException("Sharing with email is not enabled"))
         .when(shares).sendEmailMessage(any(), any());

      assertThrows(IllegalStateException.class, () -> send(List.of("a@x.com"), null, null));
   }
}
