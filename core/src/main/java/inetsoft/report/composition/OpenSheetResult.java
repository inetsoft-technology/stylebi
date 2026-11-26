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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The result of opening a runtime sheet on the node that owns it: the sheet id plus the user
 * messages (for example the "asset is locked" warning) that were produced while opening it.
 * <p>
 * The user messages are kept in a thread-local of the thread that opens the sheet, and are sent to
 * the browser from the request thread when the request ends. When the sheet is owned by another
 * node the open runs on a thread of that node, and an affinity call returns only the value of the
 * task, so the messages have to travel in the value to reach the request thread.
 */
public final class OpenSheetResult implements Serializable {
   private OpenSheetResult(String id, List<UserMessage> messages) {
      this.id = id;
      this.messages = messages;
   }

   /**
    * Create a result from the user messages of the current thread. Call it before the messages
    * are cleared by {@link inetsoft.web.ServiceProxyContext#postprocess()}.
    *
    * @param id the id of the opened sheet.
    */
   public static OpenSheetResult collect(String id) {
      List<UserMessage> messages = new ArrayList<>();

      for(MessageCommand.Type type : MessageCommand.Type.values()) {
         messages.addAll(Tool.getUserMessages(type));
      }

      return new OpenSheetResult(id, messages);
   }

   /**
    * Add the messages produced on the owner node to the user messages of the calling thread.
    *
    * @return the id of the opened sheet.
    */
   public String applyMessages() {
      for(UserMessage message : messages) {
         Tool.addUserMessage(message);
      }

      return id;
   }

   /**
    * Get the id of the opened sheet.
    */
   public String getId() {
      return id;
   }

   /**
    * Get the user messages produced while the sheet was opened.
    */
   public List<UserMessage> getMessages() {
      return messages;
   }

   @Override
   public String toString() {
      return "OpenSheetResult{id='" + id + "', messages=" + messages.size() + '}';
   }

   private final String id;
   private final List<UserMessage> messages;
   private static final long serialVersionUID = 1L;
}
