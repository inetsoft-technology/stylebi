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
package com.restfully.services;

import com.restfully.domain.*;

import java.util.*;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.json.*;
import java.io.IOException;

import sun.misc.BASE64Decoder;

@Path("/")
public class RestService {
   public RestService() {
      setupData();
   }

   @GET
   @Path("customersAuth")
   @Produces("application/json")
   public JsonArray getCustomers(@HeaderParam("authorization") String authString){
      if(!isUserAuthenticated(authString, "admin", "admin")){
            return null;
      }
      else{
         Customers customers = new Customers();
         customers.setCustomers(this.customers);
         return customers.toJson();
      }
   }

   @GET
   @Path("customersAuth/{username}")
   @Produces("application/json")
   public JsonObject getCustomers(@PathParam("username") String username, @HeaderParam("authorization") String authString){
      if(!isUserAuthenticated(authString, username)){
            return null;
      }
      else{
         for(Customer customer : customers) {
            if(customer.getFirstName().equals(username)) {
               return customer.toJson();
            }
         }
         return null;
      }
   }

   private boolean isUserAuthenticated(String authString, String username){
      return isUserAuthenticated(authString, username, "success123");
   }

   private boolean isUserAuthenticated(String authString, String username,
                     String password) {
      String dcodeAuth = "";
      //Header is in the format "Basic", we need to extract data before decoding
      //it back to original string;
      String[] authParts = authString.split("\\s+");
      String authInfo = authParts[1];
      //Decode the data back to original string
      byte[] bytes = null;

      try{
         bytes = new BASE64Decoder().decodeBuffer(authInfo);
      }
      catch (IOException e){
         e.printStackTrace();
      }

      dcodeAuth = new String(bytes);
      //return decodeAuth;
      String userinfo = username + ":" + password;
      if(dcodeAuth.equals(userinfo)){
         return true;
      }
      else{
         return false;
      }
   }

   @GET
   @Path("customers")
   @Produces("application/json")
   public JsonArray getCustomers(@QueryParam("start") int start,
                                 @QueryParam("size") @DefaultValue("2") int size,
                                 @QueryParam("firstName") String firstName,
                                 @QueryParam("lastName") String lastName)
   {
      Customers customers = new Customers();
      customers.setCustomers(this.customers);
      return customers.toJson();
   }

   @GET
   @Path("customers/{id}")
   @Produces("application/json")
   public JsonObject getCustomer(@PathParam("id") int id) {
      for(Customer customer : customers) {
         if(customer.getId() == id) {
            return customer.toJson();
         }
      }

      return null;
   }

   @GET
   @Produces("application/xml")
   @Path("orders")
   public Response getOrders(@QueryParam("start") int start,
                             @QueryParam("size") @DefaultValue("2") int size)
   {
      return null;
   }

   @GET
   @Path("orders/{id}")
   @Produces("application/xml")
   public Response getOrder(@PathParam("id") int id, @Context UriInfo uriInfo) {
      return null;
   }

   @GET
   @Path("products")
   @Produces("application/xml")
   public Products getProducts(@QueryParam("start") int start,
                               @QueryParam("size") @DefaultValue("2") int size,
                               @QueryParam("name") String name,
                               @Context UriInfo uriInfo)
   {
      return null;
   }

   @GET
   @Path("products/{id}")
   @Produces("application/xml")
   public Product getProduct(@PathParam("id") int id) {
      return null;
   }

   @GET
   @Path("pathexample")
   @Produces("application/json")
   public String getPathExample() {
      String json =
         "{ \"store\": {\n" +
         "    \"book\": [ \n" +
         "      { \"category\": \"reference\",\n" +
         "        \"author\": \"Nigel Rees\",\n" +
         "        \"title\": \"Sayings of the Century\",\n" +
         "        \"price\": 8.95\n" +
         "      },\n" +
         "      { \"category\": \"fiction\",\n" +
         "        \"author\": \"Evelyn Waugh\",\n" +
         "        \"title\": \"Sword of Honour\",\n" +
         "        \"price\": 12.99,\n" +
         "        \"isbn\": \"0-553-21311-3\"\n" +
         "      }\n" +
         "    ],\n" +
         "    \"bicycle\": {\n" +
         "      \"color\": \"red\",\n" +
         "      \"price\": 19.95\n" +
         "    }\n" +
         "  }}";

      return json;
   }

   @GET
   @Path("datasift")
   @Produces("application/json")
   public String getDataSift() {
      String json =
         "{\n" +
         "    \"id\": \"93186020677f1881aab7cddb28fa805c\",\n" +
         "    \"hash\": \"c426dd575d435e5bc68a6edf125026c4\",\n" +
         "    \"hash_type\": \"stream\",\n" +
         "    \"count\": 2,\n" +
         "    \"delivered_at\": \"Fri, 17 Aug 2012 14:23:00 +0000\",\n" +
         "    \"interactions\": [\n" +
         "        {\n" +
         "            \"interaction\": {\n" +
         "                \"source\": \"foursquare\",\n" +
         "                \"author\": {\n" +
         "                    \"username\": \"johndoe\",\n" +
         "                    \"name\": \"John Doe\",\n" +
         "                    \"id\": 10750902,\n" +
         "                    \"avatar\": \"http://a0.twimg.com/profile_images/1111111111/example.jpeg\",\n" +
         "                    \"link\": \"http://twitter.com/johndoe\"\n" +
         "                },\n" +
         "                \"type\": \"twitter\",\n" +
         "                \"created_at\": \"Fri, 17 Aug 2012 14:13:08 +0000\",\n" +
         "                \"content\": \"I like ice cream!\",\n" +
         "                \"id\": \"1e1e875ab43fa233e074337458bc1dca\",\n" +
         "                \"link\": \"http://twitter.com/johndoe/statuses/111111111111111111\",\n" +
         "                \"geo\": {\n" +
         "                    \"latitude\": 42.376104,\n" +
         "                    \"longitude\": -71.237189\n" +
         "                }\n" +
         "            },\n" +
         "            \"twitter\": {\n" +
         "                \"created_at\": \"Fri, 17 Aug 2012 14:13:08 +0000\",\n" +
         "                \"domains\": [\n" +
         "                    \"4sq.com\"\n" +
         "                ],\n" +
         "                \"geo\": {\n" +
         "                    \"latitude\": 42.376104,\n" +
         "                    \"longitude\": -71.237189\n" +
         "                },\n" +
         "                \"id\": \"111111111111111111\",\n" +
         "                \"links\": [\n" +
         "                    \"http://4sq.com/NLM3gD\"\n" +
         "                ],\n" +
         "                \"mentions\": [\n" +
         "                    \"beyonce\",\n" +
         "                    \"ladygaga\"\n" +
         "                ],\n" +
         "                \"place\": {\n" +
         "                    \"id\": \"90ad0a08b3333d6d\",\n" +
         "                    \"url\": \"http://api.twitter.com/1/geo/id/example.json\",\n" +
         "                    \"place_type\": \"poi\",\n" +
         "                    \"country\": \"United States\",\n" +
         "                    \"country_code\": \"US\",\n" +
         "                    \"full_name\": \"Cafe On the Common, Waltham\",\n" +
         "                    \"name\": \"Cafe On the Common\"\n" +
         "                },\n" +
         "                \"source\": \"http://foursquare.com\",\n" +
         "                \"text\": \"I like ice cream\",\n" +
         "                \"user\": {\n" +
         "                    \"name\": \"John Doe\",\n" +
         "                    \"url\": \"http://about.me/John Doe\",\n" +
         "                    \"description\": \"all my tweets...\",\n" +
         "                    \"location\": \"London\",\n" +
         "                    \"statuses_count\": 9689,\n" +
         "                    \"followers_count\": 2054,\n" +
         "                    \"friends_count\": 2016,\n" +
         "                    \"screen_name\": \"johndoe\",\n" +
         "                    \"lang\": \"en\",\n" +
         "                    \"time_zone\": \"Eastern Time (US & Canada)\",\n" +
         "                    \"utc_offset\": -18000,\n" +
         "                    \"listed_count\": 118,\n" +
         "                    \"id\": 11111111,\n" +
         "                    \"id_str\": \"11111111\",\n" +
         "                    \"geo_enabled\": true,\n" +
         "                    \"created_at\": \"Fri, 30 Nov 2007 21:26:38 +0000\"\n" +
         "                }\n" +
         "            }\n" +
         "        },\n" +
         "        {\n" +
         "            \"demographic\": {\n" +
         "                \"gender\": \"mostly_male\"\n" +
         "            },\n" +
         "            \"interaction\": {\n" +
         "                \"source\": \"foursquare\",\n" +
         "                \"author\": {\n" +
         "                    \"username\": \"JohnDoe\",\n" +
         "                    \"name\": \"John Doe\",\n" +
         "                    \"id\": 11111111,\n" +
         "                    \"avatar\": \"http://a0.twimg.com/profile_images/1111111111/example.jpg\",\n" +
         "                    \"link\": \"http://twitter.com/JohnDoe\"\n" +
         "                },\n" +
         "                \"type\": \"twitter\",\n" +
         "                \"created_at\": \"Fri, 17 Aug 2012 14:13:09 +0000\",\n" +
         "                \"content\": \"I love ice cream!\",\n" +
         "                \"id\": \"1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a\",\n" +
         "                \"link\": \"http://twitter.com/JohnDoe/statuses/111111111111111111\",\n" +
         "                \"geo\": {\n" +
         "                    \"latitude\": 41.48454863,\n" +
         "                    \"longitude\": -72.79693173\n" +
         "                }\n" +
         "            },\n" +
         "            \"twitter\": {\n" +
         "                \"created_at\": \"Fri, 17 Aug 2012 14:13:09 +0000\",\n" +
         "                \"domains\": [\n" +
         "                    \"4sq.com\"\n" +
         "                ],\n" +
         "                \"geo\": {\n" +
         "                    \"latitude\": 41.48454863,\n" +
         "                    \"longitude\": -72.79693173\n" +
         "                },\n" +
         "                \"id\": \"111111111111111111\",\n" +
         "                \"links\": [\n" +
         "                    \"http://4sq.com/NMH47L\"\n" +
         "                ],\n" +
         "                \"place\": {\n" +
         "                    \"id\": \"e5ac52573b5f3333\",\n" +
         "                    \"url\": \"http://api.twitter.com/1/geo/id/1a1a1a1a1a1a1a1a.json\",\n" +
         "                    \"place_type\": \"poi\",\n" +
         "                    \"country\": \"United States\",\n" +
         "                    \"country_code\": \"US\",\n" +
         "                    \"full_name\": \"Willow Farm\",\n" +
         "                    \"name\": \"Willow Farm\"\n" +
         "                },\n" +
         "                \"source\": \"http://foursquare.com\",\n" +
         "                \"text\": \"I love ice cream\",\n" +
         "                \"user\": {\n" +
         "                    \"name\": \"John Doe\",\n" +
         "                    \"description\": \"Man of mystery\n\",\n" +
         "                    \"location\": \"Main street\",\n" +
         "                    \"statuses_count\": 10073,\n" +
         "                    \"followers_count\": 444,\n" +
         "                    \"friends_count\": 533,\n" +
         "                    \"screen_name\": \"John Doe\",\n" +
         "                    \"lang\": \"en\",\n" +
         "                    \"time_zone\": \"Quito\",\n" +
         "                    \"utc_offset\": -18000,\n" +
         "                    \"listed_count\": 9,\n" +
         "                    \"id\": 11111111,\n" +
         "                    \"id_str\": \"11111111\",\n" +
         "                    \"geo_enabled\": true,\n" +
         "                    \"created_at\": \"Sun, 26 Apr 2009 23:38:45 +0000\"\n" +
         "                }\n" +
         "            }\n" +
         "        }\n" +
         "    ]\n" +
         "}";

      return json;
   }

   private void setupData() {
      customers.add(new Customer(1, "Larry", "Liang", "40 Oakcrest Dr",
                                 "East Brunswick", "NJ", "08816", "USA"));
      customers.add(new Customer(2, "Tom", "Lee", "12 Broad St",
                                 "New York", "NY", "11223", "USA"));
   }

   private List<Customer> customers = new ArrayList<Customer>();
   private List<Product> products = new ArrayList<Product>();
   private List<Order> orders = new ArrayList<Order>();
}
