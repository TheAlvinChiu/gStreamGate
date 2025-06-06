package io.github.alvinchiu.gstreamgate.service;

import io.github.alvinchiu.gstreamgate.entity.GrpcProxyMap;
import io.github.alvinchiu.gstreamgate.manager.DynamicGrpcProxyManager;
import io.github.alvinchiu.gstreamgate.repository.GrpcProxyMapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GrpcProxyServiceTest {
    private GrpcProxyMapRepository repository;
    private DynamicGrpcProxyManager manager;
    private GrpcProxyService service;

    @BeforeEach
    void setUp() {
        repository = mock(GrpcProxyMapRepository.class);
        manager = mock(DynamicGrpcProxyManager.class);
        service = new GrpcProxyService(repository, manager);
    }

    @Test
    void createProxySavesAndRegistersWhenEnabled() {
        GrpcProxyMap map = new GrpcProxyMap();
        map.setEnable("Y");
        when(repository.save(any(GrpcProxyMap.class))).thenAnswer(i -> i.getArgument(0));

        GrpcProxyMap result = service.createProxy(map, "admin");

        assertEquals("admin", result.getCreateUser());
        verify(repository).save(map);
        verify(manager).addProxyMapping(map);
    }

    @Test
    void updateProxyUpdatesExistingAndManager() {
        GrpcProxyMap existing = new GrpcProxyMap();
        existing.setProxyMapId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(GrpcProxyMap.class))).thenAnswer(i -> i.getArgument(0));

        GrpcProxyMap update = new GrpcProxyMap();
        update.setServiceName("svc");
        update.setEnable("N");

        GrpcProxyMap result = service.updateProxy(1L, update, "admin");

        assertNotNull(result);
        assertEquals("svc", result.getServiceName());
        verify(manager).updateProxyMapping(existing);
    }

    @Test
    void updateProxyStatusReturnsNullWhenNotFound() {
        when(repository.findById(2L)).thenReturn(Optional.empty());

        GrpcProxyMap result = service.updateProxyStatus(2L, true, "admin");

        assertNull(result);
    }
}
