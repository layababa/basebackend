package com.layababateam.xinxiwang_backend.service

import com.layababateam.xinxiwang_backend.model.ServerNode

/**
 * 后台节点管理端口。
 *
 * SDK 复用节点管理 HTTP 契约，节点持久化和 CDN 配置发布由接入方实现。
 */
interface AdminNodePort {
    fun listNodes(): List<ServerNode>
    fun findNode(id: String): ServerNode?
    fun saveNode(node: ServerNode): ServerNode
    fun deleteNode(id: String)
    fun publishCdnConfig()
}
