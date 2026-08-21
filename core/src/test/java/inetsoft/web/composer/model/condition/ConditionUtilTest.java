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
package inetsoft.web.composer.model.condition;

import inetsoft.test.*;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.DateCondition;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.wiz.viewsheet.ConditionVocabulary;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link ConditionVocabulary#toConditionList} builds each condition value with the same type
 * discriminator that {@link ConditionUtil#fromModelToConditionList} switches on; if the two ever
 * disagree, the DATE_IN branch below silently falls through instead of failing loud.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class ConditionUtilTest {
   @Test
   void aNamedBuiltinDateRangeFromTheVocabularyBecomesARealDateCondition() throws Exception {
      DataRefModel field = mock(DataRefModel.class);
      when(field.getName()).thenReturn("OrderDate");
      when(field.getDataType()).thenReturn(XSchema.DATE);

      String builtinName = DateCondition.getBuiltinDateConditions()[0].getName();

      Object[] model = ConditionVocabulary.toConditionList(
         List.of(new ConditionVocabulary.Clause(
            "OrderDate", "date_in", List.of(builtinName), null, false)),
         new DataRefModel[]{ field });

      Principal principal = () -> "admin";
      ConditionList conditions =
         ConditionUtil.fromModelToConditionList(model, null, null, principal);

      assertInstanceOf(DateCondition.class, conditions.getXCondition(0),
                        "a date_in value naming a builtin range must resolve to a real " +
                        "DateCondition, not fall through to a plain value condition");
   }
}
