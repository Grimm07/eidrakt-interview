# Quota API

## Assignment Section
### Design Choices
- For content negotiation, I chose the native `kotlinx.serialization` library. While this works well for kotlin-native applications, introducing Java into the application can create conflicts. At that point, it is recommended to migrate to `GSON`
- In case of further expansion of routes, an interface `ServiceRoute` was created.
- Plugins are separated from the routing layer to organize the code more effectively. This could be broken out further into sub-categories, but due to scope was not necessary.
- `Call Logging` & `CallId` were added for tracing
- `SLF4J` & `Logback` added for MDC Metadata
- `OpenAPI` spec was added for documentation accessibility & `Swagger` was added for code documentation generation
- Dependencies are managed in `gradle.properties` to keep them organized & centralized
- I intentionally put the api-key into the MDC context & logs. In practice, you would **never** put items like this in MDC or logs, but due to the scope I put it in to convey the desire to track identification & independent requests
### Trade-offs
- JAR deliverable was selected to reduce complexity (in practice would opt for a containerized version)
- Used a SAM interface instead of a regular one for brevity - if more methods were desired (such as for handling errors per route), this would need modified
- Added stress tests 
### Changes for Scaling
Turning this into a distributed system immediately changes the complexity of the project (as with any distributed component).

First, we must take into account the type of scaling - vertical vs horizontal.

Additionally, for horizontal scaling, we must migrate out of an in-memory record of usage.
- This is another exponential increase in complexity due to the immediate consideration of replication & partitioning, as well as leader selection for the data store.
- We must also handle the cases of deadlocks and race conditions for read & writes to storage to ensure the quota is not overused and/or updated incorrectly
  - Snapshot isolation is a common solution to this problem - here each transaction will read from a snapshot of the database to see all the transactions that exists at the start of the transaction
    - This is not helpful, on long-running processes (read-only queries like backups & analytics) due to potential data changes mid-transaction.
    - There is also Serializable Snapshot Isolation, but this approach is still experimental
- Another change that would need considered is schema changes. Considering the flexibility of the schema up front makes future changes easier to implement
- Going along with the above bullet, serialization of the data is another part in need of consideration when choosing the correct data store for the solution
  - While JSON is easy for humans to read, it increases the data by around 33% - consider a binary data type if speed is an absolute must.
- Error handling & fault tolerance becomes more of a problem in a large scale operation. At this point, you must consider what happens when a token is used, but the work was not finished.
- Similarly, you must ensure a request is not processed more than once in a highly distributed manner0
Another complexity that gets harder when scaling outside the same cluster (exponentially so when scaling outside the same data center), is clock synchronization.
- In a single cluster, where time synchronization is not high priority, you can use the system clock. When moving to a distributed cluster across continents, those system clocks become unreliable. Thus, you need to set up a synchronized time-server.

### Where I used Generative AI
- Generative AI was used to expand on the existing test suite.
### Tests / Example Payloads
Tests are included under `src/test` - includes Unit & Stress Testing
## Features

Here's a list of features included in this project:

| Name                                                                   |                                    Description                                     |
|------------------------------------------------------------------------|:----------------------------------------------------------------------------------:|
| [Routing](https://start.ktor.io/p/routing)                             |                         Provides a structured routing DSL                          |
| [OpenAPI](https://start.ktor.io/p/openapi)                             |                            Serves OpenAPI documentation                            |
| [Status Pages](https://start.ktor.io/p/status-pages)                   |                       Provides exception handling for routes                       |
| [Call Logging](https://start.ktor.io/p/call-logging)                   |                                Logs client requests                                |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)     | Provides automatic content conversion according to Content-Type and Accept headers |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization) |           Handles JSON serialization using kotlinx.serialization library           |
| [Rate Limiting](https://start.ktor.io/p/ktor-server-rate-limiting)     |                    Manage request rate limiting as you see fit                     |
| [Call ID](https://start.ktor.io/p/callid)                              |                         Allows to identify a request/call.                         |
| [Kotest](https://kotest.io)                                            |                                 Testing Framework                                  |
| [Mockk](https://mockk.io)                                              |                                   Mock Framework                                   |

## Building & Running

To build or run the project, use one of the following tasks:

| Task                                    | Description                                                          |
|-----------------------------------------|----------------------------------------------------------------------|
| `./gradlew test`                        | Run the tests                                                        |
| `./gradlew build`                       | Build everything                                                     |
| `./gradlew buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `./gradlew buildImage`                  | Build the docker image to use with the fat JAR                       |
| `./gradlew publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `./gradlew run`                         | Run the server                                                       |
| `./gradlew runDocker`                   | Run using the local docker image                                     |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

