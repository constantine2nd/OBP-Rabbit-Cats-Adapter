/*
 * Copyright (c) 2025 TESOBE
 *
 * This file is part of OBP-Rabbit-Cats-Adapter.
 *
 * OBP-Rabbit-Cats-Adapter is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License, Version 2.0.
 */

package com.tesobe.obp.adapter.grpc

import cats.effect.{IO, Resource}
import com.tesobe.obp.adapter.config.GrpcConfig
import com.tesobe.obp.adapter.interfaces.LocalAdapter
import com.tesobe.obp.adapter.messaging.MessageProcessor
import com.tesobe.obp.adapter.telemetry.Telemetry
import code.bankconnectors.grpc.{
  ObpConnectorRequest,
  ObpConnectorResponse,
  ObpConnectorServiceGrpc
}
import io.grpc.{Server, ServerBuilder}
import io.grpc.stub.StreamObserver

/** gRPC server that receives requests from OBP-API.
  *
  * Uses the same MessageProcessor as RabbitMQ, so both transports
  * share identical parsing/routing/response-building logic.
  */
object GrpcServer {

  /** Create a gRPC server as a cats-effect Resource for lifecycle management.
    */
  def create(
      config: GrpcConfig,
      localAdapter: LocalAdapter,
      telemetry: Telemetry
  ): Resource[IO, Server] = {
    val serviceImpl = new ObpConnectorServiceImpl(localAdapter, telemetry)

    Resource.make(
      IO {
        val server = ServerBuilder
          .forPort(config.port)
          .addService(serviceImpl)
          .build()
          .start()
        server
      }
    )(server =>
      IO {
        server.shutdown()
        server.awaitTermination()
      }
    )
  }

  /** gRPC service implementation that delegates to MessageProcessor.
    */
  private class ObpConnectorServiceImpl(
      localAdapter: LocalAdapter,
      telemetry: Telemetry
  ) extends ObpConnectorServiceGrpc.ObpConnectorServiceImplBase {

    implicit private val runtime: cats.effect.unsafe.IORuntime =
      cats.effect.unsafe.IORuntime.global

    override def processObpRequest(
        request: ObpConnectorRequest,
        responseObserver: StreamObserver[ObpConnectorResponse]
    ): Unit = {
      val methodName = request.getMethodName
      val jsonPayload = request.getJsonPayload

      val processIO = for {
        _ <- IO.println(s"[gRPC] Received request: $methodName")
        result <- MessageProcessor
          .processRequest(methodName, jsonPayload, localAdapter, telemetry)
          .handleErrorWith { error =>
            IO.println(
              s"[gRPC] [ERROR] Processing failed: ${error.getMessage}"
            ) *>
              IO(error.printStackTrace()) *>
              IO.raiseError(error)
          }
        (_, responseJson) = result
      } yield {
        val response = ObpConnectorResponse
          .newBuilder()
          .setJsonPayload(responseJson)
          .build()
        responseObserver.onNext(response)
        responseObserver.onCompleted()
      }

      processIO
        .handleErrorWith { error =>
          IO {
            responseObserver.onError(
              io.grpc.Status.INTERNAL
                .withDescription(error.getMessage)
                .asRuntimeException()
            )
          }
        }
        .unsafeRunAndForget()
    }
  }
}
