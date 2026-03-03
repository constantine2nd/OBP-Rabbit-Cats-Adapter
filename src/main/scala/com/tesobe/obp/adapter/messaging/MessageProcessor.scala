/*
 * Copyright (c) 2025 TESOBE
 *
 * This file is part of OBP-Rabbit-Cats-Adapter.
 *
 * OBP-Rabbit-Cats-Adapter is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License, Version 2.0.
 */

package com.tesobe.obp.adapter.messaging

import cats.effect.IO
import cats.syntax.either._
import com.tesobe.obp.adapter.interfaces.{LocalAdapter, LocalAdapterResult}
import com.tesobe.obp.adapter.models._
import com.tesobe.obp.adapter.telemetry.Telemetry
import io.circe.parser._
import io.circe.syntax._

import scala.concurrent.duration._

/** Transport-agnostic message processing logic.
  *
  * Shared by RabbitMQ consumer and gRPC server to avoid duplicating
  * the parsing/routing/response-building pipeline.
  */
object MessageProcessor {

  /** Process a request message and return the inbound response.
    *
    * @param process    The OBP message type (e.g., "obp.getBank")
    * @param messageJson The raw JSON message string
    * @param localAdapter The local adapter to delegate to
    * @param telemetry  Telemetry for recording metrics
    * @return (InboundMessage, serialized JSON response)
    */
  def processRequest(
      process: String,
      messageJson: String,
      localAdapter: LocalAdapter,
      telemetry: Telemetry
  ): IO[(InboundMessage, String)] = {
    val startTime = System.currentTimeMillis()

    (for {
      // Parse the message
      outboundMsg <- parseOutboundMessage(messageJson)

      // Parse raw JSON to extract additional data fields
      jsonObj <- IO.fromEither(
        io.circe.parser.parse(messageJson).flatMap(_.as[io.circe.JsonObject])
      )

      // Extract data fields (everything except outboundAdapterCallContext)
      dataFields = jsonObj.filterKeys(_ != "outboundAdapterCallContext")

      _ <- telemetry.recordMessageReceived(
        process,
        outboundMsg.outboundAdapterCallContext.correlationId,
        "adapter"
      )

      // Extract call context
      callContext = CallContext.fromOutbound(outboundMsg)

      // Log message processing
      _ <- IO.println(s"[${callContext.correlationId}] Processing: $process")

      // Handle adapter-specific messages or delegate to local adapter
      adapterResponse <- process match {
        case "obp.getAdapterInfo" =>
          handleGetAdapterInfo(localAdapter, callContext)
        case _ =>
          localAdapter.handleMessage(process, dataFields, callContext)
      }

      // Build inbound message
      inboundMsg <- buildInboundMessage(outboundMsg, adapterResponse)

      // Serialize response
      responseJson = inboundMsg.asJson.noSpaces

      // Record success
      duration = (System.currentTimeMillis() - startTime).millis
      _ <- telemetry.recordMessageProcessed(
        process,
        callContext.correlationId,
        duration
      )

      _ <- IO.println(
        s"[${callContext.correlationId}] [OK] Completed in ${duration.toMillis}ms"
      )

    } yield (inboundMsg, responseJson))
  }

  /** Handle getAdapterInfo - returns adapter information
    */
  def handleGetAdapterInfo(
      localAdapter: LocalAdapter,
      callContext: CallContext
  ): IO[LocalAdapterResult] = {
    import io.circe.Json
    import io.circe.JsonObject
    import scala.sys.process._

    val gitCommit =
      try {
        "git rev-parse HEAD".!!.trim
      } catch {
        case _: Exception => "unknown"
      }

    IO.pure(
      LocalAdapterResult.success(
        JsonObject(
          "name" -> Json.fromString("OBP-Rabbit-Cats-Adapter"),
          "version" -> Json.fromString("1.0.0-SNAPSHOT"),
          "git_commit" -> Json.fromString(gitCommit),
          "date" -> Json.fromString(java.time.Instant.now().toString)
        ),
        Nil
      )
    )
  }

  /** Parse JSON string to OutboundMessage
    */
  def parseOutboundMessage(json: String): IO[OutboundMessage] = {
    IO.fromEither(
      decode[OutboundMessage](json)
        .leftMap(err =>
          new RuntimeException(
            s"Failed to parse outbound message: ${err.getMessage}"
          )
        )
    )
  }

  /** Build inbound response message
    */
  def buildInboundMessage(
      outboundMsg: OutboundMessage,
      adapterResponse: LocalAdapterResult
  ): IO[InboundMessage] = {
    val ctx = outboundMsg.outboundAdapterCallContext

    adapterResponse match {
      case LocalAdapterResult.Success(data, messages) =>
        IO.pure(
          InboundMessage.success(
            correlationId = ctx.correlationId,
            sessionId = ctx.sessionId,
            data = data,
            backendMessages = messages
          )
        )

      case LocalAdapterResult.Error(code, message, messages) =>
        IO.pure(
          InboundMessage.error(
            correlationId = ctx.correlationId,
            sessionId = ctx.sessionId,
            errorCode = code,
            errorMessage = message,
            backendMessages = messages
          )
        )
    }
  }
}
