FROM judge0/judge0:1.13.1
USER root
COPY jdk17.tar.gz /tmp/jdk17.tar.gz
RUN echo '3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e  /tmp/jdk17.tar.gz' | sha256sum -c - \
 && mkdir -p /usr/local/openjdk17 \
 && tar -xzf /tmp/jdk17.tar.gz --strip-components=1 -C /usr/local/openjdk17 \
 && rm /tmp/jdk17.tar.gz
COPY register-java.rb /api/register-java.rb
USER judge0
