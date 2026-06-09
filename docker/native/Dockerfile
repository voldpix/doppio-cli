FROM ghcr.io/graalvm/native-image-community:21

WORKDIR /workspace

ARG JAR_FILE=target/doppio-1.0-SNAPSHOT.jar
COPY ${JAR_FILE} /workspace/doppio.jar

RUN native-image \
    --no-fallback \
    -H:+ReportExceptionStackTraces \
    -H:Name=doppio \
    -jar /workspace/doppio.jar

CMD ["/workspace/doppio", "--help"]
