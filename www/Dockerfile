FROM flowdocker/play:0.0.60

ADD . /opt/play

WORKDIR /opt/play

RUN sbt 'project www' clean stage

WORKDIR www/target/universal/stage

ENTRYPOINT ["java", "-jar", "/root/environment-provider.jar", "--service", "play", "dependency", "bin/dependency-www"]
