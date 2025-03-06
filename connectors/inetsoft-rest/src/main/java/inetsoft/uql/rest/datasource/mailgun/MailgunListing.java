package inetsoft.uql.rest.datasource.mailgun;

import inetsoft.uql.rest.json.EndpointJsonListing;

public class MailgunListing extends EndpointJsonListing<MailgunDataSource> {

   public MailgunListing() {
      super(MailgunDataSource.class, "Marketing");
   }
}
