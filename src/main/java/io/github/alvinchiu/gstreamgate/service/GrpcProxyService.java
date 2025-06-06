package io.github.alvinchiu.gstreamgate.service;

import io.github.alvinchiu.gstreamgate.entity.GrpcProxyMap;
import io.github.alvinchiu.gstreamgate.manager.DynamicGrpcProxyManager;
import io.github.alvinchiu.gstreamgate.repository.GrpcProxyMapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class GrpcProxyService {
    private static final Logger logger = LoggerFactory.getLogger(GrpcProxyService.class);

    private final GrpcProxyMapRepository grpcProxyMapRepository;
    private final DynamicGrpcProxyManager proxyManager;

    public GrpcProxyService(GrpcProxyMapRepository grpcProxyMapRepository,
                            DynamicGrpcProxyManager proxyManager) {
        this.grpcProxyMapRepository = grpcProxyMapRepository;
        this.proxyManager = proxyManager;
    }

    public List<GrpcProxyMap> getAllProxies() {
        return grpcProxyMapRepository.findAll();
    }

    public List<GrpcProxyMap> getEnabledProxies() {
        return grpcProxyMapRepository.findByEnable("Y");
    }

    public GrpcProxyMap getProxyById(Long id) {
        Optional<GrpcProxyMap> proxy = grpcProxyMapRepository.findById(id);
        return proxy.orElse(null);
    }

    public GrpcProxyMap createProxy(GrpcProxyMap proxyMap, String currentUser) {
        proxyMap.setCreateDateTime(new Date());
        proxyMap.setCreateUser(currentUser);
        GrpcProxyMap saved = grpcProxyMapRepository.save(proxyMap);
        if ("Y".equals(saved.getEnable())) {
            proxyManager.addProxyMapping(saved);
        }
        return saved;
    }

    public GrpcProxyMap updateProxy(Long id, GrpcProxyMap proxyMap, String user) {
        return grpcProxyMapRepository.findById(id)
                .map(existing -> {
                    existing.setServiceName(proxyMap.getServiceName());
                    existing.setProxyHostName(proxyMap.getProxyHostName());
                    existing.setTargetHostName(proxyMap.getTargetHostName());
                    existing.setTargetPort(proxyMap.getTargetPort());
                    existing.setConnectTimeoutMs(proxyMap.getConnectTimeoutMs());
                    existing.setSendTimeoutMs(proxyMap.getSendTimeoutMs());
                    existing.setReadTimeoutMs(proxyMap.getReadTimeoutMs());
                    existing.setSecureMode(proxyMap.getSecureMode());
                    existing.setServerCertContent(proxyMap.getServerCertContent());
                    existing.setServerKeyContent(proxyMap.getServerKeyContent());
                    existing.setAutoTrustUpstreamCerts(proxyMap.getAutoTrustUpstreamCerts());
                    existing.setTrustedCertsContent(proxyMap.getTrustedCertsContent());
                    existing.setEnable(proxyMap.getEnable());
                    existing.setUpdateDateTime(new Date());
                    existing.setUpdateUser(user);
                    GrpcProxyMap updated = grpcProxyMapRepository.save(existing);
                    proxyManager.updateProxyMapping(updated);
                    return updated;
                })
                .orElse(null);
    }

    public GrpcProxyMap updateProxyStatus(Long id, boolean enable, String user) {
        return grpcProxyMapRepository.findById(id)
                .map(existing -> {
                    existing.setEnable(enable ? "Y" : "N");
                    existing.setUpdateDateTime(new Date());
                    existing.setUpdateUser(user);
                    GrpcProxyMap updated = grpcProxyMapRepository.save(existing);
                    proxyManager.updateProxyMapping(updated);
                    return updated;
                })
                .orElse(null);
    }

    public boolean deleteProxy(Long id) {
        return grpcProxyMapRepository.findById(id)
                .map(proxy -> {
                    grpcProxyMapRepository.delete(proxy);
                    proxyManager.deleteProxyMapping(proxy);
                    return true;
                })
                .orElse(false);
    }

    public void refreshProxies() {
        proxyManager.refreshProxyMappings();
    }

    public List<String> getActiveProxyHostnames() {
        return proxyManager.getActiveProxyHostnames();
    }
}
