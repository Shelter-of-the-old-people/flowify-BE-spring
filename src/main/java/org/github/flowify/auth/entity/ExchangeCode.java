package org.github.flowify.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "exchange_codes")
public class ExchangeCode {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String accessToken;

    private String refreshToken;

    private String userId;

    @CreatedDate
    @Indexed(expireAfter = "30s")
    private Instant createdAt;
}
