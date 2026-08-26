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

package inetsoft.web.portal.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.SreeEnv;
import inetsoft.util.Tool;
import jakarta.servlet.http.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PostSignUpUserData {

   public PostSignUpUserData(String email, String firstName, String lastName, String cookies) {
      this.email = email;
      this.firstName = firstName;
      this.lastName = lastName;
      this.cookies = cookies;
   }

   public void sendUserData() {
      String postURL = SreeEnv.getProperty("selfSignUpPost.url");
      String postUsername = SreeEnv.getProperty("selfSignUpPost.username");
      String postHeader = SreeEnv.getProperty("selfSignUpPost.header");

      // Nothing in the product writes these two, so they are set by hand in the property store or
      // through INETSOFTENV_*. Decrypt on read so an operator who does not want a credential
      // sitting in the clear can store Tool.encryptPassword output instead: decryptPassword
      // returns an unrecognised value unchanged (LocalPasswordEncryption's clear-text branch), so
      // a plaintext value keeps working exactly as before.
      //
      // Deliberately not SreeEnv.getPassword. Under cloud secrets that treats the stored string as
      // a secret-manager reference and would resolve a literal to null, and its hardcoded name
      // switch reads a "password" field out of the JSON for anything outside the OpenID names -
      // the wrong field for selfSignUpPost.secretKey.
      String postPassword = Tool.decryptPassword(SreeEnv.getProperty("selfSignUpPost.password"));
      String postSecret = Tool.decryptPassword(SreeEnv.getProperty("selfSignUpPost.secretKey"));

      HttpClient client = HttpClient.newHttpClient();

      if(postURL != null) {
         try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
               .uri(URI.create(postURL))
               .method("POST", HttpRequest.BodyPublishers.ofString(parseUserData(), StandardCharsets.UTF_8))
               .header("Content-Type", "application/json; charset=UTF-8");

            if(postUsername != null && postPassword != null) {
               String credential = Base64.getEncoder().encodeToString(
                  (postUsername + ":" + postPassword).getBytes(StandardCharsets.UTF_8));
               requestBuilder.header("Authorization", "Basic " + credential);
            }

            if(postHeader != null && postSecret != null) {
               requestBuilder.header(postHeader, postSecret);
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            LOG.debug("Writing PostSignUp User: (" + parseUserData() + ") to <" + postURL + "> returned code: " + statusCode);

            if(statusCode < 200 || statusCode >= 300) {
               if(statusCode >= 500) {
                  LOG.error("Server error occurred while sending SignUpUser data. ResponseCode: " + response.statusCode());
               }
               else if(statusCode >= 400) {
                  LOG.error("Client issue occurred when sending SignUpUser data. ResponseCode: " + statusCode);
               }
               else {
                  LOG.error("Sending SignUpUser Data returned unexpected response code(" + statusCode + "). This may indicate a problem.");
               }
            }

         }
         catch (Exception e) {
            LOG.error("Error sending SignUpUser Data: "+ e);
         }
      }
      else {
         LOG.debug("selfSignUpPost.url is not set, SignUpUser Data not sent");
      }
   }

   private String parseUserData() {
      ObjectMapper objectMapper = new ObjectMapper();
      Map<String, Object> userData = new HashMap<>();

      userData.put("email", getEmail());
      userData.put("firstName", getFirstName());
      userData.put("lastName", getLastName());

      if(getCookies() != null) {
         try {
            userData.put("cookies", objectMapper.readTree(getCookies()));
         }
         catch (IOException e) {
            // Ignore cookies
         }
      }

      try {
         return objectMapper.writeValueAsString(userData);
      }
      catch (Exception e) {
         throw new RuntimeException("Error parsing user data to JSON", e);
      }
   }

   public String getEmail() {
      return email;
   }

   public void setEmail(String email) {
      this.email = email;
   }

   public String getFirstName() {
      return firstName;
   }

   public void setFirstName(String firstName) {
      this.firstName = firstName;
   }

   public String getLastName() {
      return lastName;
   }

   public void setLastName(String lastName) {
      this.lastName = lastName;
   }

   public String getCookies() {
      return cookies;
   }

   public void setCookies(String cookies) {
      this.cookies = cookies;
   }

   private String email = "";
   private String firstName = "";
   private String lastName = "";
   private String cookies = null;

   private static final Logger LOG =
      LoggerFactory.getLogger(PostSignUpUserData.class);
}
