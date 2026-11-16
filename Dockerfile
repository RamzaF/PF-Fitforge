FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build
WORKDIR /workspace
COPY InitializrSpringbootProject/pom.xml ./pom.xml
COPY InitializrSpringbootProject/src ./src
# Maven resource copying needs positive mtimes, including when COPY reuses old cache.
RUN find src/main/resources -type f -exec touch -d @1704067200 {} + \
 && mvn -B -ntp -DskipTests package \
 && test -s target/classes/templates/tienda/listado.html \
 && test -s target/classes/static/uploads/guantes_gym.png
# Surefire resolves its provider lazily; cache it while build networking is allowed.
RUN mvn -B -ntp org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get -Dartifact=org.apache.maven.surefire:surefire-junit-platform:3.2.5 \
 && mvn -B -ntp org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get -Dartifact=org.junit.platform:junit-platform-launcher:1.11.4
FROM build AS test
CMD ["mvn", "-o", "-B", "-ntp", "test"]
FROM eclipse-temurin:21.0.6_7-jre-jammy@sha256:903dcea12637919198041da42cd270d549656c06bfda9126be36f182bb0de72d AS runtime
WORKDIR /app
RUN mkdir -p /var/fitforge/uploads && chown -R 10001:10001 /var/fitforge/uploads
COPY --from=build --chown=10001:10001 /workspace/target/Fitforge-1.jar /app/app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
