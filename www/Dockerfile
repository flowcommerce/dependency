FROM flowdocker/play_builder:0.0.77 as builder
ADD . /opt/play
WORKDIR /opt/play
RUN sbt 'project www' clean stage

FROM flowdocker/play:0.0.77
COPY --from=builder /opt/play /opt/play
WORKDIR /opt/play/www/target/universal/stage
ENTRYPOINT ["java", "-jar", "/root/environment-provider.jar", "--service", "play", "dependency", "bin/dependency-www"]
HEALTHCHECK --interval=5s --timeout=5s --retries=10 \
  CMD curl -f http://localhost:9000/_internal_/healthcheck || exit 1
