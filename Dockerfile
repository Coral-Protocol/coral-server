FROM gradle:8.14.2-jdk21-jammy AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src

RUN jlink \
    --verbose \
    --add-modules java.base,jdk.unsupported,java.desktop,java.instrument,java.logging,java.management,java.sql,java.xml,java.naming,jdk.crypto.ec \
    --compress 2 --strip-debug --no-header-files --no-man-pages \
    --output /opt/minimal-java

RUN gradle build --no-daemon -x test

FROM ubuntu:jammy

# Install runtime dependencies following apt best practices: update, install with no recommends, and clean apt lists in one layer
RUN apt-get update \
    && apt-get install -y --no-install-recommends libudev1 ca-certificates \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/opt/minimal-java
ENV PATH="$JAVA_HOME/bin:$PATH"

ENV CONFIG_PATH="/config"

RUN mkdir /app
# Copy the custom minimal JRE from the builder stage
COPY --from=build "$JAVA_HOME" "$JAVA_HOME"
COPY --from=build /home/gradle/src/build/libs/ /app/
# Determine the built coral-server JAR (excluding -plain) and create a stable symlink
RUN ln -s "$(ls -1 /app/coral-server-*.jar | grep -v '\-plain\.jar' | head -n 1)" /app/coral-server.jar

ENTRYPOINT ["java","-jar", "/app/coral-server.jar"]