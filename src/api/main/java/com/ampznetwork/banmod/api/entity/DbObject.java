package com.ampznetwork.banmod.api.entity;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;
import java.util.UUID;

@Entity
public interface DbObject {
    @Id
    UUID getId();

    @Transient
    EntityType getEntityType();
}
