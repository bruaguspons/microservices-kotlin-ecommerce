package com.example.ecommerce.buildingblocks.persistence

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Version
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime
import java.util.UUID

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity(
    id: UUID? = null,
    createdAt: LocalDateTime? = null,
    createdBy: UUID? = null,
    lastModified: LocalDateTime? = null,
    lastModifiedBy: UUID? = null,
    version: Long? = null,
    isDeleted: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = id
        protected set

    @CreatedDate
    var createdAt: LocalDateTime? = createdAt
        protected set

    @CreatedBy
    var createdBy: UUID? = createdBy
        protected set

    @LastModifiedDate
    var lastModified: LocalDateTime? = lastModified
        protected set

    @LastModifiedBy
    var lastModifiedBy: UUID? = lastModifiedBy
        protected set

    @Version
    var version: Long? = version
        protected set

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean = isDeleted
        protected set
}
