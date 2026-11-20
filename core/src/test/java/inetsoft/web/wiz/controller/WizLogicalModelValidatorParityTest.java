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

   private static final String[] EXPRESSIONS = {
      "T1.AMOUNT * 2", "T1.AMOUNT +", "T1.AMOUNT T1.ID", "(T1.AMOUNT", "1", "T1.AMOUNT, 2",
      "case when T1.ID = 1 then 2 else 3 end", "T1.AMOUNT )" };

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
   void expressionVerdictAgreesWithTheControllerCheck() {
      LogicalModelController portal = new LogicalModelController(null, null, null, null, null,
                                                                 null);
      List<String> disagreements = new ArrayList<>();

      for(String expression : EXPRESSIONS) {
         StringWrapper wrapper = new StringWrapper();
         wrapper.setBody(expression);
         boolean portalSaysBad = portal.checkExpressionSQL(wrapper) != null;
         boolean bridgeSaysBad = WizLogicalModelValidator.expressionProblem(expression) != null;

         if(portalSaysBad != bridgeSaysBad) {
            disagreements.add(expression + " (portal bad=" + portalSaysBad + ")");
         }
      }

      assertTrue(disagreements.isEmpty(), "the bridge and the controller disagree on: " +
         disagreements);
   }

   @Test
   void theTableIsNotAllGoodOrAllBad() {
      long bad = java.util.Arrays.stream(COLUMNS).filter(r -> (Boolean) r[2]).count();
      assertTrue(bad > 0 && bad < COLUMNS.length);
      long badExpressions = java.util.Arrays.stream(EXPRESSIONS)
         .filter(e -> WizLogicalModelValidator.expressionProblem(e) != null).count();
      assertTrue(badExpressions > 0 && badExpressions < EXPRESSIONS.length,
                 "the expression table has both parseable and unparseable rows");
   }
}
