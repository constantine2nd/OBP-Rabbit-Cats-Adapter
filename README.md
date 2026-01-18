# OBP-Rabbit-Cats-Adapter

A functional, type-safe adapter for connecting the Open Bank Project (OBP) API to Core Banking Systems (CBS) via RabbitMQ messaging.

> **Note to Banks:** Do NOT clone and modify this repository directly.  
> See **[HOW-BANKS-USE-THIS.md](HOW-BANKS-USE-THIS.md)** for the correct way to use this adapter.

## Overview

This adapter acts as a bridge between OBP-API and your Core Banking System:

```
OBP-API ←→ RabbitMQ ←→ This Adapter ←→ Your CBS (REST/SOAP/etc)
```

**Key Features**:

- Plugin Architecture: Implement one interface, get full OBP integration
- Built-in Telemetry: Metrics, logging, and tracing
- Type-Safe: Leverages Scala's type system to catch errors at compile time
- Functional: Pure functional programming with Cats Effect
- Bank-Agnostic: Generic OBP message handling, CBS-specific implementations pluggable
- Environment support: Docker support, health checks, graceful shutdown

## Architecture

This adapter cleanly separates three concerns:

### 1. **North Side** (Generic, Reusable)

- RabbitMQ message consumption/production
- OBP message parsing and routing
- Message correlation and context tracking

### 2. **South Side** (Bank-Specific, Pluggable)

- Local adapter implementations
- Your bank's API integration
- Data mapping between CBS and OBP models

### 3. **Cross-Cutting** (Observability)

- Telemetry interface
- Metrics collection
- Distributed tracing
- Error tracking

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed documentation.

## How to Use This Adapter

**Banks:** Read **[HOW-BANKS-USE-THIS.md](HOW-BANKS-USE-THIS.md)** first! This explains:

- ✅ The correct way to use this adapter (as a dependency)
- ❌ The wrong way (cloning and modifying)
- 📦 Maven dependency vs Docker base image vs Git submodule
- 🏗️ Setting up your own adapter project
- 🔄 How to get updates without merge conflicts

## Quick Start

Try it now:

```bash
# 1. Start RabbitMQ
./start_rabbitmq.sh

# 2. Build and run (in new terminal)
./build_and_run.sh

# 3. Open browser to http://localhost:52345
# 4. Click "Send Get Adapter Info" button
# 5. Watch the console logs!
```

See [QUICKSTART.md](QUICKSTART.md) for detailed instructions.

---

## Detailed Setup

### Prerequisites

- Java 11+
- Maven 3.6+
- RabbitMQ server
- Your Core Banking System API

### 1. Build

```bash
mvn clean package
```

This creates `target/obp-rabbit-cats-adapter.jar`

### 2. Implement Your Local Adapter

Create a new class implementing `LocalAdapter`:

```scala
package com.tesobe.obp.adapter.cbs.implementations

import com.tesobe.obp.adapter.interfaces._
import com.tesobe.obp.adapter.models._
import cats.effect.IO

class YourBankAdapter(
  baseUrl: String,
  apiKey: String,
  telemetry: Telemetry
) extends LocalAdapter {

  override def name: String = "YourBank-Adapter"
  override def version: String = "1.0.0"

  override def getBank(bankId: String, callContext: CallContext): IO[LocalAdapterResult[BankCommons]] = {
    // Your implementation here
    // Call your CBS API, map response to BankCommons
    ???
  }

  override def getBankAccount(...): IO[LocalAdapterResult[BankAccountCommons]] = {
    // Your implementation
    ???
  }

  // Implement ~30 methods for full OBP functionality
  // See interfaces/LocalAdapter.scala for complete interface
}
```

### 3. Configure

Create `.env` file or set environment variables:

```bash
# HTTP Server Configuration (Discovery Page)
HTTP_HOST=0.0.0.0
HTTP_PORT=8080
HTTP_ENABLED=true

# RabbitMQ Configuration
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
RABBITMQ_REQUEST_QUEUE=obp.request
RABBITMQ_RESPONSE_QUEUE=obp.response

# Your CBS Configuration
# (Your bank-specific local adapter will define what config it needs)

# Telemetry
TELEMETRY_TYPE=console
ENABLE_METRICS=true
LOG_LEVEL=INFO
```

### 4. Run

```bash
java -jar target/obp-rabbit-cats-adapter.jar
```

Or with Docker:

```bash
docker build -t obp-adapter .
docker run --env-file .env obp-adapter
```

### 5. Access Discovery Page

Once running, open your browser to:

```
http://localhost:8080
```

The discovery page shows:

- Health & Status - Health check and readiness endpoints
- RabbitMQ - Connection info and management UI link
- Observability - Metrics and logging configuration
- CBS Configuration - Core Banking System settings
- Documentation - Links to OBP resources

Available Endpoints:

- `GET /` - Discovery page (HTML)
- `GET /health` - Health check (JSON)
- `GET /ready` - Readiness check (JSON)
- `GET /info` - Service info (JSON)

Example health check:

```bash
curl http://localhost:8080/health
```

Response:

```json
{
  "status": "healthy",
  "service": "OBP-Rabbit-Cats-Adapter",
  "version": "1.0.0-SNAPSHOT",
  "timestamp": "1704067200000"
}
```

## Local Adapter Interface

Your local adapter must implement these key operations:

```scala
trait LocalAdapter {
  // Bank Operations
  def getBank(bankId: String, ...): IO[LocalAdapterResult[BankCommons]]
  def getBanks(...): IO[LocalAdapterResult[List[BankCommons]]]

  // Account Operations
  def getBankAccount(bankId: String, accountId: String, ...): IO[LocalAdapterResult[BankAccountCommons]]
  def getBankAccounts(...): IO[LocalAdapterResult[List[BankAccountCommons]]]
  def createBankAccount(...): IO[LocalAdapterResult[BankAccountCommons]]

  // Transaction Operations
  def getTransaction(...): IO[LocalAdapterResult[TransactionCommons]]
  def getTransactions(...): IO[LocalAdapterResult[List[TransactionCommons]]]
  def makePayment(...): IO[LocalAdapterResult[TransactionCommons]]

  // Customer Operations
  def getCustomer(...): IO[LocalAdapterResult[CustomerCommons]]
  def createCustomer(...): IO[LocalAdapterResult[CustomerCommons]]

  // ... and more (see interfaces/LocalAdapter.scala)
}
```

Note: You don't need to implement all operations at once. Start with the operations your bank needs, return `LocalAdapterResult.Error` for unimplemented ones.

## Example: Mock Adapter

A mock adapter is provided for testing:

```scala
class MockLocalAdapter extends LocalAdapter {
  override def getBank(bankId: String, callContext: CallContext): IO[LocalAdapterResult[BankCommons]] = {
    IO.pure(LocalAdapterResult.success(
      BankCommons(
        bankId = bankId,
        shortName = "Mock Bank",
        fullName = "Mock Bank for Testing",
        logoUrl = "https://example.com/logo.png",
        websiteUrl = "https://example.com"
      ),
      callContext
    ))
  }

  // ... other operations
}
```

## Telemetry

The adapter includes comprehensive telemetry:

### Console Telemetry (Development)

```scala
val telemetry = new ConsoleTelemetry()
```

Output:

```
[INFO][CID: 1flssoftxq0cr1nssr68u0mioj] Message received: type=obp.getBank queue=obp.request
[INFO][CID: 1flssoftxq0cr1nssr68u0mioj] CBS operation started: getBank
[INFO][CID: 1flssoftxq0cr1nssr68u0mioj] CBS operation success: getBank duration=45ms
[INFO][CID: 1flssoftxq0cr1nssr68u0mioj] Response sent: type=obp.getBank success=true
```

### Prometheus Telemetry (Production)

```scala
val telemetry = new PrometheusTelemetry(prometheusConfig)
```

Exposes metrics at `/metrics`:

- `obp_messages_received_total{type="obp.getBank"}`
- `obp_messages_processed_seconds{type="obp.getBank"}`
- `obp_cbs_operation_duration_seconds{operation="getBank"}`
- `obp_cbs_operation_errors_total{operation="getBank",code="timeout"}`

### Custom Telemetry

Implement the `Telemetry` trait for your monitoring stack:

```scala
class YourTelemetry extends Telemetry {
  override def recordMessageProcessed(...) = {
    // Send to Datadog, New Relic, etc.
    ???
  }
  // ... implement other methods
}
```

## Message Flow

1. **OBP-API** sends message to RabbitMQ `obp.request` queue
2. **Adapter** consumes message, extracts correlation ID
3. **MessageRouter** routes to appropriate handler based on message type
4. **Handler** calls your `LocalAdapter` implementation
5. **Your Adapter** calls your CBS API
6. **Response** mapped to OBP format and sent to `obp.response` queue
7. **OBP-API** receives response and returns to client

All steps are instrumented with telemetry.

## OBP Message Types

The adapter handles these OBP message types:

- `obp.getBank` - Get bank information
- `obp.getBankAccount` - Get account details
- `obp.getTransaction` - Get transaction details
- `obp.getTransactions` - Get transaction list
- `obp.makePayment` - Create payment/transfer
- `obp.getCustomer` - Get customer information
- `obp.createCustomer` - Create new customer
- `obp.getCounterparty` - Get counterparty details
- `obp.getCurrentFxRate` - Get exchange rate
- ... and many more (see OBP message docs)

## Project Structure

```
src/main/scala/com/tesobe/obp/adapter/
├── config/                         # Configuration
│   └── Config.scala
├── models/                         # OBP message models
│   └── OBPModels.scala
├── messaging/                      # RabbitMQ handling
│   ├── RabbitMQConsumer.scala
│   └── RabbitMQProducer.scala
├── handlers/                       # Message handlers
│   ├── BankHandlers.scala
│   ├── AccountHandlers.scala
│   └── TransactionHandlers.scala
├── interfaces/                     # Core contracts
│   └── LocalAdapter.scala         ← YOU IMPLEMENT THIS
├── cbs/implementations/            # CBS implementations
│   ├── MockLocalAdapter.scala     # For testing
│   ├── RestLocalAdapter.scala     # Generic REST
│   └── YourBankAdapter.scala      ← YOUR CODE HERE
├── telemetry/                      # Observability
│   ├── Telemetry.scala
│   ├── ConsoleTelemetry.scala
│   └── PrometheusTelemetry.scala
└── AdapterMain.scala               # Entry point
```

## Testing

### Unit Tests

Test your local adapter in isolation:

```scala
class YourBankAdapterSpec extends CatsEffectSuite {
  test("getBank returns bank data") {
    val adapter = new YourBankAdapter(mockHttp, NoOpTelemetry)
    val result = adapter.getBank("test-bank", CallContext("test-123"))

    result.map {
      case LocalAdapterResult.Success(bank, _, _) =>
        assertEquals(bank.bankId, "test-bank")
      case LocalAdapterResult.Error(code, msg, _, _) =>
        fail(s"Expected success, got error: $code - $msg")
    }
  }
}
```

### Integration Tests

Use `MockLocalAdapter` to test message flow without real CBS.

### End-to-End Tests

Test with real RabbitMQ and mock CBS:

```bash
# Start RabbitMQ
docker-compose up -d rabbitmq

# Run adapter with mock adapter
LOCAL_ADAPTER_TYPE=mock java -jar target/obp-rabbit-cats-adapter.jar

# Send test message to obp.request queue
# Verify response in obp.response queue
```

## Error Handling

The adapter provides consistent error handling:

```scala
// In your local adapter
def getBank(...): IO[LocalAdapterResult[BankCommons]] = {
  httpClient.get(url)
    .map(response => LocalAdapterResult.success(mapToBankCommons(response), callContext))
    .handleErrorWith {
      case _: TimeoutException =>
        IO.pure(LocalAdapterResult.error("CBS_TIMEOUT", "Request timed out", callContext))
      case _: NotFoundException =>
        IO.pure(LocalAdapterResult.error("BANK_NOT_FOUND", "Bank does not exist", callContext))
      case e: Exception =>
        IO.pure(LocalAdapterResult.error("CBS_ERROR", e.getMessage, callContext))
    }
}
```

Errors are:

- Logged with correlation ID
- Sent as telemetry events
- Returned to OBP with proper error codes
- Include backend messages for debugging

## Performance

- **Async**: All I/O is non-blocking with Cats Effect
- **Streaming**: RabbitMQ messages processed as stream
- **Backpressure**: Configurable prefetch count prevents overload
- **Connection Pooling**: HTTP clients use connection pools
- **Retries**: Configurable retry logic with exponential backoff

## Deployment

### Docker

```dockerfile
FROM openjdk:11-jre-slim
WORKDIR /app
COPY target/obp-rabbit-cats-adapter.jar .
CMD ["java", "-jar", "obp-rabbit-cats-adapter.jar"]
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: obp-adapter
spec:
  replicas: 3
  template:
    spec:
      containers:
        - name: adapter
          image: obp-adapter:latest
          env:
            - name: RABBITMQ_HOST
              valueFrom:
                configMapKeyRef:
                  name: obp-config
                  key: rabbitmq.host
            - name: CBS_BASE_URL
              valueFrom:
                secretKeyRef:
                  name: cbs-credentials
                  key: base-url
```

## Configuration Reference

### HTTP Server

| Variable       | Description        | Default   |
| -------------- | ------------------ | --------- |
| `HTTP_HOST`    | HTTP server host   | `0.0.0.0` |
| `HTTP_PORT`    | HTTP server port   | `8080`    |
| `HTTP_ENABLED` | Enable HTTP server | `true`    |

### RabbitMQ

| Variable                  | Description          | Default        |
| ------------------------- | -------------------- | -------------- |
| `RABBITMQ_HOST`           | RabbitMQ server host | `localhost`    |
| `RABBITMQ_PORT`           | RabbitMQ server port | `5672`         |
| `RABBITMQ_USERNAME`       | Username             | `guest`        |
| `RABBITMQ_PASSWORD`       | Password             | `guest`        |
| `RABBITMQ_VIRTUAL_HOST`   | Virtual host         | `/`            |
| `RABBITMQ_REQUEST_QUEUE`  | Request queue name   | `obp.request`  |
| `RABBITMQ_RESPONSE_QUEUE` | Response queue name  | `obp.response` |
| `RABBITMQ_PREFETCH_COUNT` | Messages to prefetch | `10`           |

### Redis

| Variable        | Description  | Default     |
| --------------- | ------------ | ----------- |
| `REDIS_HOST`    | Redis host   | `localhost` |
| `REDIS_PORT`    | Redis port   | `6379`      |
| `REDIS_ENABLED` | Enable Redis | `true`      |

### Telemetry

| Variable         | Description                    | Default   |
| ---------------- | ------------------------------ | --------- |
| `TELEMETRY_TYPE` | Type (console/prometheus/noop) | `console` |
| `ENABLE_METRICS` | Enable metrics                 | `true`    |
| `LOG_LEVEL`      | Log level                      | `INFO`    |

## FAQ

**Q: Do I need to implement all 30+ methods in LocalAdapter?**

A: No. Start with the operations you need. Return `LocalAdapterResult.Error("NOT_IMPLEMENTED", ...)` for others.

**Q: Can I use SOAP instead of REST?**

A: Yes. Implement `LocalAdapter` to call SOAP endpoints. The interface is transport-agnostic.

**Q: How do I handle authentication to my CBS?**

A: Handle it in your `LocalAdapter` implementation. The adapter provides config for common auth types.

**Q: Can I run multiple adapters for different banks?**

A: Yes. Each adapter instance can have a different `LocalAdapter` implementation.

**Q: What if my CBS API doesn't map directly to OBP models?**

A: That's expected. Your `LocalAdapter` implementation does the mapping. This is bank-specific logic.

**Q: How do I debug message processing?**

A: Use `ConsoleTelemetry` in development. Every message includes a correlation ID for tracing.

## Contributing

Contributions welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## License

Apache License 2.0

## Support

- **How Banks Use This**: [HOW-BANKS-USE-THIS.md](HOW-BANKS-USE-THIS.md) ⭐
- **Architecture**: [ARCHITECTURE.md](ARCHITECTURE.md)
- **Separation of Concerns**: [SEPARATION-OF-CONCERNS.md](SEPARATION-OF-CONCERNS.md)
- **Issues**: [GitHub Issues](https://github.com/OpenBankProject/OBP-Rabbit-Cats-Adapter/issues)
- **OBP Wiki**: [https://github.com/OpenBankProject/OBP-API/wiki](https://github.com/OpenBankProject/OBP-API/wiki)
- **Community**: Join OBP Rocket Chat

## Credits

Built with:

- [Scala](https://www.scala-lang.org/) - Programming language
- [Cats Effect](https://typelevel.org/cats-effect/) - Functional effects
- [fs2](https://fs2.io/) - Functional streams
- [fs2-rabbit](https://fs2-rabbit.profunktor.dev/) - RabbitMQ client
- [http4s](https://http4s.org/) - HTTP client
- [Circe](https://circe.github.io/circe/) - JSON library

---
