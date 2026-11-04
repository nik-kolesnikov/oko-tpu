package ru.tpu.oko.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tpu.oko.auth.api.CurrentUserDto;
import ru.tpu.oko.auth.repo.AppUser;
import ru.tpu.oko.auth.repo.AppUserRepository;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.security.AdminRegistry;
import ru.tpu.oko.common.security.OkoPrincipal;

import java.time.Clock;
import java.time.Instant;

@Service
public class LoginService {

    private final AppUserRepository users;
    private final AdminRegistry adminRegistry;
    private final Clock clock;

    public LoginService(AppUserRepository users, AdminRegistry adminRegistry, Clock clock) {
        this.users = users;
        this.adminRegistry = adminRegistry;
        this.clock = clock;
    }

    /** Создаёт пользователя при первом входе, при следующих — обновляет ФИО и email из SSO. */
    @Transactional
    public OkoPrincipal upsert(ExternalIdentity identity) {
        AppUser user = users.findByLogin(identity.login())
                .map(existing -> {
                    existing.setFullName(identity.fullName());
                    existing.setEmail(identity.email());
                    return existing;
                })
                .orElseGet(() -> users.save(new AppUser(identity.login(), identity.fullName(), identity.email(),
                        Instant.now(clock))));
        return new OkoPrincipal(user.getId(), user.getLogin());
    }

    @Transactional(readOnly = true)
    public CurrentUserDto current(long userId) {
        AppUser user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Требуется вход"));
        return new CurrentUserDto(user.getId(), user.getLogin(), user.getFullName(), user.getEmail(),
                adminRegistry.isAdmin(user.getLogin()));
    }
}
