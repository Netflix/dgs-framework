Thanks for being interested in the DGS framework!
This guide helps to find the most efficient way to contribute, ask questions, and report issues.

Code of conduct
-----

Please review our [code of conduct](CODE_OF_CONDUCT.md).

What is the DGS framework
----

The DGS framework aims to make GraphQL server development easy, based on Spring Boot.
It also comes with a code generation plugin to generate Java/Kotlin code from a GraphQL schema.

The DGS framework is written in Kotlin, but primarily targets Java developers. Each feature must work equally well in
Java and Kotlin. The framework is based on Spring Boot, and does not target other environments. The framework is
designed to have minimal external dependencies aside from Spring.

I have a question!
-----

We have a dedicated [discussion forum](https://github.com/Netflix/dgs-framework/discussions) for asking "how to"
questions and to discuss ideas. The discussion forum is a great place to start if you're considering creating a feature
request or work on a Pull Request.
*Please do not create issues to ask questions.*

Please also consider if a question is directly related to DGS. Questions such as "how do I persist my data" are
unrelated to DGS, and it's better to find an answer in general Spring Boot documentation.

I want to contribute!
------

We welcome Pull Requests and already had many outstanding community contributions!
Creating and reviewing Pull Requests take considerable time. This section helps you set up for a smooth Pull Request
experience.

It's a great idea to discuss the new feature you're considering on
the [discussion forum](https://github.com/Netflix/dgs-framework/discussions) before writing any code. There are often
different ways you can implement a feature. Getting some discussion about different options helps shape the best
solution. When starting directly with a Pull Request, there is the risk of having to make considerable changes.
Sometimes that is the best approach, though!
Showing an idea with code can be very helpful; be aware that it might be throw-away work. Some of our best Pull Requests
came out of multiple competing implementations, which helped shape it to perfection.

Also, consider that not every feature is a good fit for the framework. A few things to consider are:

* Is it increasing complexity for the user, or might it be confusing?
* Does it, in any way, break backward compatibility (this is seldom acceptable)
* Does it require new dependencies (this is rarely acceptable for core modules)
* Should the feature be implemented in the main dgs-framework repository, or would it be better to set up a separate
  repository? Especially for integration with other systems, a separate repository is often the right choice because the
  life-cycle of it will be different.
* Does the feature work equally well in Java and Kotlin?

Of course, for more minor bug fixes and improvements, the process can be more light-weight.

We'll try to be responsive to Pull Requests. Do keep in mind that because of the inherently distributed nature of open
source projects, responses to a PR might take some time because of time zones, weekends, and other things we may be
working on.

I want to report an issue
-----

If you found a bug, it is much appreciated if you create an issue. Please include clear instructions on how to reproduce
the issue, or even better, include a test case on a branch. Make sure to come up with a descriptive title for the issue
because this helps while organizing issues.

I have a great idea for a new feature
----
Many features in the framework have come from ideas from the community. If you think something is missing or certain use
cases could be supported better, let us know!
You can do so by opening a discussion on the [discussion forum](https://github.com/Netflix/dgs-framework/discussions).
Provide as much relevant context to why and when the feature would be helpful. Providing context is especially important
for "Support XYZ" issues since we might not be familiar with what "XYZ" is and why it's useful. If you have an idea of
how to implement the feature, include that as well.

Once we have decided on a direction, it's time to summarize the idea by creating a new issue.


Working on the code base
====

IDE setup
-----
The DGS codebases are Kotlin based. We strongly recommend using Intellij because of the excellent support for Kotlin and
Gradle. You can use the free [Intellij Community Edition](https://www.jetbrains.com/idea/download/).

Clone and open the project using the "project from version control feature" and let Gradle import all dependencies. Note
that we build on Java 8, so a Java 8 JDK is required. If you don't have a JDK, you can use [sdkman](https://sdkman.io/)
or [Intellij](https://www.jetbrains.com/help/idea/sdk.html) to install one. Because almost all the code is Kotlin, we
don't miss any language features of newer Java releases while supporting a broad range of older releases.

Code conventions
-----
We use the standard Kotlin coding conventions. Intellij should select the correct style automatically because we checked
in the `.idea/codeStyle` folder. Furthermore, we're also using [Kotlinter](https://plugins.gradle.org/plugin/org.jmailen.kotlinter). You can run formatting manually using Gradle:

```bash
./gradlew lintKotlin  # lint kotlin sources
./gradlew :graphql-dgs-extended-validation:lintKotlin  # lint kotlin sources for a single module
./gradlew formatKotlin # format Kotlin Sources
```

We recommend installing a Git push hook to run the style check to prevent failing the build for your branch.

```
./gradlew installKotlinterPrePushHook
```

Pull Request builds
----
Pushing to a branch automatically kicks off a build. The build will be linked in the Pull Request, and under
the [CI action](https://github.com/Netflix/dgs-framework/actions/workflows/ci.yml) on GitHub.
