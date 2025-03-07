package inetsoft.uql.rest.datasource.mailgun;

import inetsoft.uql.rest.json.EndpointJsonService;

public class MailgunService  extends EndpointJsonService {
   public MailgunService() {
      super(MailgunDataSource.TYPE, MailgunDataSource.class, MailgunQuery.class);
   }
}
