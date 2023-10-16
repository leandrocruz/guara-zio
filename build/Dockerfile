FROM us.gcr.io/oystr-cloud-test/oystr-base-java:v2.0.0

LABEL oystr.container.type=oystr-service

ADD lib  /opt/oystr/service/lib
ADD bin  /opt/oystr/service/bin

RUN mkdir -p /etc/service/oystr-service
ADD run      /etc/service/oystr-service/run

