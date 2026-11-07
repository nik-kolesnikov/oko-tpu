package ru.tpu.oko.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.tpu.oko.auth.service.IdentityProvider;
import ru.tpu.oko.auth.stub.DevLoginController;
import ru.tpu.oko.auth.stub.StubIdentityProvider;

import static org.assertj.core.api.Assertions.assertThat;

/** Заглушка входа существует только в профиле dev; в prod её быть не должно. */
class StubProfileTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(StubIdentityProvider.class, DevLoginController.class);

    @Test
    void stubIsAbsentInProd() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(IdentityProvider.class);
                    assertThat(ctx).doesNotHaveBean(DevLoginController.class);
                });
    }

    @Test
    void stubIsAbsentWithoutProfile() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(IdentityProvider.class));
    }

    @Test
    void stubIsPresentInDev() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("dev"))
                .run(ctx -> assertThat(ctx).hasSingleBean(IdentityProvider.class));
    }
}
