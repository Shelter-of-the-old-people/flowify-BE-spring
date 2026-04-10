package org.github.flowify.auth.repository;

import org.github.flowify.auth.entity.ExchangeCode;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ExchangeCodeRepository extends MongoRepository<ExchangeCode, String> {

    Optional<ExchangeCode> findByCode(String code);
}
