package inetsoft.uql.rest.datasource.mailgun;

import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.rest.json.EndpointJsonDataSource;

public class MailgunDataSource extends EndpointJsonDataSource<MailgunDataSource> {
   public static final String TYPE = "Rest.Mailgun";

   public MailgunDataSource() {
      super(TYPE, MailgunDataSource.class);
      setAuthType(AuthType.BASIC);
   }

   @Override
   protected String getTestSuffix() {
      return null;
   }
}
