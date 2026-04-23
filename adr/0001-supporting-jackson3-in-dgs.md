---
title: Supporting Jackson 3 in DGS
status: ACCEPTED
date: 2026-04-23
---

# Decision

To add Jackson 3 support to the OSS DGS framework without breaking existing
users, we are introducing new client classes whose primary constructors take
a Jackson-agnostic abstraction instead of an `ObjectMapper`. The existing
Jackson 2-bound client classes (`GraphQLClient`, `GraphQLResponse`,
`WebClientGraphQLClient`, `CustomGraphQLClient`, `RestClientGraphQLClient`,
`CustomMonoGraphQLClient`, etc.) are deprecated in favor of new `Dgs`-prefixed
counterparts (`DgsGraphQLClient`, `DgsGraphQLResponse`, `DgsWebClientGraphQLClient`,
`DgsCustomGraphQLClient`, `DgsRestClientGraphQLClient`, `DgsCustomMonoGraphQLClient`, etc.)
that do not expose Jackson types on their public API.

The new interfaces and abstraction will be backported to DGS 10.x and 5.x so that
libraries still compiling against older DGS versions have a migration path
that does not require a major-version/Spring Boot upgrade. DGS 12.x ships
Jackson 3 as the autoconfigured default (following the OSS Spring Boot 4
precedent), with Jackson 2 available as opt-in via `graphql-dgs-jackson2`.

Following this approach, an application can drop Jackson 2 from its classpath
once it — and every library it consumes — have migrated to the new `Dgs*`
client classes. The deprecation warnings and kdoc are the primary migration
signal for libraries.

Naming in the interim period is admittedly confusing: two parallel client
hierarchies coexist, one tied to Jackson 2 and one Jackson-agnostic. We
judged this tradeoff worth it because it avoids a breaking change for
anyone currently constructing clients with `ObjectMapper` and lets libraries
migrate on their own schedule.

# Alternatives

## Approach 1: Constructor overloads

Add `JsonMapper` (Jackson 3) overloads alongside the existing `ObjectMapper`
constructors on the same client classes.

This is cleanly backwards compatible and gives callers a single class to
target, but it requires both Jackson 2 and Jackson 3 to be on the consumer's
compile classpath. The whole point of this effort is to unblock consumers
from needing Jackson 2 anywhere on their classpath if they want to embrace
Jackson 3, so this approach was rejected.

## Approach 2: Change the primary constructor to an abstraction

Change the existing client classes' primary constructor from `ObjectMapper`
to a version-agnostic `DgsJsonMapper` sealed interface.

Long term this is the cleanest API: Jackson is no longer exposed directly
on the public API. However, it is a breaking change for everyone
constructing clients today with `ObjectMapper`, which would force a
coordinated library and app migration for adopting DGS 12.x.
We don't want to couple this change to a required library migration
or risk runtime incompatibilities so this was rejected.

## Approach 3 (chosen): new classes with the abstraction in their primary constructors

Same API shape as Approach 2, but in new `Dgs*`-prefixed classes so the
existing classes remain source- and binary-compatible. The abstraction is
backported so libraries on older DGS versions can migrate before the old
classes are removed.

The cost is the confusing interim naming and a potential long tail for
apps to fully drop Jackson 2, since libraries face only a deprecation
warning (not a compile break) pressuring them to switch. We accept this
in exchange for maximum backward compatibility.

# Links

* PR: https://github.com/Netflix/dgs-framework/pull/2299
* OSS Spring Boot 4 Jackson 3 default precedent: https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring
