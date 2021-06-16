FROM shipilev/openjdk:16

MAINTAINER @agafox <agafox@sip3.io>
MAINTAINER @windsent <windsent@sip3.io>

RUN apt-get update && \
    apt-get install libpcap0.8 && \
    apt-get install openssl

ENV SERVICE_NAME sip3-captain
ENV HOME /opt/$SERVICE_NAME

ENV EXECUTABLE_FILE $HOME/$SERVICE_NAME.jar
ADD target/$SERVICE_NAME*.jar $EXECUTABLE_FILE

ENV VERTX_OPTIONS_FILE $HOME/vertx-options.json
ADD src/main/resources/vertx-options.json $VERTX_OPTIONS_FILE

ENV CONFIG_FILE $HOME/application.yml
ADD src/main/resources/application.yml $CONFIG_FILE

ENV LOGBACK_FILE $HOME/logback.xml
ADD src/main/resources/logback.xml $LOGBACK_FILE

ENV JAVA_OPTS "-Xms64m -Xmx128m"
ENTRYPOINT java $JAVA_OPTS -Dlogback.configurationFile=$LOGBACK_FILE -jar $EXECUTABLE_FILE --options $VERTX_OPTIONS_FILE -Dconfig.location=$CONFIG_FILE