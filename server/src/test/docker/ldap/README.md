# LDAP Security Docker

Use the generic LDAP security provider with the following settings:

| Property                  | Value                                                 |
|---------------------------|-------------------------------------------------------|
| Protocol                  | ldap                                                  |
| Host                      | localhost                                             |
| Port                      | 10389                                                 |
| Root DN                   | dc=example,dc=com                                     |
| LDAP Administrator        | uid=admin,ou=system                                   |
| Password                  | secret                                                |
| Search Subtree            | true                                                  |
| User Search               | (objectClass=inetOrgPerson)                           |
| User Base                 | ou=people                                             |
| User Attribute            | uid                                                   |
| Mail Attribute            | mail                                                  |
| Group Search              | (objectClass=organizationalUnit)                      |
| Group Base                | ou=people                                             |
| Group Attribute           | ou                                                    |
| Role Search               | (objectClass=groupOfUniqueNames)                      |
| Role Base                 | ou=groups                                             |
| Role Attribute            | cn                                                    |
| User Roles Search         | (&(objectClass=groupOfUniqueNames)(uniqueMember={1})) |
| Role Roles Search         | (&(objectClass=groupOfUniqueNames)(uniqueMember={1})) |
| Group Roles Search        | (&(objectClass=groupOfUniqueNames)(uniqueMember={1})) |

See security.ldif in this folder for the users, groups and roles.
