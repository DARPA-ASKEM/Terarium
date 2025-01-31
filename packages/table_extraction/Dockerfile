# Build the Spring Boot application
FROM python:3.11-slim-bookworm AS table_extraction_taskrunner_builder

###### DEV ONLY ######
#VVVVVVVVVVVVVVVVVVVVV

# These deps are installed only for use during local development

# Install OpenJDK
RUN apt-get update && apt-get install --assume-yes --no-install-recommends \
	build-essential \
	git \
	libgl1 \
	libglib2.0-0 \
	poppler-utils \
	wget \
	openjdk-17-jdk-headless && \
	rm -rf /var/lib/apt/lists/*

# Install table-extraction
COPY ./packages/table_extraction/table-extraction-version.txt /extractionVersion.txt
RUN COMMIT_SHA="$(cat /extractionVersion.txt)" && \
	echo "Using document_intelligence commit $COMMIT_SHA" && \
	wget --progress=dot:giga -O table-extraction.tar.gz "https://github.com/DARPA-ASKEM/document_intelligence/archive/${COMMIT_SHA}.tar.gz" && \
	tar -zxvf table-extraction.tar.gz && \
	rm table-extraction.tar.gz && \
	mv document_intelligence-* document_intelligence

WORKDIR /document_intelligence/document_intelligence/table_extractor
RUN pip3 install --no-cache-dir --requirement requirements.txt

#Install uvicorn and supervisord
RUN pip3 install --no-cache-dir uvicorn setuptools python-multipart supervisor

#^^^^^^^^^^^^^^^^^^^^
######################

WORKDIR /taskrunner

COPY ./packages/taskrunner .

RUN ./gradlew bootJar

WORKDIR /

# Copy the jar to the root directory
RUN cp /taskrunner/build/libs/*.jar .
RUN mv /terarium-1.0.0-SNAPSHOT.jar /taskrunner.jar

# ------------------------------------------------------------------------------
FROM python:3.11-slim-bookworm

WORKDIR /

# Install OpenJDK JRE
RUN apt-get update && apt-get install -y --no-install-recommends \
	build-essential \
	git \
	libgl1 \
	libglib2.0-0 \
	poppler-utils \
	wget  \
	openjdk-17-jre-headless && \
	rm -rf /var/lib/apt/lists/*

# Copy the supervisord configuration file
COPY ./packages/table_extraction/supervisord.conf /etc/supervisor/conf.d/supervisord.conf

# Copy the Spring Boot fat JAR from the builder image
COPY --from=table_extraction_taskrunner_builder /taskrunner/build/libs/*.jar /taskrunner.jar

# Install table-extraction
COPY --from=table_extraction_taskrunner_builder /document_intelligence /document_intelligence
WORKDIR /document_intelligence/document_intelligence/table_extractor
RUN pip install --no-cache-dir --requirement requirements.txt

# Install taskrunner
COPY ./packages/taskrunner/setup.py /taskrunner/setup.py
COPY ./packages/taskrunner/taskrunner.py /taskrunner/taskrunner.py
WORKDIR /taskrunner
RUN pip install --no-cache-dir --editable .

# Install document_intelligence tasks
COPY ./packages/table_extraction /table_extraction_task
WORKDIR /table_extraction_task
RUN pip install --no-cache-dir --editable .

#Install uvicorn and supervisord
RUN pip install --no-cache-dir uvicorn setuptools python-multipart supervisor

WORKDIR /
CMD ["supervisord", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
