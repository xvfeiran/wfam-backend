FROM docker-virtual.rb-artifactory.bosch.com/maven:3.9.9-amazoncorretto-21-debian AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src
COPY settings.xml /root/.m2/settings.xml

RUN mvn clean package -DskipTests

FROM docker-virtual.rb-artifactory.bosch.com/eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY bosch-chain.pem /app/bosch-chain.pem
RUN keytool -importcert -trustcacerts -cacerts \
    -storepass changeit \
    -alias bosch-root \
    -file /app/bosch-chain.pem -noprompt

EXPOSE 8102
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=test"]
