# Build the Spring Boot application
FROM python:3.11-slim-bookworm AS gollm_taskrunner_builder

###### DEV ONLY ######
#VVVVVVVVVVVVVVVVVVVVV

# These deps are installed only for use during local development

# Install OpenJDK
RUN apt-get update && apt-get install --assume-yes --no-install-recommends \
	wget  \
  openjdk-17-jdk-headless && \
	rm -rf /var/lib/apt/lists/*

# Install gollm
COPY ./packages/gollm ./gollm
WORKDIR /gollm
RUN pip install --no-cache-dir .

#^^^^^^^^^^^^^^^^^^^^
######################

WORKDIR /taskrunner

COPY ./packages/taskrunner .

RUN ./gradlew bootJar

WORKDIR /

# ------------------------------------------------------------------------------
FROM python:3.11-slim-bookworm

WORKDIR /

# Install OpenJDK JRE
RUN apt-get update && apt-get install --assume-yes --no-install-recommends \
	wget  \
  openjdk-17-jre-headless && \
	rm -rf /var/lib/apt/lists/*

# Copy the Spring Boot fat JAR from the builder image
COPY --from=gollm_taskrunner_builder /taskrunner/build/libs/*.jar /taskrunner.jar

# Install taskrunner
COPY ./packages/taskrunner/setup.py /taskrunner/setup.py
COPY ./packages/taskrunner/taskrunner.py /taskrunner/taskrunner.py
WORKDIR /taskrunner
RUN pip install --no-cache-dir --editable .

# Install GoLLM tasks
COPY ./packages/gollm /gollm
WORKDIR /gollm
RUN pip install --no-cache-dir --editable .

WORKDIR /

CMD ["java", "-jar", "taskrunner.jar"]
