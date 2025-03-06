package inetsoft.uql.rest.datasource.mailgun;


import inetsoft.sree.SreeEnv;
import inetsoft.uql.rest.json.EndpointJsonQuery;

import java.util.Map;

public class MailgunQuery extends EndpointJsonQuery<MailgunEndpoint> {
   public MailgunQuery() {
      super(MailgunDataSource.TYPE);
      setJsonPath("$");
   }

   @Override
   public Map<String, MailgunEndpoint> getEndpointMap() {
      if("true".equals(SreeEnv.getProperty("debug.endpoints"))) {
         return Endpoints.load(MailgunEndpoints.class);
      }

      return MailgunQuery.Singleton.INSTANCE.endpoints;
   }

   enum Singleton {
      INSTANCE;
      private final Map<String, MailgunEndpoint> endpoints =
         Endpoints.load(MailgunEndpoints.class);
   }
}
