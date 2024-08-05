FROM flowdocker/play_builder:latest-java17-jammy as builder
ADD . /opt/play
WORKDIR /opt/play
RUN SBT_OPTS="-Xms1024M -Xmx2048M -Xss2M -XX:MaxMetaspaceSize=2048M" sbt 'project api' clean stage

FROM flowdocker/play:latest-java17
COPY --from=builder /opt/play /opt/play
WORKDIR /opt/play/api/target/universal/stage
ENTRYPOINT ["java", "-jar", "/root/environment-provider.jar", "--service", "play", "dependency", "bin/dependency-api"]
HEALTHCHECK --interval=5s --timeout=5s --retries=10 \
  CMD curl -f http://localhost:9000/_internal_/healthcheck || exit 1
