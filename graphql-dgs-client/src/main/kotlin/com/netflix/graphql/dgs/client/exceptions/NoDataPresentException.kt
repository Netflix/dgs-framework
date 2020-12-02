package com.netflix.graphql.dgs.client.exceptions

import java.lang.RuntimeException

class NoDataPresentException: RuntimeException("No data available in response")