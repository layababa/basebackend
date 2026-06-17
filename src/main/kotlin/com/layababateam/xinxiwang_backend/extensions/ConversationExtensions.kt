package com.layababateam.xinxiwang_backend.extensions

import com.layababateam.xinxiwang_backend.model.Conversation
import com.layababateam.xinxiwang_backend.model.ConversationType

/**
 * 判斷是否為私聊（包含 type=0 的普通私聊及 type=2 的特殊私聊）
 */
fun Conversation.isPrivateChat(): Boolean = this.type == ConversationType.PRIVATE.value || this.type == ConversationType.SPECIAL_PRIVATE.value

/**
 * 判斷是否為群聊
 */
fun Conversation.isGroupChat(): Boolean = this.type == ConversationType.GROUP.value
