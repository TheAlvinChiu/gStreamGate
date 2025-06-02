package io.github.alvinchiu.gstreamgate.repository;

import io.github.alvinchiu.gstreamgate.entity.GrpcProxyMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrpcProxyMapRepository extends JpaRepository<GrpcProxyMap, Long> {

    List<GrpcProxyMap> findByCreateUser(String createUser);

    List<GrpcProxyMap> findByServiceNameContainingIgnoreCaseOrProxyHostNameContainingIgnoreCaseOrTargetHostNameContainingIgnoreCase(
            String serviceName, String proxyHostName, String targetHostName);

    void deleteByProxyMapId(long proxyMapId);

    GrpcProxyMap findByProxyMapId(long proxyMapId);

    GrpcProxyMap findByProxyHostNameAndTargetHostNameAndTargetPort(
            String proxyHostName, String targetHostName, int targetPort);

    List<GrpcProxyMap> findByEnable(String enable);
}