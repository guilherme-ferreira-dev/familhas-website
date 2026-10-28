FROM quay.io/keycloak/keycloak:26.4.0

# Opções de build ficam fixas na imagem; o container usa `start --optimized`.
# health-enabled expõe /health/ready na porta de gerenciamento 9000.
ENV KC_DB=oracle \
    KC_HEALTH_ENABLED=true

USER root
COPY providers/ojdbc17.jar /opt/keycloak/providers/ojdbc17.jar
COPY scripts/start-with-keystore.sh /opt/keycloak/scripts/start-with-keystore.sh
RUN sed -i 's/\r$//' /opt/keycloak/scripts/start-with-keystore.sh && chmod +x /opt/keycloak/scripts/start-with-keystore.sh
USER 1000

RUN /opt/keycloak/bin/kc.sh build

ENTRYPOINT ["/opt/keycloak/scripts/start-with-keystore.sh"]
