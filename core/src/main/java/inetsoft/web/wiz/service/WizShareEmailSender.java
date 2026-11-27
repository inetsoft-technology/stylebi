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

import inetsoft.uql.util.Identity;
import inetsoft.web.share.ShareController;
import inetsoft.web.share.ShareMessage;
import inetsoft.web.viewsheet.service.VSEmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.naming.NamingException;
import java.security.Principal;
import java.util.List;

/**
 * Sends a share message by email for the wiz endpoints (Redmine #82497). {@code ShareController}
 * hands the recipient text to the mailer as is, and only {@code name(User)} and
 * {@code name(Group)} entries are expanded to addresses, so any other entry that is not an
 * address ends in a {@code MessagingException}, which the wiz error handler reports as an
 * unexpected StyleBI bug. This checks the recipients first, naming the field and the entry, and
 * answers a failure of the mail transport with a fixed, handled message.
 */
public final class WizShareEmailSender {
   /** Expands recipient text to addresses, as the mailer will. */
   interface Resolver {
      List<String> resolve(String addresses, Principal user) throws Exception;
   }

   private WizShareEmailSender() {
   }

   public static void send(ShareController shares, ShareMessage message, Principal user)
      throws Exception
   {
      send(shares, message, user, (addresses, principal) ->
         VSEmailService.getEmailsList(addresses, false, principal));
   }

   static void send(ShareController shares, ShareMessage message, Principal user,
                    Resolver resolver)
      throws Exception
   {
      check("recipients", message.recipients(), true, user, resolver);
      check("ccs", message.ccs(), false, user, resolver);
      check("bccs", message.bccs(), false, user, resolver);

      try {
         shares.sendEmailMessage(message, user);
      }
      catch(MessagingException | NamingException e) {
         LOG.warn("Sharing a dashboard by email failed", e);
         throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "The share was not emailed: the mail server could not deliver the message. " +
            "The failure is logged on the server; check the mail settings and the recipient " +
            "addresses.");
      }
   }

   private static void check(String field, List<String> values, boolean required,
                             Principal user, Resolver resolver)
      throws Exception
   {
      String joined = values == null ? "" : String.join(",", values.stream()
         .filter(v -> v != null && !v.isBlank()).map(String::trim).toList());

      if(joined.isEmpty()) {
         if(required) {
            throw new IllegalArgumentException("'" + field + "' needs at least one entry.");
         }

         return;
      }

      // the same splitting VSEmailService.getEmailsList applies before it expands identities
      String[] entries = joined.replace(";", ",").replaceAll(",\s+", ",").replace(' ', ',')
         .split(",");

      for(String entry : entries) {
         if(!entry.isEmpty() && !isIdentity(entry)) {
            requireAddress(field, entry);
         }
      }

      List<String> resolved = resolver.resolve(joined, user);

      if(resolved == null || resolved.isEmpty()) {
         throw new IllegalArgumentException(
            "'" + field + "' resolves to no email address: the user or group named in " +
            "\"" + joined + "\" has no email address.");
      }
   }

   private static boolean isIdentity(String entry) {
      return entry.endsWith(Identity.USER_SUFFIX) || entry.endsWith(Identity.GROUP_SUFFIX);
   }

   private static void requireAddress(String field, String entry) {
      try {
         new InternetAddress(entry, true).validate();
      }
      catch(AddressException e) {
         throw new IllegalArgumentException(
            "'" + field + "' entry \"" + entry + "\" is not an email address. Use a full " +
            "address (name@example.com), or name" + Identity.USER_SUFFIX + " / name" +
            Identity.GROUP_SUFFIX + " for a StyleBI user or group.");
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(WizShareEmailSender.class);
}
