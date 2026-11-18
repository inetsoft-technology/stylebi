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
package inetsoft.report.composition;

import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81431: the user messages produced while a sheet is opened on a remote owner node (for
 * example the "asset is locked" warning) were dropped on that node. {@link OpenSheetResult} carries
 * them back with the sheet id.
 */
@Tag("core")
class OpenSheetResultTest {
   @BeforeEach
   @AfterEach
   void clearMessages() {
      Tool.clearUserMessage();
   }

   @Test
   void collectsTheMessagesOfEveryTypeOfTheCurrentThread() {
      Tool.addUserMessage("warning text", MessageCommand.Type.WARNING.code());
      Tool.addUserMessage("info text", MessageCommand.Type.INFO.code());
      Tool.addUserMessage("error text", MessageCommand.Type.ERROR.code());

      OpenSheetResult result = OpenSheetResult.collect("ws-1");

      assertEquals("ws-1", result.getId());
      assertEquals(List.of("error text", "info text", "warning text"),
                   result.getMessages().stream().map(UserMessage::getMessage).sorted().toList());
   }

   @Test
   void hasNoMessagesWhenNothingWasAdded() {
      OpenSheetResult result = OpenSheetResult.collect("ws-1");

      assertTrue(result.getMessages().isEmpty());
      assertEquals("ws-1", result.applyMessages());
      assertTrue(Tool.getUserMessages(MessageCommand.Type.WARNING).isEmpty());
   }

   @Test
   void doesNotClearTheMessagesOfTheCurrentThread() {
      Tool.addUserMessage("warning text", MessageCommand.Type.WARNING.code());

      OpenSheetResult.collect("ws-1");

      assertEquals(1, Tool.getUserMessages(MessageCommand.Type.WARNING).size(),
                   "collecting must not consume the messages, postprocess() does that");
   }

   @Test
   void survivesTheTripToTheRequestNode() throws Exception {
      Tool.addUserMessage("locked", MessageCommand.Type.WARNING.code());
      OpenSheetResult sent = OpenSheetResult.collect("ws-1");

      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(sent);
      }

      OpenSheetResult received;

      try(ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
         received = (OpenSheetResult) in.readObject();
      }

      assertEquals("ws-1", received.getId());
      assertEquals(1, received.getMessages().size());
      assertEquals("locked", received.getMessages().get(0).getMessage());
      assertEquals(MessageCommand.Type.WARNING.code(), received.getMessages().get(0).getLevel());
   }

   @Test
   void appliesTheMessagesOnTheThreadThatCallsIt() throws Exception {
      // the owner node's thread produces the warning and then clears it (postprocess())
      AtomicReference<OpenSheetResult> fromOwner = new AtomicReference<>();
      Thread owner = new Thread(() -> {
         Tool.addUserMessage("locked", MessageCommand.Type.WARNING.code());
         fromOwner.set(OpenSheetResult.collect("ws-1"));
         Tool.clearUserMessage();
      });
      owner.start();
      owner.join(10_000);

      assertNotNull(fromOwner.get());
      assertTrue(Tool.getUserMessages(MessageCommand.Type.WARNING).isEmpty(),
                 "the request thread must not see the message before it is applied");

      assertEquals("ws-1", fromOwner.get().applyMessages());

      List<UserMessage> shown = Tool.getUserMessages(MessageCommand.Type.WARNING);
      assertEquals(1, shown.size());
      assertEquals("locked", shown.get(0).getMessage());
   }

   @Test
   void applyingTwiceDoesNotDuplicateAPendingMessage() {
      Tool.addUserMessage("locked", MessageCommand.Type.WARNING.code());
      OpenSheetResult result = OpenSheetResult.collect("ws-1");
      Tool.clearUserMessage();

      result.applyMessages();
      result.applyMessages();

      assertEquals(1, Tool.getUserMessages(MessageCommand.Type.WARNING).size());
   }
}
