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
package inetsoft.report.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ExpressionValue;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.ScriptStateLint;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feature #77123 (review r1 M2): the condition-site hook in {@link ConditionGroup} warns once for
 * a condition script that reads its own state before writing it, with the pool off and on.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ConditionGroupStateLintTest {
   @BeforeEach
   void setUp() {
      logger = (Logger) LoggerFactory.getLogger(ScriptStateLint.LOGGER_NAME);
      level = logger.getLevel();
      logger.setLevel(Level.WARN);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      logger.detachAppender(appender);
      logger.setLevel(level);
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void conditionAccumulatorWarnsOnce(boolean pool) throws Exception {
      String name = "n_" + UUID.randomUUID().toString().replace('-', '_');
      String exp = "var " + name + "; " + name + " = (" + name + " || 0) + 1; 2";
      DefaultTableLens table = new DefaultTableLens(new Object[][] {{"value"}, {2}, {3}});

      for(int i = 0; i < 3; i++) {
         AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
         ConditionGroup group = new ConditionGroup(table, list(exp), box);
         assertTrue(group.evaluate(table, 1), "the condition result changed");
         assertFalse(group.evaluate(table, 2), "the condition result changed");
      }

      List<ILoggingEvent> warns = warnings(name);
      assertEquals(1, warns.size(), "pool " + pool);
      String msg = warns.get(0).getFormattedMessage();
      assertTrue(msg.contains("a condition script"), msg);
      assertTrue(msg.contains("rule R1"), msg);
   }

   @Test
   void safeConditionDoesNotWarn() throws Exception {
      String name = "v_" + UUID.randomUUID().toString().replace('-', '_');
      String exp = "var " + name + " = 1; " + name + " + 1";
      DefaultTableLens table = new DefaultTableLens(new Object[][] {{"value"}, {2}, {3}});
      ConditionGroup group = new ConditionGroup(table, list(exp), PoolTestSupport.poolBox(false));

      assertTrue(group.evaluate(table, 1));
      assertEquals(0, warnings(name).size());
   }

   private static ConditionList list(String exp) {
      AssetCondition condition = new AssetCondition();
      condition.setOperation(XCondition.EQUAL_TO);
      condition.setType(XSchema.INTEGER);
      ExpressionValue value = new ExpressionValue();
      value.setExpression(exp);
      value.setType(ExpressionValue.JAVASCRIPT);
      condition.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(null, "value"), condition, 0));
      return list;
   }

   private List<ILoggingEvent> warnings(String marker) {
      return appender.list.stream()
         .filter(e -> e.getLevel() == Level.WARN && e.getFormattedMessage().contains(marker))
         .toList();
   }

   private Logger logger;
   private Level level;
   private ListAppender<ILoggingEvent> appender;
}
