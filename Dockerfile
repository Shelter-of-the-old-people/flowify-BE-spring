FROM gradle:8.5-jdk21

WORKDIR /app

COPY . .

RUN ./gradlew clean bootJar -x test

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "build/libs/*.jar"]
