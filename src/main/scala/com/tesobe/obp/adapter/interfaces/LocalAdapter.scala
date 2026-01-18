/*
 * Copyright (c) 2025 TESOBE
 *
 * This file is part of OBP-Rabbit-Cats-Adapter.
 *
 * OBP-Rabbit-Cats-Adapter is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License, Version 2.0.
 *
 * OBP-Rabbit-Cats-Adapter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License, Version 2.0
 * along with OBP-Rabbit-Cats-Adapter. If not, see <http://www.apache.org/licenses/>.
 */

package com.tesobe.obp.adapter.interfaces

import cats.effect.IO
import com.tesobe.obp.adapter.models._
import io.circe.JsonObject

/**
 * Core interface for Core Banking System (CBS) adapters.
 *
 * Each bank implements this trait to provide bank-specific logic for
 * communicating with their CBS. This separation ensures that:
 *
 * 1. OBP message handling code remains generic and reusable
 * 2. CBS-specific code is isolated and can be customized per bank
 * 3. Multiple CBS implementations can coexist (REST, SOAP, Database, etc.)
 *
 * The interface works directly with JSON (matching OBP Message Docs format):
 * - Input: JSON data field from OBP outbound message
 * - Output: JSON data field for OBP inbound response
 *
 * All methods return IO[AdapterResponse] where:
 * - The outer IO represents the effect/computation
 * - AdapterResponse wraps either success data (JSON) or error information
 *
 * Implementations should handle:
 * - Network failures
 * - Authentication
 * - Retries (if applicable)
 * - Mapping CBS responses to OBP message docs format
 * - Logging/tracing (using provided telemetry)
 */
trait LocalAdapter {

  /** Adapter name/identifier (e.g., "MyBank-REST-Adapter") */
  def name: String

  /** Adapter version */
  def version: String

  /**
   * Handle an OBP message.
   *
   * This is the main entry point. The generic adapter calls this method
   * for every message received from RabbitMQ.
   *
   * @param process The OBP message type (e.g., "obp.getBank", "obp.makePayment")
   * @param data The data payload from the OBP message (varies by message type)
   * @param callContext Context information (correlation ID, user info, etc.)
   * @return Either error response or success response with data matching message docs
   */
  def handleMessage(
    process: String,
    data: JsonObject,
    callContext: CallContext
  ): IO[AdapterResponse]

  /**
   * Check if the CBS connection is healthy
   */
  def checkHealth(callContext: CallContext): IO[AdapterResponse]

  /**
   * Get adapter information
   */
  def getAdapterInfo(callContext: CallContext): IO[AdapterResponse]
}

/**
 * Response from adapter operations.
 * Provides consistent error handling across all adapter implementations.
 */
sealed trait AdapterResponse

object AdapterResponse {

  /**
   * Successful response with JSON data matching OBP message docs format
   */
  final case class Success(
    data: JsonObject,
    backendMessages: List[BackendMessage] = Nil
  ) extends AdapterResponse

  /**
   * Error response with details
   */
  final case class Error(
    errorCode: String,
    errorMessage: String,
    backendMessages: List[BackendMessage] = Nil
  ) extends AdapterResponse

  /** Convenience constructors */
  def success(data: JsonObject, messages: List[BackendMessage] = Nil): AdapterResponse =
    Success(data, messages)

  def error(code: String, message: String, messages: List[BackendMessage] = Nil): AdapterResponse =
    Error(code, message, messages)
}
