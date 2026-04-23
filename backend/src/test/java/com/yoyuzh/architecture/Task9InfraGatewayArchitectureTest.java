package com.yoyuzh.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class Task9InfraGatewayArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.yoyuzh");

    @Test
    void taskAndTransferRuntimeEntrypointsMustDependOnInfraGateways() {
        ArchRule brokerPublisherRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.internal.application.MediaMetadataTaskBrokerPublisher")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.infra.broker.LightweightBrokerGateway");

        ArchRule brokerConsumerRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.internal.application.MediaMetadataTaskBrokerConsumer")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.infra.broker.LightweightBrokerGateway");

        ArchRule transferStoreRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.transfer.internal.infra.TransferSessionStore")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.infra.lock.DistributedLockGateway");

        ArchRule backgroundTaskRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.internal.application.BackgroundTaskService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.infra.lock.DistributedLockGateway");

        ArchRule fileServiceLockRule = classes()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.infra.lock.DistributedLockGateway");

        brokerPublisherRule.check(classes);
        brokerConsumerRule.check(classes);
        transferStoreRule.check(classes);
        backgroundTaskRule.check(classes);
        fileServiceLockRule.check(classes);
    }

    @Test
    void taskAndTransferRuntimeEntrypointsMustStopDependingOnLegacyCommonInfraContracts() {
        ArchRule noLegacyBrokerRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.internal.application.MediaMetadataTaskBrokerPublisher")
                .or()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.internal.application.MediaMetadataTaskBrokerConsumer")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.common.broker.LightweightBrokerService");

        ArchRule noLegacyLockRule = noClasses()
                .that()
                .haveFullyQualifiedName("com.yoyuzh.transfer.internal.infra.TransferSessionStore")
                .or()
                .haveFullyQualifiedName("com.yoyuzh.platform.job.internal.application.BackgroundTaskService")
                .or()
                .haveFullyQualifiedName("com.yoyuzh.files.workspace.internal.application.FileService")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.yoyuzh.common.lock.DistributedLockService");

        noLegacyBrokerRule.check(classes);
        noLegacyLockRule.check(classes);
    }

    @Test
    void cacheInfrastructureMustLiveUnderInfraCache() {
        assertThat(classes.get("com.yoyuzh.infra.cache.AppRedisProperties")).isNotNull();
        assertThat(classes.get("com.yoyuzh.infra.cache.RedisConfiguration")).isNotNull();
        assertThat(classes.get("com.yoyuzh.infra.cache.RedisCacheNames")).isNotNull();
    }

    @Test
    void brokerAndLockInfrastructureMustBeOwnedDirectlyByInfra() {
        assertThat(classes.get("com.yoyuzh.infra.broker.RedisLightweightBrokerGateway")).isNotNull();
        assertThat(classes.get("com.yoyuzh.infra.broker.InMemoryLightweightBrokerGateway")).isNotNull();
        assertThat(classes.get("com.yoyuzh.infra.lock.RedisDistributedLockGateway")).isNotNull();
        assertThat(classes.get("com.yoyuzh.infra.lock.NoOpDistributedLockGateway")).isNotNull();

        assertThat(classes.stream().map(it -> it.getFullName()))
                .doesNotContain(
                        "com.yoyuzh.common.broker.LightweightBrokerService",
                        "com.yoyuzh.common.broker.InMemoryLightweightBrokerService",
                        "com.yoyuzh.common.broker.RedisLightweightBrokerService",
                        "com.yoyuzh.common.lock.DistributedLockService",
                        "com.yoyuzh.common.lock.NoOpDistributedLockService",
                        "com.yoyuzh.common.lock.RedisDistributedLockService",
                        "com.yoyuzh.infra.broker.RuntimeLightweightBrokerGateway",
                        "com.yoyuzh.infra.lock.RuntimeDistributedLockGateway"
                );
    }
}
