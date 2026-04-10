FROM gradle:8.5-jdk21 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew clean bootJar -x test

FROM cloudtype/jre:21
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar ./main.jar

ENTRYPOINT ["java", "-jar", "main.jar"]
