Thanks for being interested in the DGS framework!
This guide helps finding the most efficient way to contribute, ask questions and report issues.

What is the DGS framework
----

The DGS framework aims to make GraphQL server development easy, based on Spring Boot.
It also comes with a code generation plugin to generate Java/Kotlin code from a GraphQL schema.

The DGS framework is written in Kotlin, but primarily targets Java developers. Each feature must work equally well in Java and Kotlin. r
The framework is based on Spring Boot, and does not target other environments.
The framework is designed to have minimal external dependencies aside from Spring.

For the foreseeable future 

I have a question!
-----

We have dedicated [discussion forum](https://github.com/Netflix/dgs-framework/discussions) for asking "how to" questions and to discuss ideas.
This is a great place to start if you're considering creating a feature request or work on a Pull Request.
Please do not create issues just to ask questions.
We need to keep the list of issues managable, and only keep issues that are directly actionable.

Please also consider if a question is directly related to DGS.
Questions such as "how do I persist my data" are unrelated to DGS and it's better to find an answer in general Spring Boot documentation.

I want to contribute!
------

Pull Requests are very much welcomed!
Creating, but also reviewing, pull requests take considerable time.
This section helps you set up for a smooth Pull Request experience.

It's a great idea to discuss the new feature you're considering on the [discussion forum](https://github.com/Netflix/dgs-framework/discussions) before writing any code.
There are often different ways a feature could be implemented.
Getting some discussion about different options help shape the best solution.
When starting directly with a Pull Request there is the risk of having to make considerable changes.

Also consider that not every feature is a good fit for the framework.
A few things to consider are:

* Is it increasing complexity for the user, or might it be confusing?
* Does it, in any way, break backwards compatibility (this is almost never acceptable)
* Does it require new dependencies (this is almost never acceptable for core modules)
* Should the feature be implemented in the main dgs-framework repository, or would it be better to set up a separate repository? This might be better for integrations with other systems, that may have a different life-cycle than the framework.
* Does the feature work equally well in Java and Kotlin?

Of course, for smaller bug fixes and improvements the process can be more light-weight.

We'll try to be responsive to Pull Requests.
Do keep in mind that because of the inherent distributed nature of open source projects, responses to a PR might be delayed by time zones, weekends, and other things we may be working on. 

I want to report an issue
-----

If you found a bug it is much appreciated if you create an issue.
Please include clear instructions how to reproduce the issue, or even better, include a test case on a branch.
Make sure to come up with a descriptive title for the issue, because this really helps while organizing issues.

I have a great idea for a new feature
----

Please open a discussion on the [discussion forum](https://github.com/Netflix/dgs-framework/discussions) first.
Provide as much relevant context to why and when the feature would be helpful.
This is specially important for issues such as "Support XYZ", since we might not be familiar with what "XYZ" is and why it's useful.
If you have an idea how to implement the feature, include that as well.



