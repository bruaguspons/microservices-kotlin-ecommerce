package com.example.ecommerce.shared.domain.model

import java.time.LocalDateTime
import java.util.UUID

abstract class BaseModel<T : Any> protected constructor(
    id: T?,
    createdAt: LocalDateTime? = null,
    createdBy: UUID? = null,
    lastModified: LocalDateTime? = null,
    lastModifiedBy: UUID? = null,
    version: Long? = null,
    isDeleted: Boolean = false,
) {
    var id: T? = id
        protected set
    var createdAt: LocalDateTime? = createdAt
        protected set
    var createdBy: UUID? = createdBy
        protected set
    var lastModified: LocalDateTime? = lastModified
        protected set
    var lastModifiedBy: UUID? = lastModifiedBy
        protected set
    var version: Long? = version
        protected set
    var isDeleted: Boolean = isDeleted
        protected set
}
