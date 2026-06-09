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

import inetsoft.uql.XCondition;
import inetsoft.web.wiz.model.WorksheetConstructionModel.FilterOperator;

/**
 * Maps a worksheet-model {@link FilterOperator} to a StyleBI {@link XCondition} operation.
 *
 * <p>Standalone, pure, and state-free so the mapping is unit testable without bootstrapping
 * {@link GenerateWsService}. The notable fix here is {@code IN -> ONE_OF}: it previously mapped to
 * {@code CONTAINS} (a single-value substring match), which only honored the first value of a
 * multi-value IN filter.
 */
final class WizFilterOperation {
   private WizFilterOperation() {
   }

   /**
    * The XCondition operation code for a worksheet filter operator. {@code IN} maps to
    * {@link XCondition#ONE_OF} (multi-value in-list); {@code GE}/{@code LE} map to the strict
    * comparison op and rely on {@link #isInclusiveBound} to add the equal flag.
    */
   static int operation(FilterOperator op) {
      return switch(op) {
         case EQ -> XCondition.EQUAL_TO;
         case GT, GE -> XCondition.GREATER_THAN;
         case LT, LE -> XCondition.LESS_THAN;
         case IN -> XCondition.ONE_OF;
         case BETWEEN -> XCondition.BETWEEN;
         case LIKE -> XCondition.LIKE;
      };
   }

   /**
    * {@code GE} (>=) and {@code LE} (<=) are inclusive bounds, expressed as GREATER_THAN/LESS_THAN
    * with the XCondition "equal" flag set — a single condition item, not two.
    */
   static boolean isInclusiveBound(FilterOperator op) {
      return op == FilterOperator.GE || op == FilterOperator.LE;
   }
}
