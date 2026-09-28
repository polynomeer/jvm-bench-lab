# The benchmarks run in a container for the same reason every other lab here does: a result
# measured without declared resource limits is not reproducible on another machine.
FROM eclipse-temurin:21-jdk
WORKDIR /opt/bench
COPY naive.jar /opt/bench/naive.jar
COPY jmh.jar /opt/bench/jmh.jar
ENTRYPOINT ["sleep", "infinity"]
