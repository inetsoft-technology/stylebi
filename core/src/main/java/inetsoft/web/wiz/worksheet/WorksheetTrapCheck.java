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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.WSModelTrapContext;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.wiz.viewsheet.AgentTrapWarning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;

/**
 * The warn-only data-model trap check for the worksheet write tools that Composer's own worksheet
 * dialogs guard with a trap check (Bug #80958): {@code add_filter}, {@code edit_condition},
 * {@code set_conditions} (the condition dialog) and {@code set_group_aggregate} (the aggregate
 * dialog).
 *
 * <p>It compares two caller-supplied tables and never touches the live assembly. Only a NEW trap
 * (the old table was not already trapped) on a table bound to a logical model, or a mirror of
 * one, whose partition declares relationship cardinality produces a warning; every other table
 * kind is silent.
 */
public final class WorksheetTrapCheck {
   private static final Logger LOG = LoggerFactory.getLogger(WorksheetTrapCheck.class);

   private WorksheetTrapCheck() {
   }

   /**
    * @param before         the table before the write
    * @param after          the table after the write
    * @param aggregateCheck {@code true} for the aggregate dialog's context (aggregate refs are
    *                       counted), {@code false} for the condition dialog's context
    * @param user           the agent principal
    *
    * @return the agent-facing warning when {@code after} introduces a trap that {@code before}
    *         did not have, otherwise empty. Never throws.
    */
   public static Optional<String> newTrap(TableAssembly before, TableAssembly after,
                                          boolean aggregateCheck, Principal user)
   {
      return newTrap(before, after, aggregateCheck, user, null);
   }

   /**
    * The trap check Composer runs when a table is dropped onto the worksheet
    * ({@code WorksheetOpenAssetService#checkTrap}): a table that is not yet in the worksheet is
    * checked as a whole, there being no previous table to compare against.
    *
    * @param table the new table, before it is added to the worksheet
    * @param user  the agent principal
    *
    * @return the agent-facing warning when {@code table} is a trap, otherwise empty. Never
    *         throws.
    */
   public static Optional<String> newTableTrap(TableAssembly table, Principal user) {
      return newTableTrap(table, user, null);
   }

   static Optional<String> newTableTrap(TableAssembly table, Principal user,
                                        DataSourceRegistry registry)
   {
      try {
         if(table == null) {
            return Optional.empty();
         }

         DataSourceRegistry reg = registry != null ? registry : DataSourceRegistry.getRegistry();
         WSModelTrapContext ctx = new WSModelTrapContext(table, user, reg);

         if(!ctx.isCheckTrap() || !ctx.checkTrap(null, table).showWarning()) {
            return Optional.empty();
         }

         return Optional.of(AgentTrapWarning.forCondition(ctx.getTrapCondition()));
      }
      catch(Exception e) {
         // A warning-only check must never turn a write into a failure.
         LOG.warn("Worksheet data-model trap check failed; the write is not blocked", e);
         return Optional.empty();
      }
   }

   static Optional<String> newTrap(TableAssembly before, TableAssembly after,
                                   boolean aggregateCheck, Principal user,
                                   DataSourceRegistry registry)
   {
      try {
         if(before == null || after == null) {
            return Optional.empty();
         }

         // The condition dialog checks only the pre-aggregate list; a write that left it alone
         // (a filter routed to the post-aggregate list, a no-op rewrite) is not checked.
         if(!aggregateCheck &&
            Objects.equals(before.getPreConditionList(), after.getPreConditionList()))
         {
            return Optional.empty();
         }

         DataSourceRegistry reg = registry != null ? registry : DataSourceRegistry.getRegistry();
         WSModelTrapContext ctx = aggregateCheck
            ? new WSModelTrapContext(
               before, user, after.getAggregateInfo() != null && !after.getAggregateInfo().isEmpty(),
               true, reg)
            : new WSModelTrapContext(before, user, reg);

         if(!ctx.isCheckTrap() || !ctx.checkTrap(before, after).showWarning()) {
            return Optional.empty();
         }

         return Optional.of(AgentTrapWarning.forCondition(ctx.getTrapCondition()));
      }
      catch(Exception e) {
         // A warning-only check must never turn a write into a failure.
         LOG.warn("Worksheet data-model trap check failed; the write is not blocked", e);
         return Optional.empty();
      }
   }
}
