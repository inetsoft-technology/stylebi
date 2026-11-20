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
package inetsoft.web.wiz.controller;

import inetsoft.uql.erm.XAttribute;
import inetsoft.uql.erm.XEntity;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.web.portal.controller.database.LogicalModelController;
import inetsoft.web.portal.model.database.LogicalModelDefinition;
import inetsoft.web.portal.model.database.StringWrapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the bridge validator to the two Portal checks it re-implements because they are private
 * or live on a controller: {@code LogicalModelService.validateAttribute} (table and column) and
 * {@code LogicalModelController.checkExpressionSQL} (expression). Both run on one shared table
 * of cases; if the Portal's rule changes, a row here disagrees.
 */
class WizLogicalModelValidatorParityTest extends WizLogicalModelTestBase {
   /** table, column, whether the service reports the attribute as broken. */
   private static final Object[][] COLUMNS = {
      { "T1", "AMOUNT", false }, { "T1", "ID", false }, { "T1", "NAME", false },
      { "T1", "NOPE", true }, { "T1", "amount", true }, { "T9", "ID", true },
      { "T2", "CODE", true } };

   /**
    * expression, whether the Portal check refuses it, whether the bridge refuses it. The Portal
    * check reads the NEXT token of the lexer, but the parser has already buffered its lookahead,
    * so it only ever sees an exception thrown mid-parse and accepts a trailing operator, an
    * unclosed function call, an alias-like trailing word and a stray ")" (P4 D-2). The bridge
    * asks the parser what it has not consumed, so it refuses exactly those. Everything else
    * agrees. The Portal column pins the Portal's behavior: if it is fixed, this row changes.
    */
   private static final Object[][] EXPRESSIONS = {
      { "T1.AMOUNT * 2", false, false }, { "1", false, false },
      { "case when T1.ID = 1 then 2 else 3 end", false, false },
      { "upper(T1.NAME)", false, false }, { "(T1.ID + 1) * 3", false, false },
      { "T1.AMOUNT T1.ID", true, true }, { "(T1.AMOUNT", true, true },
      { "((1 +", true, true },
      { "1 +", false, true }, { "foo(", false, true }, { "T1.AMOUNT +", false, true },
      { "T1.AMOUNT garbage", false, true }, { "T1.AMOUNT )", false, true },
      { "T1.AMOUNT *", false, true }, { "1 2", false, true } };

   @Test
   void columnVerdictAgreesWithTheServicesOwnErrorMessage() throws Exception {
      for(Object[] row : COLUMNS) {
         String model = "parity_" + row[0] + "_" + row[1];
         XLogicalModel stored = new XLogicalModel(model);
         stored.setPartition("pv");
         XEntity entity = new XEntity("E");
         entity.addAttribute(new XAttribute("A", (String) row[0], (String) row[1], "string"));
         stored.addEntity(entity);
         dataModel.addLogicalModel(stored);

         LogicalModelDefinition read = modelService.getModel(ds, "pv", model, null, bob);
         boolean serviceSaysBroken = read.getEntities().get(0).getAttributes().get(0)
            .getErrorMessage() != null;
         WizLogicalModelValidator.Result result = new WizLogicalModelValidator().validate(
            new WizLogicalModelEnv(ds, dataModel, dataSourceService),
            def(model, entity("E", col("A", (String) row[0], (String) row[1]))),
            WizLogicalModelValidator.Mode.CREATE, null);

         assertEquals(row[2], serviceSaysBroken,
                      "premise: the service's verdict for " + row[0] + "." + row[1]);
         assertEquals(serviceSaysBroken, !result.valid(),
                      "the validator agrees with the service for " + row[0] + "." + row[1] +
                         ": " + result.errors());
      }
   }

   @Test
   void expressionVerdictIsPinnedToThePortalCheckAndTheBridgeIsStricterOnlyWhereListed() {
      LogicalModelController portal = new LogicalModelController(null, null, null, null, null,
                                                                 null);
      List<String> wrong = new ArrayList<>();

      for(Object[] row : EXPRESSIONS) {
         String expression = (String) row[0];
         StringWrapper wrapper = new StringWrapper();
         wrapper.setBody(expression);
         boolean portalBad = portal.checkExpressionSQL(wrapper) != null;
         boolean bridgeBad = WizLogicalModelValidator.expressionProblem(expression) != null;

         if(portalBad != (Boolean) row[1] || bridgeBad != (Boolean) row[2]) {
            wrong.add(expression + " (portal bad=" + portalBad + ", bridge bad=" + bridgeBad +
                         ")");
         }
      }

      assertTrue(wrong.isEmpty(), "unexpected verdicts: " + wrong);
   }

   @Test
   void theTableIsNotAllGoodOrAllBad() {
      long bad = java.util.Arrays.stream(COLUMNS).filter(r -> (Boolean) r[2]).count();
      assertTrue(bad > 0 && bad < COLUMNS.length);
      long badExpressions = java.util.Arrays.stream(EXPRESSIONS).filter(r -> (Boolean) r[2])
         .count();
      assertTrue(badExpressions > 0 && badExpressions < EXPRESSIONS.length,
                 "the expression table has both parseable and unparseable rows");
   }
}
