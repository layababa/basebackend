package com.layababateam.xinxiwang_backend.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.EventLoopGroup
import io.netty.channel.MultiThreadIoEventLoopGroup
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Component

@Component
class NettyServer(
    private val channelInitializer: NettyChannelInitializer,
    @Value("\${netty.port}") private val port: Int,
) : SmartLifecycle {

    private val log = LoggerFactory.getLogger(NettyServer::class.java)

    private val bossGroup: EventLoopGroup = MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
    private val workerGroup: EventLoopGroup = MultiThreadIoEventLoopGroup(NioIoHandler.newFactory())
    private var channelFuture: ChannelFuture? = null
    private var running = false

    override fun start() {
        channelFuture = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(channelInitializer)
            .bind(port)
            .sync()
        running = true
        log.info("Netty WebSocket server started on port {}", port)
    }

    override fun stop() {
        channelFuture?.channel()?.close()?.sync()
        workerGroup.shutdownGracefully()
        bossGroup.shutdownGracefully()
        running = false
        log.info("Netty WebSocket server stopped")
    }

    override fun isRunning(): Boolean = running
}
