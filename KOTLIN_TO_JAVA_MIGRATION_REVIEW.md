# DGS Kotlin-to-Java migration review

Reviewed branch `migrate-kotlin-to-java`, HEAD `56db08d4`, against `83a6b483` (the parent of the migration commit). Reviewed the memo pasted in the conversation. The Google Drive plugin could not read the original memo because its connected account lacked permission.

**Initial review conclusion:** The migration changes the user-facing API for both Java and Kotlin consumers. The proposed major release is appropriate if these changes are intentional, but passing the rewritten tests does not establish source or binary compatibility. The initial review also reproduced a runtime regression in Kotlin Flow execution. The findings below describe the original migration; the implementation status is recorded separately.

## Implementation status

The ordinary Java fixes requested from the first compatibility category have been implemented in the working tree. The initial findings and internal downstream investigation below are retained as historical evidence.

| Category | Implemented fix |
|---|---|
| Flow scheduling | Query and mutation Flow collection uses `Dispatchers.Default`; the subscription publisher retains its previous context. |
| Collection inputs | Restored baseline wildcard input parameters for clients, headers, errors, scalar options, and collaborators. Kept invariant return types and the original Java executor interfaces. |
| Boolean getters | Restored the old `get…` methods alongside `is…` getters. |
| Exception cause | Restored `DgsException.getCause(): Exception`, including the compiler-generated covariant bridge. |
| Kotlin nullability | Restored baseline JetBrains nullability annotations on matching library declarations, including convenience constructors and explicitly declared forwarding methods. |
| JPMS | Made optional Jackson module requirements static; added static annotation-module requirements where needed and opened JSON model packages to Jackson 2/3 for deserialization. |
| Jackson 2 exceptions | Rethrow the original Jackson exceptions without wrapping them or adding checked `throws` clauses to the public API. |
| Constructors and defaults | Restored ordinary constructor overloads and their defaults; explicitly selected Spring configuration binding constructors. |
| Data-class value behavior | Restored Kotlin equality, hash recurrence, and string field order for 48 former data classes outside the private loader-holder implementation. |
| Data-class operations | Restored positional `copy` and public `componentN` methods, enabling positional Kotlin calls and destructuring. |
| Schema constructor | Restored the original `Function1` constructor and deprecated constructor descriptor. The `Predicate` implementation constructor is private to avoid ambiguous lambda calls; the builder still accepts `Predicate`. |
| Context-builder SPI | Restored the baseline `Map<String, ? extends Object>` parameters. Existing Kotlin overrides using `Map<String, Any>?` compile against these declarations. |
| Constants | Added Java `OperationMessageKt` and `ProtocolKt` facades for the original Java static imports. |

Six concrete Spring executor methods previously exposed wildcard maps while implementing invariant Java interface methods. Java cannot declare those overrides with the same wildcard signatures. Added delegating overloads taking `CharSequence` queries and wildcard maps: existing Java calls with a `String` query and typed maps compile and execute through these overloads. The original erased `String` method descriptors remain available to binaries. Exact reflective generic signatures on those six methods still differ.

Restoring nullable constructor values also required separating JSON creation from ordinary construction. Ordinary constructors preserve nullable values. JSON creation preserves omitted defaults; plain mappers retain explicit null for nullable properties and reject null for non-null properties. DGS-owned mappers that use `NullIsSameAsDefault` register mixins to retain that policy for migrated classes, including nullable properties with non-null defaults. Java declarations cannot follow that Kotlin-specific mapper feature dynamically: externally supplied mappers configured with it may need equivalent mixins for these classes. Required non-null properties still reject missing/null values.

### Verification after fixes

- Final full `./gradlew test` passed: **539 tests**, zero failures, errors, or skips. [Build scan](https://gradle.com/s/dskzpnfq4esac).
- Added Java consumer tests for typed client maps, concrete header lists, scalar options, and all six Spring executor paths; added Kotlin regression tests for data-class methods, configuration binding, Flow scheduling, and Jackson 2/3 JSON defaults/nulls.
- Compared baseline and current value behavior for all 49 former data classes: 48 matching constructor/copy/component/equality/hash/string cases pass, including each nullable property set to null. The private loader-holder record is excluded from the supported API check.
- Recompiled Java getter/cause consumers and ran their baseline-compiled binaries successfully against the fixes. Java and Kotlin typed client map calls compile against both versions. Positional Kotlin `copy` and destructuring compile/run against both versions, and the baseline-compiled consumer also runs against the fixes.
- The Flow runtime probe returns before its 300 ms collection finishes and collects on `DefaultDispatcher-worker-1`; the permanent regression test uses a latch to verify this without relying on timing alone.
- A Kotlin unsafe nullable dereference is rejected against both versions. The annotation inventory has no remaining gaps on matching reviewed library API members; nine remaining inventory entries are build metadata, examples, and a private record.
- Jackson 2 malformed-input probes throw `JsonEOFException` directly against both versions. Client Jackson 2/3 factories and the server Jackson 2 mapper match baseline null-to-default behavior for the migrated model fields. The server hooks resolve optional model classes by name and introduce no dependency edges.
- Module-path JSON deserialization succeeds for both client mapper factories and matches the baseline defaults. The Jackson 2 factory also works with Jackson 3 absent. Module-path resolution succeeds without the optional Kotlin Jackson module for core and without the optional Jackson 2 JDK8 module for client.
- `git diff --check` passes.

### Remaining migration requirements

This work does not make the migration fully source or binary compatible. The separately listed Kotlin binary bridges remain absent: companions (including `CustomMonoGraphQLClient.Companion.createWithWebClient`), singleton entry points, default-mask constructors/methods, `copy$default`, interface `$DefaultImpls`, and enum `getEntries`. Baseline-compiled consumer probes still reproduce the expected companion/default-mask/copy/enum linkage failures.

Kotlin named arguments, named/defaulted `copy`, property overrides, object-expression syntax, reified helpers, and top-level Kotlin imports still need source changes or a Kotlin facade. The reserved JVM method name `default()` still needs a Kotlin or bytecode bridge to retain that exact entry point. The internal impact findings below therefore remain relevant to rollout and downstream rebuild planning.

## Initial review verification

- Built the pre-migration sources in an isolated directory, without changing the working branch.
- Built the migrated sources and ran `./gradlew test`: successful, 526 test cases reported, zero failures/errors/skips. [Build scan](https://gradle.com/s/om5drre24unv6).
- Compared public/protected class-file members, JVM descriptors, generic signatures, and static/instance flags from both builds. Changes to compiler-generated implementation classes were not treated as user-facing findings merely because they appeared in the inventory.
- Compiled representative Java and Kotlin consumer sources against each version; ran consumers compiled against the old version with the migrated framework.
- Reproduced Flow scheduling, Jackson exception behavior, and module-path resolution differences with the same external dependencies.
- Source files were not edited during the initial review. Temporary fixtures and the baseline build are under `/tmp`.

## Initial migration findings

### 1. Query and mutation Flow collection now executes on the caller's thread

**Priority: P1.** [FlowDataFetcherResultProcessor.java](graphql-dgs/src/main/java/com/netflix/graphql/dgs/internal/FlowDataFetcherResultProcessor.java#L39).

The old implementation used `CoroutineScope(Dispatchers.Default).future { flow.toList() }` for non-subscription operations. The new implementation creates a publisher with `EmptyCoroutineContext`, then immediately subscribes with `Flux.from(publisher).collectList().toFuture()`.

A cold Flow containing `Thread.sleep(300); emit(1)` produced:

| Version | Time until `process` returned | Thread collecting the Flow |
|---|---:|---|
| Base | 14 ms | `DefaultDispatcher-worker-1` |
| Branch | 371 ms | `main` |

This can serialize resolver execution and block a request thread or WebFlux event loop. Computation before the first suspension is also affected, even without explicit blocking I/O. Subscription processing previously used the publisher path already; the regression concerns queries and mutations. Preserve the old dispatcher for the non-subscription path and add a regression test that checks execution does not block the calling thread.

### 2. Java client APIs narrow collection generics and reject existing consumer code

**Priority: P1.** [DgsGraphQLClient.java](graphql-dgs-client/src/main/java/com/netflix/graphql/dgs/client/DgsGraphQLClient.java#L30).

The Kotlin declarations emitted `Map<String, ? extends Object>` parameters. The Java replacements use `Map<String, Object>`. This affects `DgsGraphQLClient`, `GraphQLClient`, the Mono/reactive contracts, and their concrete clients.

```java
void execute(DgsGraphQLClient client, Map<String, String> variables) {
    client.executeQuery("query", variables);
}
```

This compiles against the base and fails against the branch: `Map<String,String> cannot be converted to Map<String,Object>`. Corresponding Kotlin code using `Map<String, String>` still compiles; Kotlin collection covariance handles that example.

Related narrowing occurs in response/header constructors, error lists, scalar option maps, and collaborator collections. For example, option constructors change from `Map<Class<?>, ? extends Coercing<?, ?>>` to `Map<Class<?>, Coercing<?, ?>>`. Existing Java implementations of affected interfaces can also encounter signature clashes when recompiling. Generic narrowing preserves erased JVM descriptors, so this category principally breaks source compatibility.

Preserve the previous wildcard signatures on input parameters where the framework reads the supplied collections.

### 3. Previously compiled Kotlin consumers fail even where their source still compiles

**Priority: P1 for rollout.** [DgsContext.java](graphql-dgs/src/main/java/com/netflix/graphql/dgs/context/DgsContext.java#L60) and [GraphQLErrorExtensions.java](graphql-dgs-client/src/main/java/com/netflix/graphql/dgs/client/GraphQLErrorExtensions.java#L34).

These are separate from Kotlin source edits:

- Kotlin calls such as `DgsContext.from(graphQLContext)` were compiled through `DgsContext.Companion`. The branch removes the companion field/class, producing `NoSuchFieldError: Companion`. Keeping the Java static method does not preserve that compiled Kotlin call.
- `GraphQLErrorExtensions(ErrorType.INTERNAL)` still compiles after the migration, but an old consumer invokes the synthetic constructor taking a default-argument mask and `DefaultConstructorMarker`. It now fails with `NoSuchMethodError`.
- Old `HttpResponse.copy(body = "changed")` calls fail with `NoSuchMethodError` for `copy$default`.
- Old `ErrorType.entries` calls fail with `NoSuchMethodError` for `getEntries()`. Recompiling the same source against the Java enum succeeds.
- Removed Kotlin interface `$DefaultImpls` classes can affect consumers compiled with legacy Kotlin default-method conventions. This was identified in the class-file inventory rather than asserted to affect every Kotlin consumer.
- Kotlin objects converted to static utility classes also lose `INSTANCE` or change instance methods to static methods. Examples include `BaseDgsQueryExecutor`, `DataLoaderNameUtil`, and `MultipartVariableMapper`; most of these are implementation APIs.

A rebuild of application sources alone does not rewrite a separately published dependency containing old DGS call sites. The rollout should include dependent libraries, or retain compatibility bridges for the contracts that must remain binary compatible. A source build validation pipeline will not catch every such packaged-library failure.

### 4. Boolean getter names and exception cause return types break Java source and binaries

**Priority: P2.** [DgsSpringGraphQLConfigurationProperties.java](graphql-dgs-spring-graphql/src/main/java/com/netflix/graphql/dgs/springgraphql/autoconfig/DgsSpringGraphQLConfigurationProperties.java#L53) and [DgsInvalidInputArgumentException.java](graphql-dgs/src/main/java/com/netflix/graphql/dgs/exceptions/DgsInvalidInputArgumentException.java#L21).

`getEnabled()` becomes `isEnabled()` in configuration classes. This affects APQ, metrics query/resolver/complexity configuration, strict mode/federation/preparsed provider configuration, and Spring GraphQL async dispatch. Other examples include `getTickerModeEnabled()`, `getSchemaWiringValidationEnabled()`, and `getShowSdlComments()` becoming `is...()` methods.

Confirmed with `new Asyncdispatch(true).getEnabled()`: base compilation/run succeed, branch compilation fails, and the old binary fails with `NoSuchMethodError`. Kotlin property syntax changes from `.enabled` to `.isEnabled`; this is visible in the PR's own test edits. Spring property binding still passed its existing tests, so the finding concerns programmatic API access.

The Kotlin `DgsException` and `DgsInvalidInputArgumentException` overrode `getCause()` with the covariant return type `Exception`. The Java replacements inherit `Throwable.getCause()`, returning `Throwable`. This code compiled against the base:

```java
Exception cause = new DgsInvalidInputArgumentException("bad", new Exception()).getCause();
```

It fails to compile against the branch, and the old binary throws `NoSuchMethodError` for `getCause(): Exception`. Restore the narrower override and retain old getter aliases where compatibility is desired.

### 5. Kotlin source APIs change beyond ordinary named arguments

**Priority: P2; intentional changes need migration guidance.** [HttpResponse.java](graphql-dgs-client/src/main/java/com/netflix/graphql/dgs/client/HttpResponse.java#L23), [DgsRequestData.java](graphql-dgs/src/main/java/com/netflix/graphql/dgs/internal/DgsRequestData.java#L23), and [DgsCustomContextBuilderWithRequest.java](graphql-dgs/src/main/java/com/netflix/graphql/dgs/context/DgsCustomContextBuilderWithRequest.java#L31).

| Existing Kotlin usage | Change required |
|---|---|
| `HttpResponse(statusCode = 200, body = "OK")` | Positional arguments; named arguments are prohibited for Java declarations, including declarations compiled with `-parameters`. |
| Data class `copy`, destructuring, or explicit `componentN` | Construct a replacement object or access getters/properties. |
| `override val extensions` / `override val headers` in `DgsRequestData` implementations | Implement `getExtensions()` / `getHeaders()` methods. The old source fails with “overrides nothing.” |
| Context builder override with `extensions: Map<String, Any>?` | Java `Map<String, ?>` maps to Kotlin `Map<String, *>`; the old override fails. Reactive context builders and context contributors have related changes. |
| `Jackson2DgsJsonMapperAdapter.default()` or `Jackson3DgsJsonMapperAdapter.default()` | Use `defaultMapper()`. The old method disappears. `default` cannot be declared as a Java method identifier, so retaining that JVM method needs a compatibility strategy. |
| `jsonTypeRef<List<Foo>>()` | Use an anonymous `TypeRef<List<Foo>>`. The top-level reified helper is removed. |
| Top-level subscription constants, e.g. `GQL_DATA` | Import from `OperationMessageType`; protocol constants move to `Protocol`. Previously compiled constant uses are normally inlined. |
| `EmptyPayload` object expression | Use `EmptyPayload.INSTANCE`. |
| Explicit `Companion` references/imports | Use the new static API. |

Removed data class operations affect client errors, response/request details, subscription messages and payloads, request data, schema results, data fetcher references, and configuration objects. Java consumers could also call their generated `copy`/`componentN` methods.

Constructors are also removed or reshaped. Examples include no-argument constructors on several configuration types, the all-argument APQ/metrics constructors, and `DgsSchemaProvider` constructors whose component filter changes from Kotlin `Function1` to Java `Predicate`. The new builder is useful, but does not preserve the old constructor descriptors. Several configuration classes also lose structural equality/hash-code behavior.

The PR rewrites Kotlin tests to accommodate these API differences. That is evidence of source incompatibility, not a guarantee that downstream Kotlin sources remain compatible.

### 6. Kotlin nullability contracts are lost on migrated Java APIs

**Priority: P2.** [DgsContext.java](graphql-dgs/src/main/java/com/netflix/graphql/dgs/context/DgsContext.java#L56), [DgsRequestData.java](graphql-dgs/src/main/java/com/netflix/graphql/dgs/internal/DgsRequestData.java#L24), and [HttpResponse.java](graphql-dgs-client/src/main/java/com/netflix/graphql/dgs/client/HttpResponse.java#L42).

Former Kotlin nullable/non-null declarations often become unannotated Java platform types. For example, `DgsContext.requestData`, request headers, and `HttpResponse.body` can be null but no longer carry their previous Kotlin nullability guarantees.

`DgsContext(null, null).requestData.headers` was rejected by the old Kotlin compiler and now compiles, despite dereferencing a null request data object. This reduces compile-time protection for Kotlin consumers and can also affect explicit override/inference choices. Add accurate Java nullability annotations; these can preserve Kotlin type checking without reintroducing Kotlin runtime parameter checks.

### 7. JPMS descriptors newly force optional dependencies at startup

**Priority: P2; applies to module-path consumers.** [Core module-info.java](graphql-dgs/src/main/java/module-info.java#L22) and [Client module-info.java](graphql-dgs-client/src/main/java/module-info.java#L4).

The core now has mandatory `requires tools.jackson.module.kotlin`, although the Gradle dependency is `compileOnly` and mapper creation has a conditional registration path. The client newly requires Jackson 2 datatype/parameter-name modules and Jackson 3 core/databind modules.

With otherwise identical external module paths, module-layer initialization succeeded for the base and failed for the branch when:

- Core was resolved without `tools.jackson.module.kotlin`: `FindException: Module tools.jackson.module.kotlin not found`.
- Client was resolved without legacy `com.fasterxml.jackson.datatype.jdk8`: `FindException: Module com.fasterxml.jackson.datatype.jdk8 not found`.

The module system fails before conditional mapper selection can execute. Review `requires static` for optional implementation dependencies and verify the supported Jackson combinations on the module path. The existing Spring Boot classpath tests do not exercise this path. These probes verify module resolution; they do not establish that all pre-existing JPMS application behavior was supported.

### 8. Jackson 2 error types change

**Priority: P2, depending on consumer error handling.** [Jackson2DgsJsonMapperAdapter.java](graphql-dgs-client/src/main/java/com/netflix/graphql/dgs/client/Jackson2DgsJsonMapperAdapter.java#L67).

The Java translations catch Jackson 2 `JsonProcessingException` and wrap it in `UncheckedIOException`. For malformed JSON `"{"`, the old adapter directly threw `JsonEOFException`; the branch throws `UncheckedIOException` with that exception as its cause. Related wrapping exists in JSON serialization/client paths and the Jackson 2 server mapper.

Kotlin code catching `JsonProcessingException` will now miss these failures. Document the exception change or preserve it with an explicit error contract. The existing JSON mapping tests passing does not verify compatibility of consumer catch clauses.

## Implications for the memo

The memo's major-version candidate rollout is sensible, but it should explicitly state that:

1. Java source compatibility changes in some client and configuration APIs, and some Java binaries fail without recompilation.
2. Kotlin sources need migration edits; Kotlin binaries and separately built libraries can fail even when their source syntax remains valid.
3. Pure Java implementation sources do not yet mean a Kotlin-free runtime. Core still depends on `kotlin-reflect` and coroutines, exposes `KClass` in the input-mapper SPI, and the build continues to import the Kotlin BOM and apply the Kotlin plugin. Kotlin data class and suspend/Flow support retains Kotlin version concerns.
4. Rewriting existing tests keeps functional tests useful but weakens their value as compatibility tests. Validation should include untouched Java/Kotlin consumer fixtures and previously compiled consumer libraries.

Before the release candidate, I would fix the Flow scheduling regression, preserve Java wildcard signatures/getter aliases/covariant cause accessors, annotate nullability, and correct optional module requirements. Then explicitly choose which Kotlin source/binary changes the major release accepts and publish migration instructions with the candidate.

## Temporary verification artifacts

These artifacts were generated under `/tmp` during the review and are not included in the repository. The findings and verification results above are recorded independently of these temporary files.


- Consumer compilation/run results (`/tmp/dgs-review-probes/results.json`).
- Additional runtime/default-constructor/nullability results (`/tmp/dgs-review-probes/extra-results.json`).
- Compiled old API inventory (`/tmp/dgs-api-old.json`), compiled new API inventory (`/tmp/dgs-api-new.json`), and raw API differences (`/tmp/dgs-api-changes.json`). These are structural inventories, including implementation/compiler-generated types; they are not a standalone compatibility verdict and do not resolve inherited members.
- Consumer probe runner (`/tmp/dgs-review-probes.rb`), additional probe runner (`/tmp/dgs-review-extra-probes.rb`), SPI probe runner (`/tmp/dgs-review-spi-probes.rb`), module resolution comparison (`/tmp/dgs-review-module-clean.rb`), and Jackson exception comparison (`/tmp/dgs-review-json-error.rb`).

## Compatibility options

The user requested implementation of the Java-preservable fixes below. These tables distinguish that scope from additional binary bridges and Kotlin source migrations.

### Fixable with ordinary Java implementation and API declarations

| Issue | Compatibility approach |
|---|---|
| Flow scheduling | Restore the previous dispatcher for query/mutation collection. |
| Client collection generics | Restore previous wildcard input signatures. |
| Boolean getters | Retain old get-prefixed aliases. |
| Exception cause return types | Restore the covariant Exception-returning overrides. |
| Nullability | Add accurate Kotlin-recognized annotations. |
| Optional JPMS dependencies | Make optional module requirements optional and verify supported combinations. |
| Jackson exception wrapping | Preserve the original runtime exception contract. Java checked-exception rules require an implementation choice; simply adding throws declarations could itself change source compatibility. |
| Removed constructors | Restore original overloads and defaults. |
| Data class equality/hash/string behavior | Implement the previous behavior explicitly. |
| componentN and positional copy | Restore matching methods. Kotlin destructuring can work with Java componentN methods. |
| Function1 parameter changed to Predicate | Retain a compatible Function1 overload; this keeps a Kotlin runtime type in the API. |
| Context-builder map signature | Preserve the previous Kotlin-facing parameter contract. |
| Java imports of Kotlin file-facade constants | Retain forwarding constants in Java classes with the original names. |

### Binary bridges are generally possible, with additional work

| Missing Kotlin-generated entry point | Compatibility approach |
|---|---|
| Companion field/class | Recreate matching fields, nested classes, methods, and JVM descriptors. |
| Singleton INSTANCE and instance methods | Preserve old singleton entry points. |
| Default-argument constructors | Retain constructors taking the original argument mask and DefaultConstructorMarker. |
| copy$default and other $default methods | Preserve bridge signatures and default-selection behavior. |
| Interface $DefaultImpls | Retain matching helper classes where required by compiled implementations. |
| Enum getEntries() | Restore the static method and Kotlin EnumEntries return type. |

Test bridges using consumers compiled against the old framework. Some bridges retain Kotlin runtime types. A method named default() cannot be declared in Java source because default is a keyword; preserving that exact JVM entry point requires a small Kotlin compatibility layer or generated bytecode.

### Kotlin source features not preserved by ordinary Java declarations alone

| Kotlin source usage | Choice |
|---|---|
| Named arguments | Use positional calls or retain Kotlin API declarations. Java parameter names do not enable Kotlin named arguments. |
| Full default/named-argument calling model | Java overloads preserve many calls, but cannot reproduce the complete Kotlin model. |
| copy(body = "...") | Java copy methods can preserve positional calls; named calls need migration or a Kotlin API layer. |
| override val implementing interface properties | Implement getter methods or retain a Kotlin-declared interface. |
| Reified jsonTypeRef<T>() | Keep a small inline Kotlin helper or require an explicit type token. |
| Kotlin object expression such as EmptyPayload | Use INSTANCE or retain a Kotlin object declaration. |
| Kotlin top-level imports/declarations | Migrate to Java static APIs or retain a Kotlin facade. |

A Java implementation with a small Kotlin API facade can preserve many of these source features. Requiring all public declarations to be Java makes those source migrations necessary.

## Internal downstream impact

Review date: October 2, 2026. Branch: `migrate-kotlin-to-java`, head `56db08d48bf76f99f361157afdfde8c08c37414f`, baseline `83a6b4835765f793d3852555c1a8b81fcd33d065`.

Scope: the Kotlin source-language changes and Kotlin-generated binary entry points from the initial API review above. The implementation/API fixes in the first category are handled separately. No production source files were edited during this downstream investigation, and no downstream builds or deployments were run.

### Conclusion

**Internal impact is observed.** The queried removed companion methods have references in **31 distinct internal repositories**, deduplicated across methods, branches, and dependency versions. This is a set of candidate consumers, not a claim that 31 production applications were individually reproduced failing.

There are also concrete source edits needed in internal Java and Kotlin code. Some are in tests; several are in production code and shared library modules. The most important rollout requirement is to rebuild or bridge the internal DGS authz/metrics integration modules, because their existing Kotlin binaries reference the removed DgsContext companion.

### Evidence and coverage

- **Evidence mode:** known contracts and source searches, using the locally verified PR differences.
- **Outcome:** IMPACT OBSERVED for the queried symbols and inspected source sites.
- **Full indexed commit comparison:** NO DECISION. Quasar returned `BOTH_UNAVAILABLE` for the exact base/head, with no ingestion attempt registered. No main-branch fallback was used.
- Quasar exact-symbol queries cover dependency versions including 12.1.0, 12.0.x, 10.6.0, and 10.4.0. Sourcegraph searches used internal default-branch snapshots and excluded the OSS DGS framework mirror.
- Sourcegraph returned timeout/limit warnings on some broad searches. Those are not used as exhaustive counts. The context-call aggregation completed with `limitHit=false`: 62 files in 11 internal repositories, including the internal DGS integration repository. Explicit Companion calls and Quasar references add additional evidence.
- Quasar freshness results for queried usages generally showed zero stale entries and indexed dates from September 28 through October 2. Unique repository counts below are calculated from returned rows; `dataFreshness.totalRepos` sometimes differs because of branches/versions.
- Quasar did not report implementations of DgsCustomContextBuilderWithRequest even though Sourcegraph confirms two Kotlin implementations. Therefore zero indexed implementations or synthetic-helper references cannot establish absence of impact.
- These are source inspections combined with the earlier old/new consumer compilation and linkage probes. The internal repositories themselves were not built against this branch.

Raw Quasar/search results and inspected source responses were captured in temporary review files (`/tmp/dgs-internal-impact-evidence.json` and `/tmp/dgs-internal-impact-source-evidence.json`). They are not included in this repository; observed counts, limitations, and source references are recorded below.

### 1. Removed companions: observed internal consumers

| Changed contract | Distinct repositories in queried results | Interpretation |
|---|---:|---|
| DgsContext.Companion.getCustomContext, getRequestData, and from overloads | 11 | Implicit Kotlin calls normally recompile against Java static methods; previously compiled callers require bridges or rebuilding. Explicit Companion calls also require source edits. |
| MonoGraphQLClient.Companion.createWithWebClient overloads | 21 | Implicit Kotlin calls require rebuilding; explicit Java/Kotlin Companion calls/imports require source edits. |
| GraphQLRequestOptions.Companion.createCustomObjectMapper overload queried | 4 | Potential binary impact to companion callers; ec-socialite-backend has a production Kotlin call. |
| GraphQLClient.Companion.createCustom | 2 | spkr-keel-nflx and px-domain-graph-service-java. Production Kotlin call confirmed in Keel. |
| Jackson2DgsJsonMapperAdapter.Companion.default | 1 | abe-abdispatch; also a method rename requiring source edits. |

Counts overlap; the union is 31 repositories. The getCustomContext(+1) method alone has nine current indexed repositories, all using DGS 12.1.0. MonoGraphQLClient.createWithWebClient() has 16 distinct repositories across the returned version/branch rows; its other queried overloads add five.

#### Internal framework modules should be rebuilt together

In px-domain-graph-service-java, production Kotlin code calls DgsContext.from in:

- [NetflixAuthzInstrumentation.kt:35](https://sourcegraph.netflix.io/github.netflix.net/corp/px-domain-graph-service-java@f40e3953dcfd880c6787795774c642608caa5cb6/-/blob/graphql-dgs-netflix-authz-instrumentation/src/main/kotlin/com/netflix/graphql/dgs/authz/instrumentation/NetflixAuthzInstrumentation.kt?L35).
- [DgsGraphQLNetflixIpcMetricsTagBridge.kt:100](https://sourcegraph.netflix.io/github.netflix.net/corp/px-domain-graph-service-java@f40e3953dcfd880c6787795774c642608caa5cb6/-/blob/graphql-dgs-netflix-query-metrics/src/main/kotlin/com/netflix/graphql/dgs/metrics/micrometer/DgsGraphQLNetflixIpcMetricsTagBridge.kt?L100).
- NetflixPassportInstrumentation.kt:41.
- DgsGraphQLNetflixRequestMetricsInstrumentation.kt:49.

The earlier review reproduced `NoSuchFieldError: Companion` for an old Kotlin DgsContext.from consumer. These are concrete production call sites of that binary-sensitive API. Upgrading OSS DGS while retaining the old compiled internal integration JARs risks failures even in otherwise Java applications. Recompiling those internal modules against the migrated framework, or keeping the old companion entry points, addresses this specific risk.

#### Explicit companion usage also breaks Java source

An exact literal Sourcegraph search confirmed **five repositories and six call sites** of `CustomMonoGraphQLClient.Companion.createWithWebClient(...)`. Three repositories use it in application code, one in an example application, and one in end-to-end tests:


- [partner-docs-dgs / EnterpriseGatewayClient.java:21](https://sourcegraph.netflix.io/github.netflix.net/corp/partner-docs-dgs@96fb2fbbdd4222927ed16462d2c8b5d4b7da0c91/-/blob/partner-docs-dgs-server/src/main/java/com/netflix/partnerdocsdgs/common/EnterpriseGatewayClient.java?L21).
- test-env-stability / test-env-stability-server/src/main/java/com/netflix/testenvstability/bfg/BfgGatewayClient.java:20.
- che-mps-footage-management / mps-footage-management-server/src/main/java/com/netflix/mps/footagemanagement/mpscore/MpsCoreGraphQLClient.java:28.
- ste-bfg-client-example / bfg-client-example-server/src/main/java/com/netflix/bfgclientexample/BfgGatewayClient.java:18 (example application).

si-workstation-director has two calls in end-to-end tests:

- [BfgGatewayClient.java:22](https://sourcegraph.netflix.io/github.netflix.net/corp/si-workstation-director/-/blob/workstation-director-server/src/e2eTest/java/com/netflix/workstationdirector/e2etest/client/BfgGatewayClient.java?L22).
- [WorkstationDirectorGraphqlClient.java:20](https://sourcegraph.netflix.io/github.netflix.net/corp/si-workstation-director/-/blob/workstation-director-server/src/e2eTest/java/com/netflix/workstationdirector/e2etest/client/WorkstationDirectorGraphqlClient.java?L20).

These five exact-call consumers are a subset of the 21 repositories referencing the queried MonoGraphQLClient companion factory methods.

The old Companion field is accessible through the implementing class. The Java replacement removes it, and static interface methods are not inherited by implementing classes. The replacement should be `MonoGraphQLClient.createWithWebClient(...)`, rather than simply deleting `Companion` from the old expression.

Other explicit Kotlin sites:

- [spt-spt-workflow / FileResolver.kt:18 and 24](https://sourcegraph.netflix.io/github.netflix.net/corp/spt-spt-workflow@f124134db6b950d9d8948a7ef578d287fdad099f/-/blob/src/main/java/com/netflix/sptworkflow/core/graphql/FileResolver.kt?L18): `DgsContext.Companion.getCustomContext(dfe)`.
- [ec-mkt-notifications / StudioGatewayGraphQLClient.kt:8](https://sourcegraph.netflix.io/github.netflix.net/corp/ec-mkt-notifications@8e9a74a0ec3cfb3ad3e4a042d6c2efae2fe93a0a/-/blob/mkt-notifications-studioedge-client/src/main/kotlin/com/netflixstudioedgeclient/service/StudioGatewayGraphQLClient.kt?L8): imports `MonoGraphQLClient.Companion.createWithWebClient`.
- ec-mkt-notifications / mkt-notifications-studioedge-client/src/main/kotlin/com/netflixstudioedgeclient/service/GraphQLClientHandler.kt:5: same import.

These require source edits unless a compatibility façade remains.

### 2. Kotlin source-language changes: confirmed sites

#### Named constructor arguments

**Production:** [che-content-hub-dgs / Exceptions.kt:8](https://sourcegraph.netflix.io/github.netflix.net/corp/che-content-hub-dgs@4dc92800e5f2140e1737268896ab4340ac1adefa/-/blob/content-hub-dgs-server/src/main/kotlin/com/netflix/content/hub/dgs/exceptions/Exceptions.kt?L8).

Three exception subclasses call the DgsException superclass constructor using `message =`, `cause =`, and `errorType =`: DgsPermissionDeniedException, DgsPreconditionFailedException, and DgsBadRequestException. They need positional arguments, even if every Java constructor overload is restored.

**Tests:** [fleet-fleetwide / GraphQLTestHelpers.kt:22–24](https://sourcegraph.netflix.io/github.netflix.net/corp/fleet-fleetwide@681401aadb3d7490d9222043c66242ddce78ca9f/-/blob/fleetwide-common/src/test/kotlin/com/netflix/fleetwide/graphql/GraphQLTestHelpers.kt?L22).

The helper constructs `GraphQLError(message = message, extensions = GraphQLErrorExtensions(errorType = errorType))`. Both named-argument calls need migration.

Quasar reports GraphQLErrorExtensions constructor references in 14 distinct repositories. File-level inspection of all 14 returned repository lists shows 13 have Java test callers; fleet-fleetwide has the Kotlin test helper above. Therefore the constructor reference count must not be presented as 14 confirmed Kotlin default-constructor failures. The fleet helper also uses omitted default parameters, making its old compiled form binary-sensitive; the earlier review reproduced this constructor linkage failure.

#### Reified helper removal

**Production:** [fleet-fleetwide / EnterpriseEdgeClient.kt:2937](https://sourcegraph.netflix.io/github.netflix.net/corp/fleet-fleetwide@681401aadb3d7490d9222043c66242ddce78ca9f/-/blob/fleetwide-common/src/main/kotlin/com/netflix/fleetwide/graphql/EnterpriseEdgeClient.kt?L2937).

It imports `com.netflix.graphql.dgs.client.jsonTypeRef` and calls `jsonTypeRef<List<Application>>()`. Keep the inline Kotlin helper or replace it with an anonymous TypeRef. Because this helper is inline, its removal primarily affects rebuilding source, rather than requiring the helper at runtime in already inlined callers.

#### Mapper default() rename

**Tests:** abe-abdispatch:

- [NfConfigGraphQlClientTest.kt:24](https://sourcegraph.netflix.io/github.netflix.net/corp/abe-abdispatch@cad0c3ef9791c7a43ebbe516f8e18c33f8666486/-/blob/abdispatch-server/src/test/kotlin/com/netflix/abdispatch/clients/NfConfigGraphQlClientTest.kt?L24).
- [DataJunctionGraphQlClientTest.kt:31](https://sourcegraph.netflix.io/github.netflix.net/corp/abe-abdispatch@cad0c3ef9791c7a43ebbe516f8e18c33f8666486/-/blob/abdispatch-server/src/test/kotlin/com/netflix/abdispatch/clients/DataJunctionGraphQlClientTest.kt?L31).

Both use `Jackson2DgsJsonMapperAdapter.default()` and need `defaultMapper()`. Quasar confirms abe-abdispatch as the indexed consumer of the old companion method. sprng-java-testing-docs also contains a documentation example and a commented Java example that should be updated; those are not counted as executable call sites.

### 3. Additional observed source changes that can be avoided with Java compatibility signatures

#### Context-builder map signature

Production Kotlin implementations in:

- [che-stargate / StargateDgsContextBuilder.kt:16–19](https://sourcegraph.netflix.io/github.netflix.net/corp/che-stargate@8517e2186a5b30375a583d7d766d6dadf1817dfb/-/blob/stargate-server/src/main/kotlin/com/netflix/stargate/dgs/StargateDgsContextBuilder.kt?L16).
- [ste-studio-registry / RegistryContext.kt:24–27](https://sourcegraph.netflix.io/github.netflix.net/corp/ste-studio-registry@7e12c72c02d91377ba625cd39295a983df2580f3/-/blob/studio-registry-server/src/main/java/com/netflix/studioregistry/dgs/context/RegistryContext.kt?L24).

Both override `build(extensions: Map<String, Any>?, ...)`. Against this branch's Java `Map<String, ?>` declaration, that override no longer matches, as the earlier SPI fixture verified. Preserve the original Kotlin-facing parameter contract in Java or change the Kotlin override to `Map<String, *>`.

Quasar references the interface in 14 repositories, but most cannot be counted as affected Kotlin overrides. The two sites above are the confirmed examples.

#### Moved subscription constant: Java consumer

[px-memeapi / DgsSSESubscriptionHandler.java:30](https://sourcegraph.netflix.io/github.netflix.net/corp/px-memeapi@af92971c735e90430c9c2fccb877b124f3e9f5d6/-/blob/memeapi-server/src/main/java/com/netflix/meme/api/graphql/subscriptions/DgsSSESubscriptionHandler.java?L30) statically imports:

```java
import static com.netflix.graphql.types.subscription.OperationMessageKt.SSE_GQL_SUBSCRIPTION_DATA;
```

The old facade class disappears. Change the import to OperationMessageType or retain a Java OperationMessageKt forwarding-constant class. This source compatibility issue is fixable in Java. Existing compiled uses of this compile-time constant are normally inlined.

### 4. Categories without confirmed internal consumers

I did not confirm internal uses of:

- Migrated DGS data class `copy` / `copy$default` or destructuring.
- The DGS client ErrorType enum's `entries` accessor.
- Kotlin property overrides implementing DgsRequestData.
- Subscription EmptyPayload object-expression syntax or Kotlin top-level subscription imports.
- Direct executable calls to the migrated internal BaseDgsQueryExecutor, DataLoaderNameUtil, or MultipartVariableMapper singleton APIs.
- Removed `$DefaultImpls` helper calls.

Important exclusions: fleet-fleetwide ErrorType.entries results refer to its own ErrorType, not the DGS enum. Other copy searches matched application-owned models. Internal utility-name results were predominantly documentation, benchmarks, or comments. These were not counted as affected consumers.

Quasar searches did not find the synthetic copy/default/entries helpers, and source searching cannot inspect packaged bytecode. These negatives do not prove absence of old compiled callers. Default-method behavior additionally depends on the Kotlin compiler/JVM-default settings used to build a library.

Checked examples that do not require source edits include the positional HttpResponse constructors in Keel and Socialite. Keel still has separate binary exposure through GraphQLClient.createCustom; the HttpResponse calls themselves should not be mislabeled as default-mask constructor calls, since the old two-argument HttpResponse constructor was explicit.

### 5. Rollout recommendation

1. Rebuild the internal DGS authz and query-metrics modules against the migrated OSS framework as part of the internal candidate release.
2. Apply the small source changes listed above, or retain the corresponding compatibility façades.
3. Decide whether to preserve companion bridges. They have observed internal consumers across 31 repositories; a few bridges could avoid requiring independently published Kotlin libraries to be rebuilt immediately.
4. Include Keel, Scribe, Fleetwide, Content Hub, Stargate, Studio Registry, marketing notifications, and one explicit Java companion consumer in candidate validation.
5. Validate at least one application with an existing published Kotlin library that references DGS. A clean application-source build alone does not exercise that binary scenario.

### Queried companion consumers: complete deduplicated list

- abe-abdispatch
- ads-ads-partner-dataflow
- ads-ads-public-api
- campaign-data-foundations
- che-content-hub-dgs
- che-mps-footage-management
- che-mps-ops-dashboard-api
- che-mps-pulls-dgs
- che-stargate
- dpep-studio-orchestrator-api
- ec-mkt-notifications
- ec-mktp-campaign-extractor
- ec-socialite-backend
- exo-campaign-notifications
- jvm-rocket-enrollment-analyzer
- mce-cdrive-api
- partner-docs-dgs
- pub-cms-asset-pipeline
- pub-pulse-isc
- px-domain-graph-service-java
- px-edgar_server
- rgt-flows-dgs
- si-workstation-director
- spkr-keel-nflx
- spkr-orca-nflx
- spkr-scribe
- spt-spt-workflow
- ste-bfg-client-example
- ste-graphdoctor
- ste-studio-registry
- test-env-stability

These are only the consumers of the queried companion contracts, not an exhaustive inventory of all changes in the PR or all DGS-dependent repositories.
