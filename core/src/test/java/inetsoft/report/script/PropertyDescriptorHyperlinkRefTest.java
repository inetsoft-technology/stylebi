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
package inetsoft.report.script;

import inetsoft.report.Hyperlink;
import inetsoft.test.*;
import inetsoft.util.script.JavaScriptEngine;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80976: converting a script object/array to a {@link Hyperlink.Ref} must accept
 * Boolean flags and non-String text values as delivered by the script engine.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PropertyDescriptorHyperlinkRefTest {
   private Context ctx;

   @BeforeEach
   void setUp() {
      ctx = Context.newBuilder("js").allowAllAccess(true).build();
   }

   @AfterEach
   void tearDown() {
      ctx.close();
   }

   /** Evaluate a JS literal and convert it the way TableDataVSAScriptable.setHyperlink does. */
   private Hyperlink.Ref convertJs(String js) {
      Object val = JavaScriptEngine.unwrap(ctx.eval("js", "(" + js + ")"));
      return (Hyperlink.Ref) PropertyDescriptor.convert(val, Hyperlink.Ref.class);
   }

   @Test
   void booleanFlagsFromScriptAreApplied() {
      Hyperlink.Ref ref = convertJs(
         "{link:'Report1', sendSelectionParameters:true, disableParameterSheet:true}");
      assertEquals("Report1", ref.getLink());
      assertTrue(ref.isSendSelectionParameters());
      assertTrue(ref.isDisablePrompting());
   }

   @Test
   void booleanFalseFlagsFromScriptAreApplied() {
      Hyperlink.Ref ref = convertJs(
         "{link:'Report1', sendReportParameters:false, sendSelectionParameters:false}");
      assertFalse(ref.isSendReportParameters());
      assertFalse(ref.isSendSelectionParameters());
   }

   @Test
   void sendReportParametersBooleanTrue() {
      assertTrue(convertJs("{link:'R', sendReportParameters:true}").isSendReportParameters());
   }

   @Test
   void stringTrueFlagDoesNotThrow() {
      Hyperlink.Ref ref = convertJs(
         "{link:'Report1', sendReportParameters:'true', sendSelectionParameters:'true'}");
      assertTrue(ref.isSendReportParameters());
      assertTrue(ref.isSendSelectionParameters());
   }

   @Test
   void stringFalseFlagDoesNotThrow() {
      assertFalse(convertJs("{link:'R', sendReportParameters:'false'}").isSendReportParameters());
   }

   @Test
   void numberTooltipIsConvertedToText() {
      Hyperlink.Ref ref = convertJs("{link:'Report1', tooltip:5, target:7}");
      assertNotNull(ref.getToolTip());
      assertTrue(ref.getToolTip().startsWith("5"));
      assertNotNull(ref.getTargetFrame());
      assertTrue(ref.getTargetFrame().startsWith("7"));
   }

   @Test
   void stringTextKeysStillWork() {
      Hyperlink.Ref ref = convertJs("{link:'Report1', tooltip:'tip', target:'_blank', type:'web'}");
      assertEquals("Report1", ref.getLink());
      assertEquals("tip", ref.getToolTip());
      assertEquals("_blank", ref.getTargetFrame());
      assertEquals(Hyperlink.WEB_LINK, ref.getLinkType());
   }

   @Test
   void nullLinkDoesNotThrow() {
      Hyperlink.Ref ref = convertJs("{link:null, tooltip:'tip'}");
      assertNotNull(ref);
      assertEquals("tip", ref.getToolTip());
   }

   @Test
   void plainParametersStillPassThrough() {
      Hyperlink.Ref ref = convertJs("{link:'R', p1:3, p2:'x', sendSelectionParameters:true}");
      assertEquals("x", ref.getParameter("p2"));
      assertNotNull(ref.getParameter("p1"));
      assertTrue(ref.isSendSelectionParameters());
   }

   @Test
   void arrayFormBooleanFlag() {
      Hyperlink.Ref ref = convertJs("['Report1', ['sendSelectionParameters', true]]");
      assertEquals("Report1", ref.getLink());
      assertTrue(ref.isSendSelectionParameters());
   }

   @Test
   void arrayFormPairsBooleanFlag() {
      Hyperlink.Ref ref = convertJs(
         "['Report1', [['disableParameterSheet', true], ['p', 'v']]]");
      assertTrue(ref.isDisablePrompting());
      assertEquals("v", ref.getParameter("p"));
   }

   @Test
   void mapInputBooleanFlags() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("link", "Report1");
      map.put("sendSelectionParameters", Boolean.TRUE);
      map.put("disableParameterSheet", Boolean.TRUE);
      Hyperlink.Ref ref = (Hyperlink.Ref) PropertyDescriptor.convert(map, Hyperlink.Ref.class);
      assertTrue(ref.isSendSelectionParameters());
      assertTrue(ref.isDisablePrompting());
   }

   @Test
   void isTrueAcceptsBooleanAndStringOnly() {
      assertTrue(Hyperlink.Ref.isTrue(Boolean.TRUE));
      assertTrue(Hyperlink.Ref.isTrue("true"));
      assertFalse(Hyperlink.Ref.isTrue(Boolean.FALSE));
      assertFalse(Hyperlink.Ref.isTrue("false"));
      assertFalse(Hyperlink.Ref.isTrue(null));
      assertFalse(Hyperlink.Ref.isTrue(1));
   }
}
