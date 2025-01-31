# Build the Spring Boot application
FROM python:3.11-slim-bookworm AS text_extraction_taskrunner_builder

###### DEV ONLY ######
#VVVVVVVVVVVVVVVVVVVVV

# These deps are installed only for use during local development

# Install OpenJDK
RUN apt-get update && apt-get install --assume-yes --no-install-recommends \
	ocrmypdf \
	poppler-utils \
	openjdk-17-jdk-headless && \
	rm -rf /var/lib/apt/lists/*

# Install PyMuPDF
RUN pip install --no-cache-dir PyMuPDF

#^^^^^^^^^^^^^^^^^^^^
######################

WORKDIR /taskrunner

COPY ./packages/taskrunner .

RUN ./gradlew bootJar

WORKDIR /

# ------------------------------------------------------------------------------
FROM python:3.11-slim-bookworm

WORKDIR /

# Install OpenJRE
RUN apt-get update && apt-get install --assume-yes --no-install-recommends \
	ocrmypdf \
	poppler-utils \
	openjdk-17-jre-headless && \
	rm -rf /var/lib/apt/lists/*

# Install PyMuPDF
RUN pip install --no-cache-dir PyMuPDF

# Copy the Spring Boot fat JAR from the builder image
COPY --from=text_extraction_taskrunner_builder /taskrunner/build/libs/*.jar /taskrunner.jar

# Install taskrunner
COPY ./packages/taskrunner/setup.py /taskrunner/setup.py
COPY ./packages/taskrunner/taskrunner.py /taskrunner/taskrunner.py
WORKDIR /taskrunner
RUN pip install --no-cache-dir --editable .

# Install pdf tasks
COPY ./packages/text_extraction /text_extraction_task

WORKDIR /text_extraction_task
RUN pip install --no-cache-dir --editable .

WORKDIR /

CMD ["java", "-jar", "taskrunner.jar"]
