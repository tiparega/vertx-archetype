FROM openjdk:15-jdk-alpine
ARG JAR_FILE
COPY target/${JAR_FILE} /usr/java/app.jar
WORKDIR /usr/java/
CMD ["java", "-jar", "app.jar"]