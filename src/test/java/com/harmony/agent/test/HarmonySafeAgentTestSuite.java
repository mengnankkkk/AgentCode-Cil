package com.harmony.agent.test;

import com.harmony.agent.test.e2e.Bzip2E2ETest;
import com.harmony.agent.test.e2e.YlongRuntimeE2ETest;
import com.harmony.agent.test.unit.PersistentCacheManagerTest;
import com.harmony.agent.test.integration.DecisionEngineIntegrationTest;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * 完整测试套件
 *
 * 包含:
 * - E2E 测试 (bzip2, ylong_runtime)
 * - 单元测试 (PersistentCacheManager, RustMigrationAdvisor 等)
 * - 集成测试 (DecisionEngine 并发)
 *
 * 运行方式:
 * mvn test -Dtest=HarmonySafeAgentTestSuite
 *
 * 或针对特定测试:
 * mvn test -Dtest=Bzip2E2ETest
 * mvn test -Dtest=PersistentCacheManagerTest
 */
@Suite
@SuiteDisplayName("🚀 HarmonySafeAgent 完整测试套件")
@SelectClasses({
    // E2E 测试
    Bzip2E2ETest.class,
    YlongRuntimeE2ETest.class,

    // 单元测试
    PersistentCacheManagerTest.class,

    // 集成测试
    DecisionEngineIntegrationTest.class
})
public class HarmonySafeAgentTestSuite {
    // 套件定义
}
