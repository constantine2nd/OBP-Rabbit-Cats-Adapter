# OBP-Rabbit-Cats-Adapter Architecture

## Overview

This adapter bridges the Open Bank Project (OBP) API and Core Banking Systems (CBS) using RabbitMQ for messaging. The architecture is designed with clear separation of concerns to maximize reusability and minimize bank-specific customization.

## Design Principles

1. **Separation of Concerns**: CBS-specific code is isolated from generic OBP message handling
2. **Plugin Architecture**: Multiple CBS implementations can coexist
3. **Observability First**: Telemetry is a first-class concern, separate from business logic
4. **Functional Programming**: Uses Cats Effect for pure functional effects
5. **Type Safety**: Leverages Scala's type system to prevent errors at compile time

## Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                         OBP-API                             │
│                      (RabbitMQ Client)                      │
└─────────────────────┬───────────────────────────────────────┘
                      │ RabbitMQ Messages
                      │ (obp.request / obp.response)
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                   ADAPTER - NORTH SIDE                      │
│                  (Generic, Reusable)                        │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │   RabbitMQ Consumer/Producer (messaging/)           │   │
│  │   - Queue management                                │   │
│  │   - Message routing                                 │   │
│  │   - Correlation ID tracking                         │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │   OBP Message Models (models/)                      │   │
│  │   - CallContext, OutboundAdapterCallContext         │   │
│  │   - InboundAdapterCallContext                       │   │
│  │   - BankCommons, AccountCommons, etc.               │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │   Message Handlers (handlers/)                      │   │
│  │   - Route messages by type (obp.getBank, etc.)      │   │
│  │   - Orchestrate CBS calls                           │   │
│  │   - Build responses                                 │   │
│  └─────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────┘
                       │ LocalAdapter Interface
                       ▼
┌─────────────────────────────────────────────────────────────┐
│               ADAPTER - INTERFACE LAYER                     │
│                  (contracts/)                               │
├─────────────────────────────────────────────────────────────┤
│  trait LocalAdapter {                                       │
│    def getBank(...): IO[AdapterResponse[BankCommons]]          │
│    def getBankAccount(...): IO[...]                        │
│    def makePayment(...): IO[...]                           │
│    // ... all CBS operations                               │
│  }                                                          │
└──────────────────────┬──────────────────────────────────────┘
                       │ Implementation
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                   ADAPTER - SOUTH SIDE                      │
│              (CBS-Specific, Customizable)                   │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │   REST CBS   │  │   SOAP CBS   │  │   Mock CBS   │     │
│  │   Adapter    │  │   Adapter    │  │   Adapter    │     │
│  │              │  │              │  │              │     │
│  │ implements   │  │ implements   │  │ implements   │     │
│  │ LocalAdapter │  │ LocalAdapter │  │ LocalAdapter │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
│         │                  │                  │             │
│         └──────────────────┴──────────────────┘             │
│                            │                                │
└────────────────────────────┼────────────────────────────────┘
                             │ HTTP/SOAP/DB/etc.
                             ▼
                    ┌─────────────────┐
                    │  Core Banking   │
                    │     System      │
                    └─────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   CROSS-CUTTING CONCERNS                    │
│                     (telemetry/)                            │
├─────────────────────────────────────────────────────────────┤
│  trait Telemetry {                                          │
│    - Message metrics                                        │
│    - CBS operation metrics                                  │
│    - Performance monitoring                                 │
│    - Error tracking                                         │
│    - Distributed tracing                                    │
│  }                                                          │
│                                                             │
│  Implementations:                                           │
│    - ConsoleTelemetry (dev)                                │
│    - PrometheusTelemetry (production)                      │
│    - DatadogTelemetry (production)                         │
│    - NoOpTelemetry (testing)                               │
└─────────────────────────────────────────────────────────────┘
```

## Directory Structure

```
src/main/scala/com/tesobe/obp/adapter/
├── config/                    # Configuration (generic)
│   └── Config.scala          # RabbitMQ, CBS, app config
│
├── models/                    # OBP Message Models (generic)
│   └── OBPModels.scala       # All OBP data types
│
├── messaging/                 # RabbitMQ handling (generic)
│   ├── RabbitMQConsumer.scala
│   ├── RabbitMQProducer.scala
│   └── MessageRouter.scala
│
├── handlers/                  # Message handlers (generic)
│   ├── BankHandlers.scala
│   ├── AccountHandlers.scala
│   ├── TransactionHandlers.scala
│   └── CustomerHandlers.scala
│
├── interfaces/                # Contracts (generic)
│   └── LocalAdapter.scala    # THE interface CBS must implement
│
├── cbs/                       # CBS implementations (bank-specific)
│   └── implementations/
│       ├── RestLocalAdapter.scala     # HTTP REST implementation
│       ├── SoapLocalAdapter.scala     # SOAP implementation
│       ├── MockLocalAdapter.scala     # Testing/demo
│       └── YourBankAdapter.scala    # Bank-specific impl
│
├── telemetry/                 # Observability (generic framework)
│   ├── Telemetry.scala       # Interface
│   ├── ConsoleTelemetry.scala
│   ├── PrometheusTelemetry.scala
│   └── CompositeTelemetry.scala  # Combine multiple
│
└── AdapterMain.scala          # Main entry point
```

## Key Interfaces

### 1. LocalAdapter (interfaces/LocalAdapter.scala)

**Purpose**: Define what operations a CBS must support

**Bank Developers**: Implement this trait for your specific CBS

```scala
trait LocalAdapter {
  def name: String
  def version: String

  // Core operations
  def getBank(bankId: String, callContext: CallContext): IO[AdapterResponse[BankCommons]]
  def getBankAccount(...): IO[AdapterResponse[BankAccountCommons]]
  def makePayment(...): IO[AdapterResponse[TransactionCommons]]
  // ... 30+ operations covering OBP functionality
}
```

**Key Points**:

- All methods return `IO[AdapterResponse[T]]` for pure functional effects
- `AdapterResponse` is a sealed trait with `Success` and `Error` cases
- `CallContext` contains correlation ID, user info, auth context
- Bank-specific logic stays in your implementation

### 2. Telemetry (telemetry/Telemetry.scala)

**Purpose**: Observability and monitoring

**Operations Teams**: Choose or implement telemetry backend

```scala
trait Telemetry {
  // Message lifecycle
  def recordMessageReceived(...)
  def recordMessageProcessed(...)
  def recordMessageFailed(...)

  // CBS operations
  def recordCBSOperationStart(...)
  def recordCBSOperationSuccess(...)
  def recordCBSOperationFailure(...)

  // Business metrics
  def recordPaymentSuccess(...)
  def recordAccountCreated(...)

  // Tracing
  def startSpan(...): IO[String]
  def endSpan(...)
}
```

**Key Points**:

- Telemetry is injected, not hardcoded
- Multiple implementations can coexist
- NoOp implementation for testing
- Console implementation for development

## Message Flow

### 1. Inbound Message from OBP

```
OBP-API sends message to RabbitMQ (obp.request queue)
    ↓
RabbitMQConsumer receives message
    ↓
Telemetry.recordMessageReceived()
    ↓
MessageRouter extracts message type (e.g., "obp.getBank")
    ↓
Route to appropriate Handler (e.g., BankHandlers)
    ↓
Handler calls LocalAdapter.getBank()
    ↓
LocalAdapter implementation makes CBS call
    ↓
Response wrapped in AdapterResponse
    ↓
Handler builds InboundAdapterCallContext
    ↓
RabbitMQProducer sends response to obp.response queue
    ↓
Telemetry.recordMessageProcessed()
```

### 2. Error Handling

```
If CBS call fails:
    ↓
AdapterResponse.Error returned
    ↓
Telemetry.recordCBSOperationFailure()
    ↓
Handler builds error InboundAdapterCallContext
    ↓
Error response sent to OBP with proper error codes
    ↓
Telemetry.recordMessageFailed()
```

## Implementing a New Local Adapter

### Step 1: Create Implementation Class

```scala
package com.tesobe.obp.adapter.cbs.implementations

import com.tesobe.obp.adapter.interfaces._
import com.tesobe.obp.adapter.models._
import cats.effect.IO

class MyBankAdapter(
  baseUrl: String,
  apiKey: String,
  telemetry: Telemetry
) extends LocalAdapter {

  override def name: String = "MyBank-REST-Adapter"
  override def version: String = "1.0.0"

  override def getBank(bankId: String, callContext: CallContext): IO[AdapterResponse[BankCommons]] = {
    for {
      // Start telemetry
      _ <- telemetry.recordCBSOperationStart("getBank", callContext.correlationId)

      // Make CBS HTTP call
      result <- httpClient.get(s"$baseUrl/banks/$bankId")
        .map(response =>
          // Map CBS response to OBP BankCommons
          AdapterResponse.success(
            BankCommons(
              bankId = response.id,
              shortName = response.name,
              fullName = response.fullName,
              // ... map all fields
            ),
            callContext
          )
        )
        .handleErrorWith(error =>
          IO.pure(AdapterResponse.error(
            "BANK_NOT_FOUND",
            error.getMessage,
            callContext
          ))
        )

      // End telemetry
      _ <- telemetry.recordCBSOperationSuccess("getBank", callContext.correlationId, duration)
    } yield result
  }

  // Implement all other LocalAdapter methods...
}
```

### Step 2: Register Adapter

```scala
// In AdapterMain.scala or config
val localAdapter: LocalAdapter = config.cbsType match {
  case "mybank" => new MyBankAdapter(config.baseUrl, config.apiKey, telemetry)
  case "mock" => new MockLocalAdapter(telemetry)
  case _ => throw new IllegalArgumentException(s"Unknown CBS type: ${config.cbsType}")
}
```

### Step 3: Run Adapter

```bash
export MYBANK_CBS_URL=https://cbs.mybank.com/api
export MYBANK_CBS_API_KEY=secret123
export RABBITMQ_HOST=localhost
export RABBITMQ_REQUEST_QUEUE=obp.request
export RABBITMQ_RESPONSE_QUEUE=obp.response

java -jar obp-rabbit-cats-adapter.jar
```

## Configuration

### Environment Variables

**RabbitMQ Configuration**:

- `RABBITMQ_HOST` - RabbitMQ server host
- `RABBITMQ_PORT` - RabbitMQ server port (default: 5672)
- `RABBITMQ_USERNAME` - Username for authentication
- `RABBITMQ_PASSWORD` - Password for authentication
- `RABBITMQ_REQUEST_QUEUE` - Queue to consume messages from
- `RABBITMQ_RESPONSE_QUEUE` - Queue to send responses to

**CBS Configuration**:
Your bank-specific local adapter will define what configuration it needs.

**Telemetry Configuration**:

- `TELEMETRY_TYPE` - Telemetry implementation (console/prometheus/datadog/noop)
- `ENABLE_METRICS` - Enable metrics collection (true/false)

## Testing Strategy

### 1. Unit Tests

Test local adapter implementations in isolation:

```scala
class MyBankAdapterSpec extends CatsEffectSuite {
  test("getBank returns bank when CBS responds successfully") {
    val adapter = new MyBankAdapter(mockHttpClient, NoOpTelemetry)
    val result = adapter.getBank("bank-id", CallContext("corr-123"))

    result.map {
      case AdapterResponse.Success(bank, _, _) =>
        assertEquals(bank.bankId, "bank-id")
      case _ => fail("Expected success")
    }
  }
}
```

### 2. Integration Tests

Test with mock RabbitMQ and mock CBS:

```scala
test("adapter processes obp.getBank message end-to-end") {
  // Set up mock RabbitMQ, mock CBS, real handlers
  // Send message, verify response
}
```

### 3. CBS Stub for Development

Use `MockLocalAdapter` for development without real CBS:

```scala
class MockLocalAdapter extends LocalAdapter {
  def getBank(...) = IO.pure(
    AdapterResponse.success(
      BankCommons(
        bankId = "mock-bank",
        shortName = "Mock",
        fullName = "Mock Bank for Testing"
      ),
      callContext
    )
  )
}
```

## Monitoring and Observability

### Metrics to Track

1. **Message Processing**:
   - Messages received/processed/failed per minute
   - Processing duration (p50, p95, p99)
   - Queue depth

2. **CBS Operations**:
   - Operation success/failure rate
   - Operation duration by type
   - Retry count
   - Error rate by error code

3. **Business Metrics**:
   - Payments processed
   - Accounts created
   - Transaction volume

4. **System Health**:
   - Memory usage
   - CPU usage
   - Connection status (RabbitMQ, CBS)

### Logging

All logs include:

- Correlation ID (for request tracing)
- Timestamp
- Log level
- Component name
- Message

Example:

```
[INFO][CID: 1flssoftxq0cr1nssr68u0mioj] Message received: type=obp.getBank queue=obp.request
[INFO][CID: 1flssoftxq0cr1nssr68u0mioj] CBS operation started: getBank
[INFO][CID: 1flssoftxq0cr1nssr68u0mioj] CBS operation success: getBank duration=45ms
[INFO][CID: 1flssoftxq0cr1nssr68u0mioj] Message processed: type=obp.getBank duration=52ms
```

## Deployment

### Docker

```dockerfile
FROM openjdk:11-jre-slim
COPY target/obp-rabbit-cats-adapter.jar /app/adapter.jar
CMD ["java", "-jar", "/app/adapter.jar"]
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
          image: obp-rabbit-cats-adapter:latest
          env:
            - name: RABBITMQ_HOST
              value: rabbitmq-service
            - name: MYBANK_CBS_URL
              value: https://cbs.internal
          resources:
            requests:
              memory: "512Mi"
              cpu: "500m"
```

## Benefits of This Architecture

### For Bank Developers

✅ **Only implement `LocalAdapter` trait** - Clear contract  
✅ **No RabbitMQ knowledge needed** - Already handled  
✅ **No OBP message format knowledge needed** - Already handled  
✅ **Focus on CBS integration** - Your domain expertise  
✅ **Telemetry included** - Observability for free

### For Operations Teams

✅ **Standard RabbitMQ setup** - Same for all banks  
✅ **Pluggable telemetry** - Use your monitoring stack  
✅ **Clear metrics** - Know what's happening  
✅ **Health checks** - Easy monitoring  
✅ **Containerizable** - Deploy anywhere

### For OBP Team

✅ **Reusable core** - Generic message handling  
✅ **Multiple CBS support** - One adapter, many banks  
✅ **Consistent interface** - All adapters work the same  
✅ **Type-safe** - Compiler catches errors  
✅ **Functional** - Pure, testable, composable

## Next Steps

1. **Implement your local adapter** - Extend `LocalAdapter` trait
2. **Configure environment** - Set CBS credentials and endpoints
3. **Choose telemetry backend** - Console for dev, Prometheus for prod
4. **Deploy and monitor** - Use provided metrics
5. **Iterate** - Add more CBS operations as needed

## Support

- **GitHub**: [OBP-Rabbit-Cats-Adapter](https://github.com/OpenBankProject/OBP-Rabbit-Cats-Adapter)
- **Documentation**: [OBP Wiki](https://github.com/OpenBankProject/OBP-API/wiki)
- **Community**: OBP Rocket Chat
