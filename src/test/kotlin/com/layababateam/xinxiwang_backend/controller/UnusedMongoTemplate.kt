package com.layababateam.xinxiwang_backend.controller

import org.springframework.data.mongodb.core.MongoTemplate
import sun.misc.Unsafe

internal fun unusedMongoTemplate(): MongoTemplate {
    val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
    unsafeField.isAccessible = true
    val unsafe = unsafeField.get(null) as Unsafe
    return unsafe.allocateInstance(MongoTemplate::class.java) as MongoTemplate
}
