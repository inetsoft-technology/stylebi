/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.datasource.twitter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.stream.Collectors;

public class TwitterAuthorizationString {
   public TwitterAuthorizationString(String url, String consumerKey, String consumerSecret) {
      this.url = url;
      this.consumerKey = consumerKey;
      this.consumerSecret = consumerSecret;
   }

   public TwitterAuthorizationString setParameters(Map<String, String> parameters) {
      this.parameters = parameters;
      return this;
   }

   public TwitterAuthorizationString setPost(boolean post) {
      this.post = post;
      return this;
   }

   public TwitterAuthorizationString setOauthTokenSecret(String oauthTokenSecret) {
      this.oauthTokenSecret = oauthTokenSecret;
      return this;
   }

   public TwitterAuthorizationString setOauthToken(String oauthToken) {
      this.oauthToken = oauthToken;
      return this;
   }

   public String build() {
      final Map<String, String> parametersToSign = createOAuthParameters();

      if(parameters != null) {
         parametersToSign.putAll(parameters);
      }

      final String signingKey = encode(consumerSecret, "Consumer Secret") + "&" + encode(oauthTokenSecret, "OAuth Token Secret");
      final String signature = generateSignature(url, parametersToSign, signingKey, post);
      parametersToSign.put("oauth_signature", signature);
      final String parameterString = parametersToSign.entrySet().stream()
         .sorted(Map.Entry.comparingByKey())
         .map(entry -> encodeForAuthorization(entry.getKey(), entry.getValue()))
         .collect(Collectors.joining(", "));
      return "OAuth" + " " + parameterString;
   }

   private Map<String, String> createOAuthParameters() {
      final HashMap<String, String> defaultParameters = new HashMap<>();
      defaultParameters.put("oauth_consumer_key", consumerKey);
      defaultParameters.put("oauth_nonce", generateNonce());
      defaultParameters.put("oauth_signature_method", "HMAC-SHA1");
      defaultParameters.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));

      if(oauthToken != null) {
         defaultParameters.put("oauth_token", oauthToken);
      }

      defaultParameters.put("oauth_version", "1.0");
      return defaultParameters;
   }

   private String generateNonce() {
      final SecureRandom sr = new SecureRandom();
      final byte[] randomBytes = new byte[32];
      sr.nextBytes(randomBytes);
      return Base64.getEncoder().encodeToString(randomBytes);
   }

   private String encodeForSignature(String key, String value) {
      return String.format("%s=%s", encode(key), encode(value, key));
   }

   private String encodeForAuthorization(String key, String value) {
      return String.format("%s=\"%s\"", encode(key), encode(value, key));
   }

   /**
    * URL encode a string
    *
    * @param value the string to encode
    *
    * @return the URL encoded string
    */
   private String encode(String value) {
      return encode(value, value);
   }

   /**
    * URL encode a string
    *
    * @param value the string to encode
    * @param alias an alias to print to the log if the value may contain sensitive information
    *
    * @return the URL encoded string
    */
   private String encode(String value, String alias) {
      try {
         return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~");
      }
      catch(UnsupportedEncodingException e) {
         throw new RuntimeException("Failed to encode value for " + alias, e.getCause());
      }
   }

   public String generateSignature(String url, Map<String, String> oauthParameters, String signingKey, boolean post) {
      final String parameterString = oauthParameters.entrySet().stream()
         .sorted(Map.Entry.comparingByKey())
         .map(entry -> encodeForSignature(entry.getKey(), entry.getValue()))
         .collect(Collectors.joining("&"));
      final String method = post ? "POST" : "GET";
      final String baseString = method + "&" + encode(url) + "&" + encode(parameterString);

      try {
         final Mac mac = Mac.getInstance("HmacSHA1");
         final SecretKeySpec key = new SecretKeySpec(signingKey.getBytes(), "HmacSHA1");
         mac.init(key);
         return Base64.getEncoder().encodeToString(mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8)));
      }
      catch(NoSuchAlgorithmException | InvalidKeyException e) {
         throw new RuntimeException("Unable to generate signature for base string", e);
      }
   }


   private String url;
   private String consumerKey;
   private String consumerSecret;
   private Map<String, String> parameters;
   private boolean post;
   private String oauthToken;
   private String oauthTokenSecret = "";
}

