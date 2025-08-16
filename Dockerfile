# syntax=docker/dockerfile:1.17.0
FROM clojure:temurin-24-alpine AS builder

WORKDIR /build

COPY deps.edn build.clj ./
RUN --mount=from=m2repo,target=/root/.m2,readwrite clojure -P -T:build/task
COPY src ./src
COPY resources ./resources
RUN --mount=from=m2repo,target=/root/.m2,readwrite \
    --mount=from=git,target=/.git,readonly \
    clojure -T:build/task uberjar
RUN mkdir tmp-classes \
    && cd tmp-classes \
    && jar xf ../target/*.jar \
    && cd ..
RUN $JAVA_HOME/bin/jlink \
    --add-modules $(jdeps --multi-release 24 --ignore-missing-deps --print-module-deps tmp-classes) \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /opt/jre

# If you get this error message when running the Docker image:
# ``` log
# exec /opt/jre/bin/java: no such file or directory
# ```
#
# It is because `jlink` produces a stripped custom JRE without any
# libc or Alpine runtime files. Since scratch has no dynamic linker
# (`/lib/ld-musl-x86_64.so.1` for Alpine or
# `/lib64/ld-linux-x86-64.so.2` for glibc), then your java binary
# cannot start.
#
# Then use `ldd` to print the shared objects (shared libraries)
# required by each program or shared object specified on the command
# line. Then add the result of this line to the scratch image code
# below:

#RUN ldd /opt/jre/bin/java
RUN addgroup -S app && adduser -S app -G app

FROM scratch
ARG VERSION
LABEL version=$VERSION

COPY --from=builder /etc/passwd /etc/passwd
COPY --from=builder /etc/group /etc/group
COPY --from=builder /opt/jre /opt/jre
COPY --from=builder /build/target/*.jar /app/standalone.jar

# Copy necessary dynamic loader and libraries from builder above into
# the scratch image.
COPY --from=builder /lib/ld-musl-x86_64.so.1 /lib/
COPY --from=builder /opt/jre/lib/libjli.so /opt/jre/lib/

ENV PATH="/opt/jre/bin:$PATH"
ENV JAVA_HOME="/opt/jre"
ENV JDK_JAVA_OPTIONS="-XshowSettings:system -XX:+UseContainerSupport -XX:MaxRAMPercentage=90"

WORKDIR /app
USER app
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "standalone.jar"]
