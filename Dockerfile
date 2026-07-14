FROM docker-virtual.rb-artifactory.bosch.com/maven:3.9.9-amazoncorretto-21-debian AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src
COPY settings.xml /root/.m2/settings.xml

RUN mvn clean package -DskipTests

FROM cngvm00110.apac.bosch.com:20443/library/eclipse-temurin:21-jre

ARG PROFILE=dev

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY bosch-chain.pem /app/bosch-chain.pem
RUN keytool -importcert -trustcacerts -cacerts \
    -storepass changeit \
    -alias bosch-root \
    -file /app/bosch-chain.pem -noprompt

ENV TZ=Asia/Shanghai
EXPOSE 8102
# 使用构建参数替换硬编码的 test
ENTRYPOINT ["sh", "-c", "java -Duser.timezone=Asia/Shanghai -jar app.jar --spring.profiles.active=${PROFILE}"]