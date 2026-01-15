/*
 * Copyright (c) 2025 TESOBE
 *
 * This file is part of OBP-Rabbit-Cats-Adapter.
 *
 * OBP-Rabbit-Cats-Adapter is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License, Version 2.0.
 */

package com.tesobe.obp.adapter.telemetry

import io.prometheus.client._
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports

/**
 * Prometheus metrics for OBP adapter
 */
object PrometheusMetrics {

  // Initialize JVM metrics (memory, GC, threads, etc.)
  DefaultExports.initialize()

  // Message processing metrics
  val messagesReceived: Counter = Counter.build()
    .name("obp_messages_received_total")
    .help("Total number of messages received from RabbitMQ")
    .labelNames("message_type", "queue")
    .register()

  val messagesProcessed: Counter = Counter.build()
    .name("obp_messages_processed_total")
    .help("Total number of messages successfully processed")
    .labelNames("message_type")
    .register()

  val messagesFailed: Counter = Counter.build()
    .name("obp_messages_failed_total")
    .help("Total number of messages that failed processing")
    .labelNames("message_type", "error_code")
    .register()

  val messageProcessingDuration: Histogram = Histogram.build()
    .name("obp_message_processing_duration_seconds")
    .help("Message processing duration in seconds")
    .labelNames("message_type")
    .buckets(0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)
    .register()

  // CBS operation metrics
  val cbsOperations: Counter = Counter.build()
    .name("obp_cbs_operations_total")
    .help("Total number of CBS operations")
    .labelNames("operation", "status")
    .register()

  val cbsOperationDuration: Histogram = Histogram.build()
    .name("obp_cbs_operation_duration_seconds")
    .help("CBS operation duration in seconds")
    .labelNames("operation")
    .buckets(0.01, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0)
    .register()

  val cbsOperationErrors: Counter = Counter.build()
    .name("obp_cbs_operation_errors_total")
    .help("Total number of CBS operation errors")
    .labelNames("operation", "error_code")
    .register()

  val cbsOperationRetries: Counter = Counter.build()
    .name("obp_cbs_operation_retries_total")
    .help("Total number of CBS operation retries")
    .labelNames("operation")
    .register()

  // RabbitMQ metrics
  val rabbitmqConnections: Gauge = Gauge.build()
    .name("obp_rabbitmq_connections")
    .help("Number of active RabbitMQ connections")
    .register()

  val rabbitmqConnectionErrors: Counter = Counter.build()
    .name("obp_rabbitmq_connection_errors_total")
    .help("Total number of RabbitMQ connection errors")
    .register()

  val queueDepth: Gauge = Gauge.build()
    .name("obp_queue_depth")
    .help("Current queue depth")
    .labelNames("queue")
    .register()

  // Response metrics
  val responsesSent: Counter = Counter.build()
    .name("obp_responses_sent_total")
    .help("Total number of responses sent")
    .labelNames("message_type", "success")
    .register()

  // Business metrics
  val paymentsTotal: Counter = Counter.build()
    .name("obp_payments_total")
    .help("Total number of payments")
    .labelNames("bank_id", "currency", "status")
    .register()

  val paymentsAmount: Summary = Summary.build()
    .name("obp_payments_amount")
    .help("Payment amounts")
    .labelNames("bank_id", "currency")
    .register()

  val accountsCreated: Counter = Counter.build()
    .name("obp_accounts_created_total")
    .help("Total number of accounts created")
    .labelNames("bank_id", "account_type")
    .register()

  val customersCreated: Counter = Counter.build()
    .name("obp_customers_created_total")
    .help("Total number of customers created")
    .labelNames("bank_id")
    .register()

  // Health check metrics
  val healthChecks: Counter = Counter.build()
    .name("obp_health_checks_total")
    .help("Total number of health checks")
    .labelNames("component", "status")
    .register()

  /**
   * Get all metrics in Prometheus text format
   */
  def getMetrics: String = {
    import java.io.StringWriter
    val writer = new StringWriter()
    TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples())
    writer.toString
  }
}