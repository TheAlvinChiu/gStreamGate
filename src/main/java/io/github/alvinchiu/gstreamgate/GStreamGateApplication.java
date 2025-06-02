package io.github.alvinchiu.gstreamgate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.bind.annotation.RestController;

/**
 * gRPC Proxy 主應用類
 * 確保所有組件都被正確掃描
 */
@SpringBootApplication(scanBasePackages = "io.github.alvinchiu.gstreamgate")
@EntityScan("io.github.alvinchiu.gstreamgate.entity")
@EnableJpaRepositories("io.github.alvinchiu.gstreamgate.repository")
public class GStreamGateApplication {

    private static final Logger logger = LoggerFactory.getLogger(GStreamGateApplication.class);

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(GStreamGateApplication.class, args);

        // 打印所有 RestController 用於調試
        String[] controllerBeans = context.getBeanNamesForAnnotation(RestController.class);
        logger.info("Found {} RestController beans:", controllerBeans.length);
        for (String beanName : controllerBeans) {
            Object bean = context.getBean(beanName);
            logger.info("  - {} ({})", beanName, bean.getClass().getName());
        }

        // 打印所有 JPA Repository 用於調試
        String[] repositoryBeans = context.getBeanNamesForType(org.springframework.data.repository.Repository.class);
        logger.info("Found {} Repository beans:", repositoryBeans.length);
        for (String beanName : repositoryBeans) {
            Object bean = context.getBean(beanName);
            logger.info("  - {} ({})", beanName, bean.getClass().getName());
        }

        logger.info("gRPC Proxy Application started successfully!");
    }
}