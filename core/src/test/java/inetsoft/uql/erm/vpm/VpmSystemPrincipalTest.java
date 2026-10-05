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
package inetsoft.uql.erm.vpm;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.HiddenColumns;
import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.uql.jdbc.XExpression;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77559: the system user ({@link XPrincipal#SYSTEM}) is deliberately NOT exempt from VPM.
 * {@link VirtualPrivateModel#evaluate}, {@link VpmCondition#evaluate} and
 * {@link HiddenColumns#evaluate} must apply the VPM to the system principal exactly as to any
 * other principal, and there must be no name-based bypass for a principal named
 * {@code INETSOFT_SYSTEM}. The opt-out for SYSTEM-built data is the per-asset bypassVPM flag.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VpmSystemPrincipalTest {
   private static final String PARTITION = "SALES_PARTITION";

   static Stream<Arguments> systemPrincipals() {
      Supplier<Principal> xPrincipal =
         () -> new XPrincipal(new IdentityID(XPrincipal.SYSTEM, "host-org"));
      Supplier<Principal> xPrincipalNullOrg =
         () -> new XPrincipal(new IdentityID(XPrincipal.SYSTEM, null));
      // the principal the scheduler and MV creation use for SYSTEM-owned work
      Supplier<Principal> srPrincipal =
         () -> SUtil.getPrincipal(new IdentityID(XPrincipal.SYSTEM, "host-org"), null, false);
      // a bare java.security.Principal whose name is exactly "INETSOFT_SYSTEM"
      Supplier<Principal> bareName = () -> () -> XPrincipal.SYSTEM;

      return Stream.of(
         Arguments.of("XPrincipal(SYSTEM, org)", xPrincipal),
         Arguments.of("XPrincipal(SYSTEM, null org)", xPrincipalNullOrg),
         Arguments.of("SRPrincipal from SUtil.getPrincipal", srPrincipal),
         Arguments.of("bare-name Principal", bareName));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("systemPrincipals")
   void virtualPrivateModelIsAppliedToSystemPrincipal(String label, Supplier<Principal> user)
      throws Exception
   {
      VirtualPrivateModel vpm = new VirtualPrivateModel("vpm77559");
      VpmCondition cond = new VpmCondition("cond");
      cond.setType(VpmCondition.PHYSICMODEL);
      cond.setTable(PARTITION);
      vpm.addCondition(cond);

      assertTrue(vpm.evaluate(new String[] { "ORDERS" }, new String[] { "ORDERS.REGION" },
                              new VariableTable(), user.get(), PARTITION, null, false),
                 "VPM must be applied to " + label);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("systemPrincipals")
   void vpmConditionBuildsWhereForSystemPrincipal(String label, Supplier<Principal> user)
      throws Exception
   {
      VpmCondition cond = new VpmCondition("cond");
      cond.setType(VpmCondition.TABLE);
      cond.setTable("ORDERS");
      cond.setCondition(new XBinaryCondition(
         new XExpression("ORDERS.REGION", XExpression.FIELD),
         new XExpression("East", XExpression.VALUE), "="));

      String where = cond.evaluate(null, new String[] { "ORDERS" }, new String[] { "ORDERS" },
                                   new String[] { "ORDERS.REGION" }, null,
                                   new VariableTable(), user.get(), false);

      assertNotNull(where, "VPM condition must produce a WHERE clause for " + label);
      assertTrue(where.contains("REGION") && where.contains("East"),
                 "unexpected VPM condition for " + label + ": " + where);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("systemPrincipals")
   void hiddenColumnsAreHiddenFromSystemPrincipal(String label, Supplier<Principal> user)
      throws Exception
   {
      HiddenColumns hidden = new HiddenColumns();
      hidden.addHiddenColumn(new AttributeRef("ORDERS", "AMOUNT"));

      String[] result = hidden.evaluate(new String[] { "ORDERS" },
                                        new String[] { "ORDERS.AMOUNT" },
                                        new VariableTable(), user.get(), false, null);

      assertEquals(1, result.length, "hidden column must stay hidden from " + label);
   }
}
