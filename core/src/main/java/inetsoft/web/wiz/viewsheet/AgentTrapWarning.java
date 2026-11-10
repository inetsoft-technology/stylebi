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
package inetsoft.web.wiz.viewsheet;

/**
 * The single producer of the agent-facing data-model trap warning text (Bug #80476). Every
 * trap warning a wiz write returns begins with {@link #PREFIX}, so the plugin recognizes it by that
 * fixed English prefix and does not frame it as a script or chart failure.
 *
 * <p>The write is always applied: the agent surface warns where the Composer UI would ask the
 * user to confirm, and {@code undo} is the equivalent of the Composer's "undo" button.
 */
public final class AgentTrapWarning {
   private AgentTrapWarning() {
   }

   /** The fixed prefix every data-model trap warning starts with. */
   public static final String PREFIX = "Data-model trap: ";

   private static final String APPLIED = "The write was applied; use undo to revert it.";

   /**
    * Warning for a binding write: the command Composer sends carries only the grayed-out fields,
    * not the tables that form the trap, so the text is generic.
    */
   public static String genericTrap() {
      return PREFIX + "this change introduces a new fan/chasm trap in the logical model " +
         "(Composer would ask to confirm). " + APPLIED;
   }

   /**
    * Warning naming the trap, e.g. {@code "Chasm Trap is on Orders, Returns, Customer"}.
    */
   public static String forCondition(String condition) {
      String text = condition == null ? "" : condition.trim();
      return PREFIX + (text.isEmpty() ? "this change introduces a new fan/chasm trap" : text) +
         ". " + APPLIED;
   }

   /** True if {@code message} was produced by this class. */
   public static boolean isTrapWarning(String message) {
      return message != null && message.startsWith(PREFIX);
   }
}
