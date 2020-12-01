package com.netflix.graphql.dgs.exceptions

import java.lang.RuntimeException

class NoDgsFederationResolverFoundException: RuntimeException("@key directive was used in schema, but could not find DgsComponent implementing DgsFederationResolver.")

