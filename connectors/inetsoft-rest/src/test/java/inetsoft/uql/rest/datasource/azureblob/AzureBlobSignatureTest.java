/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.rest.datasource.azureblob;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.tabular.HttpParameter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Account SAS signing. The expected signature is computed here from the string-to-sign layout in
 * the Microsoft "Create an account SAS" document, not from the production code.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class AzureBlobSignatureTest {
   private static final String KEY =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(
         StandardCharsets.UTF_8));

   private static AzureBlobDataSource source(String account, String key) {
      AzureBlobDataSource ds = new AzureBlobDataSource();
      ds.setName("azsig-" + System.nanoTime());
      ds.setAccountName(account);
      ds.setStorageAccountKey(key);
      return ds;
   }

   private static String hmac(String key, String stringToSign) throws Exception {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(Base64.getDecoder().decode(key), "HmacSHA256"));
      return Base64.getEncoder().encodeToString(
         mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
   }

   /** Account SAS string-to-sign for service version 2018-11-09 up to 2020-10-02. */
   private static String stringToSign(AzureBlobDataSource ds) {
      return String.join("\n", ds.getAccountName(), ds.getSignaturePermissions(),
                         ds.getSignatureServices(), ds.getSignatureResourceTypes(),
                         ds.getSignatureStartTime(), ds.getSignatureExpiryTime(),
                         ds.getSignatureIpRange(), ds.getSignatureProtocol(),
                         ds.getSignatureServicesVersion()) + "\n";
   }

   private static Map<String, String> queryParams(AzureBlobDataSource ds) {
      Map<String, String> map = new LinkedHashMap<>();

      for(HttpParameter p : ds.getQueryHttpParameters()) {
         if(p.getType() == HttpParameter.ParameterType.QUERY) {
            map.put(p.getName(), p.getValue());
         }
      }

      return map;
   }

   @Test
   void signatureMatchesIndependentHmacOfDocumentedStringToSign() throws Exception {
      AzureBlobDataSource ds = source("myacct", KEY);
      ds.generateSignature("s");
      assertEquals(hmac(KEY, stringToSign(ds)), ds.getSignature());
   }

   @Test
   void emptyFieldsGetDefaults() throws Exception {
      AzureBlobDataSource ds = source("myacct", KEY);
      ds.generateSignature("s");
      assertEquals("2019-07-07", ds.getSignatureServicesVersion());
      assertEquals("b", ds.getSignatureServices());
      assertEquals("sco", ds.getSignatureResourceTypes());
      assertEquals("lr", ds.getSignaturePermissions());
      assertEquals("https", ds.getSignatureProtocol());
      assertEquals("", ds.getSignatureIpRange());
   }

   @Test
   void explicitFieldsAreKeptAndSigned() throws Exception {
      AzureBlobDataSource ds = source("myacct", KEY);
      ds.setSignatureServicesVersion("2020-02-10");
      ds.setSignatureServices("bf");
      ds.setSignatureResourceTypes("o");
      ds.setSignaturePermissions("rwdl");
      ds.setSignatureIpRange("168.1.5.60-168.1.5.70");
      ds.setSignatureProtocol("https,http");
      ds.generateSignature("s");
      assertEquals("bf", ds.getSignatureServices());
      assertEquals("rwdl", ds.getSignaturePermissions());
      assertEquals(hmac(KEY, stringToSign(ds)), ds.getSignature());
   }

   @Test
   void changingAnySignedFieldChangesTheSignature() throws Exception {
      AzureBlobDataSource base = source("myacct", KEY);
      base.generateSignature("s");
      String baseStart = base.getSignatureStartTime();
      String baseExpiry = base.getSignatureExpiryTime();

      Map<String, java.util.function.Consumer<AzureBlobDataSource>> changes = new LinkedHashMap<>();
      changes.put("permissions", d -> d.setSignaturePermissions("rwl"));
      changes.put("services", d -> d.setSignatureServices("q"));
      changes.put("resourceTypes", d -> d.setSignatureResourceTypes("o"));
      changes.put("ip", d -> d.setSignatureIpRange("10.0.0.1"));
      changes.put("protocol", d -> d.setSignatureProtocol("https,http"));
      changes.put("version", d -> d.setSignatureServicesVersion("2020-02-10"));
      changes.put("account", d -> d.setAccountName("otheracct"));
      changes.put("key", d -> d.setStorageAccountKey(Base64.getEncoder().encodeToString(
         "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8))));

      for(Map.Entry<String, java.util.function.Consumer<AzureBlobDataSource>> e :
         changes.entrySet())
      {
         AzureBlobDataSource ds = source("myacct", KEY);
         e.getValue().accept(ds);
         ds.generateSignature("s");
         // start and expiry are derived from the clock; compare only if they did not move
         if(baseStart.equals(ds.getSignatureStartTime()) &&
            baseExpiry.equals(ds.getSignatureExpiryTime()))
         {
            assertNotEquals(base.getSignature(), ds.getSignature(), e.getKey());
         }
         else {
            assertEquals(hmac(ds.getStorageAccountKey(), stringToSign(ds)), ds.getSignature(),
                         e.getKey());
         }
      }
   }

   @Test
   void queryParametersCarryTheDocumentedNames() throws Exception {
      AzureBlobDataSource ds = source("myacct", KEY);
      ds.generateSignature("s");
      Map<String, String> q = queryParams(ds);
      assertEquals(ds.getSignatureServicesVersion(), q.get("sv"));
      assertEquals(ds.getSignatureServices(), q.get("ss"));
      assertEquals(ds.getSignatureResourceTypes(), q.get("srt"));
      assertEquals(ds.getSignaturePermissions(), q.get("sp"));
      assertEquals(ds.getSignatureStartTime(), q.get("st"));
      assertEquals(ds.getSignatureExpiryTime(), q.get("se"));
      assertEquals(ds.getSignatureProtocol(), q.get("spr"));
      assertEquals(ds.getSignature(), q.get("sig"));
      assertFalse(q.containsKey("sip"), "sip is omitted when no IP range is set");
   }

   @Test
   void ipRangeIsSentAsSip() throws Exception {
      AzureBlobDataSource ds = source("myacct", KEY);
      ds.setSignatureIpRange("10.0.0.1-10.0.0.9");
      ds.generateSignature("s");
      assertEquals("10.0.0.1-10.0.0.9", queryParams(ds).get("sip"));
   }

   @Test
   void signatureWithBase64SpecialCharactersIsEncodedInTheUrl() throws Exception {
      // sig is base64 and may contain + / =; st and se contain ':'
      AzureBlobDataSource ds = source("myacct", KEY);
      ds.generateSignature("s");
      ds.setSignature("a+b/c=");
      org.apache.http.client.utils.URIBuilder b =
         new org.apache.http.client.utils.URIBuilder("https://x.blob.core.windows.net");

      for(HttpParameter p : ds.getQueryHttpParameters()) {
         if(p.getType() == HttpParameter.ParameterType.QUERY) {
            b.addParameter(p.getName(), p.getValue());
         }
      }

      String url = b.build().toASCIIString();
      assertTrue(url.contains("sig=a%2Bb%2Fc%3D"), url);
   }

   @Test
   void noSignatureIsGeneratedWithoutAKey() throws Exception {
      AzureBlobDataSource ds = source("myacct", null);
      ds.generateSignature("s");
      assertNull(ds.getSignature());
      assertNull(ds.getSignatureStartTime());
   }

   /**
    * Microsoft "Create an account SAS": for version 2020-12-06 and later the string-to-sign has a
    * signedEncryptionScope line after the version.
    */
   @Test
   @Disabled("Bug #80273: the string-to-sign has no signedEncryptionScope line for sv 2020-12-06 and later; enable when it is fixed")
   void version20201206AddsEncryptionScopeLine() throws Exception {
      AzureBlobDataSource ds = source("myacct", KEY);
      ds.setSignatureServicesVersion("2020-12-06");
      ds.generateSignature("s");
      assertEquals(hmac(KEY, stringToSign(ds) + "\n"), ds.getSignature());
   }
}
