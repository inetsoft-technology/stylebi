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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSModelTrapContext;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * The one explicit pre-write data-model trap check the wiz services share (Bug #80476).
 *
 * <p>It compares two caller-supplied info objects and never touches the live assembly, unlike the
 * native {@code checkVSTableTrap}/{@code checkTrapForCalc}, which swap the live info in and
 * out without a {@code finally}. Callers pass clones.
 */
public final class AgentTrapCheck {
   private static final Logger LOG = LoggerFactory.getLogger(AgentTrapCheck.class);

   private AgentTrapCheck() {
   }

   /**
    * @return the trap description (e.g. {@code "Chasm Trap is on A, B, C"}) when {@code newInfo}
    *         introduces a trap that {@code oldInfo} did not have, otherwise empty.
    */
   public static Optional<String> newTrap(RuntimeViewsheet rvs, DataSourceRegistry registry,
                                          VSAssemblyInfo oldInfo, VSAssemblyInfo newInfo)
   {
      try {
         return newTrap(new VSModelTrapContext(rvs, registry), oldInfo, newInfo);
      }
      catch(RuntimeException e) {
         // A warning-only check must never turn a write into a failure.
         LOG.warn("Data-model trap check failed; the write is not blocked", e);
         return Optional.empty();
      }
   }

   static Optional<String> newTrap(VSModelTrapContext context, VSAssemblyInfo oldInfo,
                                   VSAssemblyInfo newInfo)
   {
      if(!context.isCheckTrap() || !context.checkTrap(oldInfo, newInfo).showWarning()) {
         return Optional.empty();
      }

      return Optional.of(context.getTrapCondition());
   }
}
