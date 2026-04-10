# build
FROM gradle:8.5-jdk21 AS builder
WORKDIR /build
COPY . .
RUN gradle clean bootJar -x test

# runtime
FROM cloudtype/jre:21
WORKDIR /app
COPY --from=builder /build/build/libs/*.jar ./main.jar

ENTRYPOINT ["java", "-jar", "main.jar"]
